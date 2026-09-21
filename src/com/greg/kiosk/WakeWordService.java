package com.greg.kiosk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;
import org.json.JSONObject;

/**
 * WakeWordService — "Hey Greg" always-on wake word, anywhere on the phone.
 *
 * Ported from Wall Greg's actual production pipeline (surface/web/src/main.ts,
 * SURFACE_MODE='vad'): a cheap local gate decides WHEN to spend a cloud call,
 * a cloud transcript decides WHETHER the wake word was said. Wall Greg's local
 * gate is a Silero ONNX VAD running in a browser via WASM; there's no
 * equivalent running natively on this phone, so this is a simplified stand-in:
 * a rolling RMS energy threshold over raw PCM. It doesn't discriminate speech
 * from a loud game sound effect as well as a trained VAD would, but it keeps
 * the same shape — most of the time nothing is happening, so most of the time
 * nothing gets sent anywhere.
 *
 * Flow:
 *   1. Continuously read 16kHz mono PCM from the mic.
 *   2. Track a rolling noise floor; when a burst of frames crosses the
 *      threshold, buffer it (with ~300ms pre-roll) until it quiets back down
 *      or hits a 4s cap.
 *   3. POST that clip to greg-transcribe (Groq Whisper, server-side key).
 *   4. If the transcript contains "greg" — release the mic from this loop and
 *      hand off to the same native SpeechRecognizer + TextToSpeech pipeline
 *      already proven on the Ask Greg chat screen, to capture the real
 *      question and speak the answer.
 *   5. Resume step 1.
 *
 * Runs as its own foreground service so it survives the kiosk WebView being
 * backgrounded by a game — same pattern as FloatingHomeService/ShadeGuardService,
 * revived by WatchdogReceiver if the process is OOM-killed.
 */
public class WakeWordService extends Service {
    private static final String CHANNEL_ID = "greg_wakeword";
    private static final String SUPABASE_URL = "https://zdmxqzkqutizehynqojk.supabase.co";
    private static final String TRANSCRIBE_URL = SUPABASE_URL + "/functions/v1/greg-transcribe";
    private static final String CHAT_URL = SUPABASE_URL + "/functions/v1/greg-kid-chat";
    private static final Pattern WAKE_PATTERN = Pattern.compile("\\bgreg\\b", Pattern.CASE_INSENSITIVE);

    private static final int SAMPLE_RATE = 16000;
    private static final int FRAME_MS = 20;
    private static final int FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000; // 320
    private static final int PREROLL_FRAMES = 15;   // ~300ms
    private static final int SILENCE_END_FRAMES = 30; // ~600ms of quiet ends a segment
    private static final int MAX_SEGMENT_FRAMES = 200; // ~4s hard cap
    private static final int MIN_SPEECH_FRAMES = 8;   // ~160ms floor — ignore blips
    private static final double SPEECH_MULTIPLIER = 2.5;
    private static final double MIN_THRESHOLD = 350;

    private AudioRecord audioRecord;
    private Thread vadThread;
    private volatile boolean running = false;
    private volatile boolean inConversationFlow = false;

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String pendingSpeech;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationChannel chan = new NotificationChannel(
            CHANNEL_ID, "Greg Wake Word", NotificationManager.IMPORTANCE_MIN);
        chan.setShowBadge(false);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(chan);
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Greg is listening")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build();
        startForeground(2, notification);

        startVadLoop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopVadLoop();
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
        if (tts != null) { tts.shutdown(); tts = null; }
    }

    // ── VAD loop ─────────────────────────────────────────────────────

    private void startVadLoop() {
        if (running) return;
        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuf <= 0) minBuf = FRAME_SAMPLES * 2 * 4;
        int bufSize = Math.max(minBuf, FRAME_SAMPLES * 2 * 8);
        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize);
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                audioRecord = null;
                return;
            }
            audioRecord.startRecording();
        } catch (Exception e) {
            audioRecord = null;
            return;
        }

        running = true;
        vadThread = new Thread(new Runnable() {
            public void run() { runVadLoop(); }
        }, "greg-wakeword-vad");
        vadThread.start();
    }

    private void stopVadLoop() {
        running = false;
        if (vadThread != null) {
            try { vadThread.join(500); } catch (InterruptedException e) { /* best effort */ }
            vadThread = null;
        }
        if (audioRecord != null) {
            try { audioRecord.stop(); } catch (Exception e) { /* best effort */ }
            try { audioRecord.release(); } catch (Exception e) { /* best effort */ }
            audioRecord = null;
        }
    }

    private void runVadLoop() {
        short[] frame = new short[FRAME_SAMPLES];
        // Circular pre-roll buffer of raw frames
        short[][] preroll = new short[PREROLL_FRAMES][];
        int prerollIdx = 0;
        int prerollCount = 0;

        double noiseFloor = 300;
        boolean inSpeech = false;
        int silenceRun = 0;
        int speechFrameTotal = 0;
        ByteArrayOutputStream segment = null;

        while (running) {
            if (audioRecord == null) break;
            int read = audioRecord.read(frame, 0, FRAME_SAMPLES);
            if (read <= 0) continue;

            double rms = rms(frame, read);

            if (!inSpeech) {
                double threshold = Math.max(noiseFloor * SPEECH_MULTIPLIER, MIN_THRESHOLD);
                if (rms > threshold) {
                    // Onset — start a segment, seeded with pre-roll
                    inSpeech = true;
                    silenceRun = 0;
                    speechFrameTotal = 0;
                    segment = new ByteArrayOutputStream();
                    int start = prerollCount < PREROLL_FRAMES ? 0 : prerollIdx;
                    int count = Math.min(prerollCount, PREROLL_FRAMES);
                    for (int i = 0; i < count; i++) {
                        short[] pf = preroll[(start + i) % PREROLL_FRAMES];
                        if (pf != null) writeFrame(segment, pf, pf.length);
                    }
                    writeFrame(segment, frame, read);
                    speechFrameTotal++;
                } else {
                    noiseFloor = noiseFloor * 0.95 + rms * 0.05;
                    if (noiseFloor < 50) noiseFloor = 50;
                }
            } else {
                writeFrame(segment, frame, read);
                speechFrameTotal++;
                double threshold = Math.max(noiseFloor * SPEECH_MULTIPLIER, MIN_THRESHOLD);
                if (rms > threshold) {
                    silenceRun = 0;
                } else {
                    silenceRun++;
                }
                boolean ended = silenceRun >= SILENCE_END_FRAMES || speechFrameTotal >= MAX_SEGMENT_FRAMES;
                if (ended) {
                    inSpeech = false;
                    if (speechFrameTotal >= MIN_SPEECH_FRAMES) {
                        final byte[] pcm = segment.toByteArray();
                        handleSegment(pcm);
                    }
                    segment = null;
                }
            }

            // Maintain pre-roll ring regardless of state (cheap copy)
            short[] copy = new short[read];
            System.arraycopy(frame, 0, copy, 0, read);
            preroll[prerollIdx] = copy;
            prerollIdx = (prerollIdx + 1) % PREROLL_FRAMES;
            if (prerollCount < PREROLL_FRAMES) prerollCount++;
        }
    }

    private static double rms(short[] samples, int len) {
        double sum = 0;
        for (int i = 0; i < len; i++) sum += (double) samples[i] * samples[i];
        return Math.sqrt(sum / Math.max(1, len));
    }

    private static void writeFrame(ByteArrayOutputStream out, short[] frame, int len) {
        for (int i = 0; i < len; i++) {
            short s = frame[i];
            out.write(s & 0xFF);
            out.write((s >> 8) & 0xFF);
        }
    }

    /** A candidate speech clip finished — ship it off for transcription without blocking the VAD loop. */
    private void handleSegment(final byte[] pcm) {
        if (inConversationFlow) return; // already mid-conversation, skip
        new Thread(new Runnable() {
            public void run() { transcribeAndCheck(pcm); }
        }, "greg-wakeword-upload").start();
    }

    // ── Transcription + wake check ──────────────────────────────────

    private void transcribeAndCheck(byte[] pcm) {
        try {
            byte[] wav = toWav(pcm);
            String b64 = android.util.Base64.encodeToString(wav, android.util.Base64.NO_WRAP);

            JSONObject req = new JSONObject();
            req.put("audio_b64", b64);
            req.put("mime", "audio/wav");

            String respBody = postJson(TRANSCRIBE_URL, req.toString(), 8000, 15000);
            if (respBody == null) return;
            JSONObject resp = new JSONObject(respBody);
            String text = resp.optString("text", "");
            if (text.length() == 0) return;

            android.util.Log.d("greg-wake", "heard: " + text);

            if (WAKE_PATTERN.matcher(text).find()) {
                beginConversation();
            }
        } catch (Exception e) {
            android.util.Log.w("greg-wake", "transcribe failed", e);
        }
    }

    // ── Conversation flow (reuses the proven push-to-talk pattern) ──

    private void beginConversation() {
        if (inConversationFlow) return;
        inConversationFlow = true;
        // Stop the VAD loop's own mic use before SpeechRecognizer claims the mic.
        stopVadLoop();
        mainHandler.post(new Runnable() {
            public void run() { doStartListening(); }
        });
    }

    private void endConversation() {
        inConversationFlow = false;
        startVadLoop();
    }

    private void doStartListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            endConversation();
            return;
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        }
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) {
                endConversation();
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    askGreg(matches.get(0));
                } else {
                    endConversation();
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        try {
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            endConversation();
        }
    }

    private void askGreg(final String question) {
        new Thread(new Runnable() {
            public void run() {
                String reply = "Hmm, ask me again?";
                try {
                    JSONObject req = new JSONObject();
                    req.put("question", question);
                    req.put("person", "dwight");
                    req.put("surface", "oneplus6t-wakeword");
                    String respBody = postJson(CHAT_URL, req.toString(), 5000, 20000);
                    if (respBody != null) {
                        JSONObject resp = new JSONObject(respBody);
                        String r = resp.optString("response", "");
                        if (r.length() > 0) reply = r;
                    }
                } catch (Exception e) {
                    android.util.Log.w("greg-wake", "ask failed", e);
                }
                final String finalReply = reply;
                mainHandler.post(new Runnable() {
                    public void run() { doSpeak(finalReply); }
                });
            }
        }, "greg-wakeword-ask").start();
    }

    private void doSpeak(String text) {
        if (text == null || text.length() == 0) { endConversation(); return; }
        if (tts == null) {
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override public void onInit(int status) {
                    ttsReady = (status == TextToSpeech.SUCCESS);
                    if (ttsReady) {
                        tts.setLanguage(Locale.US);
                        if (pendingSpeech != null) { speakNow(pendingSpeech); pendingSpeech = null; }
                    } else {
                        endConversation();
                    }
                }
            });
            pendingSpeech = text;
            return;
        }
        if (!ttsReady) { pendingSpeech = text; return; }
        speakNow(text);
    }

    private void speakNow(String text) {
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}
            @Override public void onDone(String utteranceId) {
                endConversation();
            }
            @Override public void onError(String utteranceId) {
                endConversation();
            }
        });
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "greg_wake_utt_" + System.currentTimeMillis());
    }

    // ── HTTP + WAV helpers ───────────────────────────────────────────

    private static String postJson(String urlStr, String json, int connectTimeoutMs, int readTimeoutMs) {
        HttpURLConnection c = null;
        try {
            URL u = new URL(urlStr);
            c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(connectTimeoutMs);
            c.setReadTimeout(readTimeoutMs);
            c.setRequestProperty("Content-Type", "application/json");
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(body.length);
            OutputStream os = c.getOutputStream();
            os.write(body);
            os.flush();
            os.close();
            int code = c.getResponseCode();
            java.io.InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            if (is == null) return null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            is.close();
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            android.util.Log.w("greg-wake", "post failed: " + urlStr, e);
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Wraps raw 16-bit mono PCM in a standard 44-byte WAV header. */
    private static byte[] toWav(byte[] pcm) {
        int byteRate = SAMPLE_RATE * 2;
        int dataLen = pcm.length;
        int totalLen = 36 + dataLen;
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + dataLen);
        writeStr(out, "RIFF");
        writeIntLE(out, totalLen);
        writeStr(out, "WAVE");
        writeStr(out, "fmt ");
        writeIntLE(out, 16);
        writeShortLE(out, (short) 1);  // PCM
        writeShortLE(out, (short) 1);  // mono
        writeIntLE(out, SAMPLE_RATE);
        writeIntLE(out, byteRate);
        writeShortLE(out, (short) 2);  // block align
        writeShortLE(out, (short) 16); // bits per sample
        writeStr(out, "data");
        writeIntLE(out, dataLen);
        out.write(pcm, 0, pcm.length);
        return out.toByteArray();
    }

    private static void writeStr(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        out.write(b, 0, b.length);
    }
    private static void writeIntLE(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF); out.write((v >> 8) & 0xFF); out.write((v >> 16) & 0xFF); out.write((v >> 24) & 0xFF);
    }
    private static void writeShortLE(ByteArrayOutputStream out, short v) {
        out.write(v & 0xFF); out.write((v >> 8) & 0xFF);
    }
}
