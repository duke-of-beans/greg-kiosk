@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET S=-s 3481E0000131

echo === Force landscape globally === > "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell settings put system accelerometer_rotation 0 >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell settings put system user_rotation 1 >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

echo === Enable gesture navigation (swipe up = home, swipe edge = back) === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell cmd overlay enable com.android.internal.systemui.navbar.gestural >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

echo === Verify === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell settings get system user_rotation >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell settings get system accelerometer_rotation >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

echo === Done === >> "D:\Tools\greg-kiosk\stdout.txt"
type "D:\Tools\greg-kiosk\stdout.txt"
