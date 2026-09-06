@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET S=-s 3481E0000131

REM Check Android version, Widevine level, and what's installed
echo === DEVICE INFO === > "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell getprop ro.build.version.release >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
echo. >> "D:\Tools\greg-kiosk\stdout.txt"

echo === WIDEVINE === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell getprop ro.com.google.clientidbase >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell ls /vendor/lib/libwvhidl.so /vendor/lib/mediadrm/libwvdrmengine.so 2>nul >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell "dumpsys media.drm 2>/dev/null | head -20" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
echo. >> "D:\Tools\greg-kiosk\stdout.txt"

echo === PLAY STORE / APPSTORE === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell pm list packages google 2>nul >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell pm list packages amazon 2>nul >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
echo. >> "D:\Tools\greg-kiosk\stdout.txt"

echo === EXISTING STREAMING APPS === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell pm list packages netflix >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell pm list packages youtube >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell pm list packages disney >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell pm list packages hulu >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell pm list packages tubi >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell pm list packages paramount >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell pm list packages pluto >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell pm list packages spectrum >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
echo. >> "D:\Tools\greg-kiosk\stdout.txt"

echo === SCREEN RES === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell wm size >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell wm density >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

echo === CPU ARCH === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell getprop ro.product.cpu.abi >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
