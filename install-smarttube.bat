@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET S=-s 3481E0000131
SET CURL="D:\Program Files\Git\mingw64\bin\curl.exe"

echo Downloading SmartTubeNext... > "D:\Tools\greg-kiosk\stdout.txt"
%CURL% -sL "https://github.com/yuliskov/SmartTube/releases/download/32.38s/SmartTube_stable_32.38_arm64-v8a.apk" -o "D:\Tools\greg-kiosk\smarttube.apk" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

dir "D:\Tools\greg-kiosk\smarttube.apk" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

echo Installing to Skylight... >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% install "D:\Tools\greg-kiosk\smarttube.apk" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

echo Done >> "D:\Tools\greg-kiosk\stdout.txt"
