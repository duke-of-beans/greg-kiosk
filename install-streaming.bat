@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET S=-s 3481E0000131
SET CURL="D:\Program Files\Git\mingw64\bin\curl.exe"
SET DIR=D:\Tools\greg-kiosk\apks
if not exist "%DIR%" mkdir "%DIR%"

REM === Download streaming APKs ===
REM These are the TV/tablet versions from APKMirror
REM Using universal/arm64 builds for Android 12 arm64

echo === Downloading streaming apps === > "D:\Tools\greg-kiosk\stdout.txt"

REM Tubi - free, likely works without GMS
echo Tubi... >> "D:\Tools\greg-kiosk\stdout.txt"
%CURL% -sL "https://d2f1geqydzp9oc.cloudfront.net/apks/tubi-tv-latest.apk" -o "%DIR%\tubi.apk" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

REM For Netflix, Disney+, Paramount+, etc - we need APKs that work with MicroG
REM Let's try installing from APKPure which provides direct download links

REM Netflix
echo Netflix... >> "D:\Tools\greg-kiosk\stdout.txt"
%CURL% -sL "https://d.apkpure.net/b/APK/com.netflix.mediaclient?versionCode=85040" -o "%DIR%\netflix.apk" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

dir "%DIR%\*.apk" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

echo === Installing apps === >> "D:\Tools\greg-kiosk\stdout.txt"
for %%f in ("%DIR%\*.apk") do (
  echo Installing %%~nf... >> "D:\Tools\greg-kiosk\stdout.txt"
  %ADB% %S% install "%%f" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
)

echo === Checking installed === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell pm list packages -3 >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

echo === Done === >> "D:\Tools\greg-kiosk\stdout.txt"
