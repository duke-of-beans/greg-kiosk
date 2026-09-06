@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET S=-s 3481E0000131

echo === Reloading dashboard === > "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell am force-stop com.greg.kiosk >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell am start -n com.greg.kiosk/.KioskActivity -d "https://greg-skylight-dashboard-davids-projects-b0509900.vercel.app" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

echo === Check if Calendar and Maps are installed === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell pm list packages | findstr "calendar" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell pm list packages | findstr "maps" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell pm list packages | findstr "spotify" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
echo === Done === >> "D:\Tools\greg-kiosk\stdout.txt"
type "D:\Tools\greg-kiosk\stdout.txt"
