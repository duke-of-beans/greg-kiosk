@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET S=-s 3481E0000131

REM Launch Aurora Store so David can search for "Rotation Control"
%ADB% %S% shell am force-stop com.greg.kiosk > "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell am start -n com.aurora.store/.MainActivity >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
type "D:\Tools\greg-kiosk\stdout.txt"
