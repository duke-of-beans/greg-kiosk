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
%JAVAC% -source 1.8 -target 1.8 -classpath "%PLATFORM%" -d "%OUT%\classes" "%SRC%\src\com\greg\kiosk\KioskActivity.java" "%SRC%\src\com\greg\kiosk\BootReceiver.java" "%SRC%\src\com\greg\kiosk\FloatingHomeService.java" 2>&1

echo === LISTING CLASSES ===
dir /b /s "%OUT%\classes\*.class"

echo === DEXING ===
REM Pass all class files using a for loop to build the command
setlocal enabledelayedexpansion
set "FILES="
for /r "%OUT%\classes" %%f in (*.class) do set "FILES=!FILES! "%%f""
call "%BT%\d8.bat" --output "%OUT%" --lib "%PLATFORM%" !FILES! 2>&1
endlocal

echo === PACKAGING ===
"%BT%\aapt2.exe" link -o "%OUT%\greg-kiosk-unsigned.apk" --manifest "%SRC%\AndroidManifest.xml" -I "%PLATFORM%" --min-sdk-version 28 --target-sdk-version 28 2>&1

echo === ADDING DEX ===
cd /d "%OUT%"
copy /b "%OUT%\greg-kiosk-unsigned.apk" "%OUT%\greg-kiosk.apk" >nul
"%BT%\aapt.exe" add "%OUT%\greg-kiosk.apk" classes.dex 2>&1

echo === SIGNING ===
call "%BT%\apksigner.bat" sign --ks "%USERPROFILE%\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android "%OUT%\greg-kiosk.apk" 2>&1

echo === RESULT ===
dir "%OUT%\greg-kiosk.apk" 2>&1
