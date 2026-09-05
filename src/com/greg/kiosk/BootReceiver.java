package com.greg.kiosk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Start httpd for local dashboard serving
            try {
                Runtime.getRuntime().exec(new String[]{
                    "/system/bin/busybox", "httpd", "-p", "127.0.0.1:8080", "-h", "/sdcard"
                });
            } catch (Exception e) { /* best effort */ }

            // Launch the kiosk activity
            Intent launch = new Intent(context, KioskActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launch.setData(android.net.Uri.parse("http://127.0.0.1:8080/dashboard.html"));
            context.startActivity(launch);
        }
    }
}
