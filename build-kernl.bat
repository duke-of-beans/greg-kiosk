@echo off
cd /d "D:\Projects\Project Mind\kernl-mcp"
"D:\Program Files\nodejs\node.exe" "node_modules\typescript\bin\tsc" > "D:\Tools\greg-kiosk\stdout.txt" 2>&1
echo EXIT_CODE=%ERRORLEVEL% >> "D:\Tools\greg-kiosk\stdout.txt"
