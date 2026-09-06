@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET F=-s GCC19D04134608TV

REM Check if Roku app is already installed on Fire
%ADB% %F% shell pm list packages roku > "D:\Tools\greg-kiosk\stdout.txt" 2>&1

REM Try to open Amazon Appstore to the Roku app page
%ADB% %F% shell am start -a android.intent.action.VIEW -d "amzn://apps/android?p=com.roku.remote" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
