@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET S=-s 3481E0000131

REM Re-enable Bluetooth service on Skylight
%ADB% %S% shell svc bluetooth enable > "D:\Tools\greg-kiosk\stdout.txt" 2>&1

REM Check status
%ADB% %S% shell settings get global bluetooth_on >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

REM Make discoverable
%ADB% %S% shell am start -a android.bluetooth.adapter.action.REQUEST_DISCOVERABLE --ei android.bluetooth.adapter.extra.DISCOVERABLE_DURATION 300 >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
