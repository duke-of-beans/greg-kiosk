@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET S=-s 3481E0000131

echo === All third-party apps on Skylight === > "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell pm list packages -3 >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
