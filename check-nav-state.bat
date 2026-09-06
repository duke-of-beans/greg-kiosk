@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET S=-s 3481E0000131

echo === Nav bar state === > "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell "dumpsys window | grep -i 'mNavigationBar\|navbar\|NavigationBar'" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
echo --- >> "D:\Tools\greg-kiosk\stdout.txt"

echo === Overlay state === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell "cmd overlay list | grep navbar" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
echo --- >> "D:\Tools\greg-kiosk\stdout.txt"

echo === Accessibility state === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell settings get secure enabled_accessibility_services >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell settings get secure accessibility_enabled >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
echo --- >> "D:\Tools\greg-kiosk\stdout.txt"

echo === Try enabling nav bar via prop === >> "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %S% shell su -c "setprop persist.sys.navbar.show true" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
%ADB% %S% shell su -c "setprop qemu.hw.mainkeys 0" >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1

echo === Done === >> "D:\Tools\greg-kiosk\stdout.txt"
type "D:\Tools\greg-kiosk\stdout.txt"
