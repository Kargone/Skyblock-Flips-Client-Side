@echo off
set "MODS=C:\Users\karso\.lunarclient\profiles\26\mods\fabric-26.1.2"

call gradlew.bat clean build || exit /b 1

del /Q "%MODS%\Skyblock-Flips-Client-Side-*.jar" 2>nul

for /f "delims=" %%f in ('dir /b "build\libs\Skyblock-Flips-Client-Side-*.jar" ^| findstr /v /i "sources"') do (
    move /Y "build\libs\%%f" "%MODS%\"
)