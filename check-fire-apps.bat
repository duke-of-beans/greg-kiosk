@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET F=-s GCC19D04134608TV

echo === Streaming apps on Fire === > "D:\Tools\greg-kiosk\stdout.txt"
%ADB% %F% shell pm list packages -3 >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
echo. >> "D:\Tools\greg-kiosk\stdout.txt"

echo === Checking specific apps === >> "D:\Tools\greg-kiosk\stdout.txt"
for %%p in (netflix hulu disney paramount tubi pluto youtube spectrum) do (
  echo --- %%p --- >> "D:\Tools\greg-kiosk\stdout.txt"
  %ADB% %F% shell pm list packages %%p >> "D:\Tools\greg-kiosk\stdout.txt" 2>&1
)
