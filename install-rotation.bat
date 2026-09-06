@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET S=-s 3481E0000131
SET CURL="D:\Program Files\Git\mingw64\bin\curl.exe"

echo === Downloading Rotation Control === > "D:\Tools\greg-kiosk\stdout.txt"
%CURL% -sL "https://f-droid.org/repo/com.pranavpandey.rotation_30.apk" -o "D:\Tools\greg-kiosk\rotation.apk" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
dir "D:\Tools\greg-kiosk\rotation.apk" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

REM If that didn't work try alternate
for %%F in ("D:\Tools\greg-kiosk\rotation.apk") do if %%~zF LSS 1000 (
  echo Trying alternate... >> "D:\Tools\greg-kiosk\stdout.txt"
  %CURL% -sL "https://f-droid.org/repo/org.crape.rotationcontrol_11.apk" -o "D:\Tools\greg-kiosk\rotation.apk" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
  dir "D:\Tools\greg-kiosk\rotation.apk" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
)

echo === Installing === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% install "D:\Tools\greg-kiosk\rotation.apk" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

echo === Done === >> "D:\Tools\greg-kiosk\stdout.txt"
type "D:\Tools\greg-kiosk\stdout.txt"
