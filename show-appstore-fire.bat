@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET F=-s GCC19D04134608TV

REM Stop greg-kiosk temporarily so the Appstore is visible
%ADB% %F% shell am force-stop com.greg.kiosk > "D:\Tools\greg-kiosk\stdout.txt" 2>&1
REM Open Appstore to Roku app
%ADB% %F% shell am start -a android.intent.action.VIEW -d "amzn://apps/android?p=com.roku.remote" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
type "D:\Tools\greg-kiosk\stdout.txt"
