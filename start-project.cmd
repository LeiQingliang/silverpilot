@echo off
setlocal
cd /d "%~dp0"
set "BROWSER_ARG=-OpenBrowser"
if /I "%SILVERPILOT_SKIP_BROWSER%"=="1" set "BROWSER_ARG="
set "CONSOLE_ARG=-KeepConsoleOpen"
if /I "%SILVERPILOT_NO_PAUSE%"=="1" set "CONSOLE_ARG="

where pwsh.exe >nul 2>&1
if %errorlevel% equ 0 (
    pwsh.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-project.ps1" %BROWSER_ARG% %CONSOLE_ARG% %*
) else (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-project.ps1" %BROWSER_ARG% %CONSOLE_ARG% %*
)

set "START_EXIT=%errorlevel%"
if not "%START_EXIT%"=="0" (
    echo.
    echo Startup failed. The diagnostic message above identifies the blocked layer.
    pause
)
exit /b %START_EXIT%
