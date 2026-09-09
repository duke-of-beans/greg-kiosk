@echo off
SET SDK=D:\Tools\android-sdk
SET BT=%SDK%\build-tools\35.0.0
SET PLATFORM=%SDK%\platforms\android-35\android.jar
SET JAVAC="C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot\bin\javac.exe"
SET SRC=D:\Tools\greg-kiosk
SET OUT=%SRC%\build
SET CLS=%OUT%\classes\com\greg\kiosk

if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%\classes"

echo === COMPILING ===
REM Glob every .java rather than listing them by hand. The old script named four
REM files explicitly, so adding a class meant remembering to edit this line --
REM and a forgotten entry fails as a confusing "cannot find symbol" rather than
REM an obviously missing file.
setlocal enabledelayedexpansion
set "SRCS="
for %%f in ("%SRC%\src\com\greg\kiosk\*.java") do set "SRCS=!SRCS! "%%f""
echo Sources:!SRCS!
%JAVAC% -source 1.8 -target 1.8 -classpath "%PLATFORM%" -d "%OUT%\classes" !SRCS! 2>&1
endlocal

echo === LISTING CLASSES ===
dir /b /s "%OUT%\classes\*.class"

echo === DEXING ===
REM Pass all class files using a for loop to build the command
setlocal enabledelayedexpansion
set "FILES="
for /r "%OUT%\classes" %%f in (*.class) do set "FILES=!FILES! "%%f""
call "%BT%\d8.bat" --output "%OUT%" --lib "%PLATFORM%" !FILES! 2>&1
endlocal

echo === COMPILING RESOURCES ===
REM res/xml/network_security_config.xml + res/raw/sentinel.crt (trusted Sentinel cert)
"%BT%\aapt2.exe" compile --dir "%SRC%\res" -o "%OUT%\res.zip" 2>&1

echo === PACKAGING ===
"%BT%\aapt2.exe" link -o "%OUT%\greg-kiosk-unsigned.apk" --manifest "%SRC%\AndroidManifest.xml" -I "%PLATFORM%" --min-sdk-version 28 --target-sdk-version 28 --auto-add-overlay "%OUT%\res.zip" 2>&1

echo === ADDING DEX ===
cd /d "%OUT%"
copy /b "%OUT%\greg-kiosk-unsigned.apk" "%OUT%\greg-kiosk.apk" >nul
"%BT%\aapt.exe" add "%OUT%\greg-kiosk.apk" classes.dex 2>&1

echo === SIGNING ===
call "%BT%\apksigner.bat" sign --ks "%USERPROFILE%\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android "%OUT%\greg-kiosk.apk" 2>&1

echo === RESULT ===
dir "%OUT%\greg-kiosk.apk" 2>&1
