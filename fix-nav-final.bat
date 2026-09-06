@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET S=-s 3481E0000131

echo === Check available accessibility services === > "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell pm list packages accessibility >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
echo --- >> "D:\Tools\greg-kiosk\stdout.txt"

echo === Try enabling Accessibility Menu === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.accessibility.accessibilitymenu.AccessibilityMenuService >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell settings put secure accessibility_enabled 1 >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
echo --- >> "D:\Tools\greg-kiosk\stdout.txt"

echo === Check if SystemUI has nav capabilities === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell dumpsys window | find "mNavigationBar" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
echo --- >> "D:\Tools\greg-kiosk\stdout.txt"

echo === Check current overlay state === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell cmd overlay list | find "navbar" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
echo --- >> "D:\Tools\greg-kiosk\stdout.txt"

echo === Force show nav bar via settings === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell settings put global policy_control null* >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell su -c "settings put secure navigation_mode 0" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

echo === Done === >> "D:\Tools\greg-kiosk\stdout.txt"
type "D:\Tools\greg-kiosk\stdout.txt"
