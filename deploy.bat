@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET S=-s GCC19D04134608TV
REM Launch greg-kiosk with Vercel URL
%ADB% %S% shell am start -n com.greg.kiosk/.KioskActivity -d "https://greg-surface-web.vercel.app" > "D:\Tools\greg-kiosk\inst.txt" 2>&1
echo --- >> "D:\Tools\greg-kiosk\inst.txt"
REM Set as default home
%ADB% %S% shell cmd package set-home-activity com.greg.kiosk/.KioskActivity >> "D:\Tools\greg-kiosk\inst.txt" 2>&1
