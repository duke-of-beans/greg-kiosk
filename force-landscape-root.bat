@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET S=-s 3481E0000131

REM Force rotation override for all apps via root
%ADB% %S% shell su -c "settings put system accelerometer_rotation 0" > "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell su -c "settings put system user_rotation 1" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
REM This is the nuclear option — ignores app-requested orientation on rooted devices
%ADB% %S% shell su -c "setprop persist.demo.rotation_lock 1" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell su -c "setprop persist.demo.rotation_lock_mode 1" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
type "D:\Tools\greg-kiosk\stdout.txt"
