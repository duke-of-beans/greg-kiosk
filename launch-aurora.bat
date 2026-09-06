@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET S=-s 3481E0000131

echo === All third-party apps on Skylight === > "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell pm list packages -3 >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

echo. >> "D:\Tools\greg-kiosk\stdout.txt"
echo === Launching Aurora Store === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell am force-stop com.greg.kiosk >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell am start -n com.aurora.store/.MainActivity >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
