@echo off
setlocal
cd /d "%~dp0"

where pwsh.exe >nul 2>&1
if %errorlevel% equ 0 (
    pwsh.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\stop-project.ps1" %*
) else (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\stop-project.ps1" %*
)

set "STOP_EXIT=%errorlevel%"
if not "%STOP_EXIT%"=="0" (
    echo.
    echo Cleanup failed. Review the diagnostic message above.
    pause
)
exit /b %STOP_EXIT%
