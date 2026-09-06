@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET S=-s 3481E0000131

REM Check Widevine level
%ADB% %S% shell "cat /vendor/etc/init/android.hardware.drm@1.4-service.widevine.rc 2>/dev/null || cat /vendor/etc/init/android.hardware.drm@1.3-service.widevine.rc 2>/dev/null || echo 'no widevine init'" > "D:\Tools\greg-kiosk\stdout.txt" 2>&1
echo === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell "getprop ro.com.google.gmsversion" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
echo === >> "D:\Tools\greg-kiosk\stdout.txt"
REM Check if device is certified
%ADB% %S% shell "getprop ro.product.first_api_level" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
echo === >> "D:\Tools\greg-kiosk\stdout.txt"
REM Direct widevine level check
%ADB% %S% shell "getprop drm.service.enabled" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
echo === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell "ls /vendor/lib/mediadrm/" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
