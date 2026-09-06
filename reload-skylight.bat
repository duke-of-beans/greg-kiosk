@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET S=-s 3481E0000131

REM Force reload the Skylight's WebView by relaunching with the URL
%ADB% %S% shell am force-stop com.greg.kiosk > "D:\Tools\greg-kiosk\stdout.txt" 2>&1
timeout /t 2 /nobreak > nul
%ADB% %S% shell am start -n com.greg.kiosk/.KioskActivity -d "https://greg-skylight-dashboard-davids-projects-b0509900.vercel.app" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
type "D:\Tools\greg-kiosk\stdout.txt"
