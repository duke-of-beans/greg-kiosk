@echo off
SET ADB="D:\Tools\android-sdk\platform-tools\adb.exe"
SET S=-s 3481E0000131
%ADB% %S% shell "pm list packages | grep -E 'calendar|maps|spotify'"
