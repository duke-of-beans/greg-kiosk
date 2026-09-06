@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET S=-s 3481E0000131

echo === BUILDING === > "D:\Tools\greg-kiosk\stdout.txt"
call "D:\Tools\greg-kiosk\build.bat" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

echo === INSTALLING === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% install -r "D:\Tools\greg-kiosk\build\greg-kiosk.apk" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

echo === LAUNCHING === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell am start -n com.greg.kiosk/.KioskActivity -d "https://greg-skylight-dashboard-davids-projects-b0509900.vercel.app" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

echo === DONE === >> "D:\Tools\greg-kiosk\stdout.txt"
