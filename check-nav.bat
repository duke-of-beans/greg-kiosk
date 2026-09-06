@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET S=-s 3481E0000131

echo === Nav overlays === > "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell cmd overlay list >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

echo === Current nav mode === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell settings get secure navigation_mode >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

echo === Try showing nav bar globally === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell settings put global policy_control null >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell wm overscan 0,0,0,0 >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
