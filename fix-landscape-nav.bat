@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET S=-s 3481E0000131

echo === Force landscape harder === > "D:\Tools\greg-kiosk\stdout.txt"
REM Android 12+ display rotation lock
%ADB% %S% shell cmd display set-user-rotation fixed 1 >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
REM Also try wm approach
%ADB% %S% shell wm set-fix-to-user-rotation enabled >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

echo === Check available nav overlays === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell cmd overlay list | findstr navbar >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

echo === Enable 3-button nav bar === >> "D:\Tools\greg-kiosk\stdout.txt"
REM Try the 3-button overlay
%ADB% %S% shell cmd overlay enable com.android.internal.systemui.navbar.threebutton >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
REM Disable gestural if it was set
%ADB% %S% shell cmd overlay disable com.android.internal.systemui.navbar.gestural >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

echo === Done === >> "D:\Tools\greg-kiosk\stdout.txt"
type "D:\Tools\greg-kiosk\stdout.txt"
