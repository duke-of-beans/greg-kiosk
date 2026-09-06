@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET S=-s 3481E0000131

REM Kill Aurora Store and any other foreground app
%ADB% %S% shell am force-stop com.aurora.store > "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell am force-stop com.greg.kiosk >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
timeout /t 1 /nobreak > nul

REM Relaunch greg-kiosk with dashboard
%ADB% %S% shell am start -n com.greg.kiosk/.KioskActivity -d "https://greg-skylight-dashboard-davids-projects-b0509900.vercel.app" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
type "D:\Tools\greg-kiosk\stdout.txt"
