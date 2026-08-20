@echo off
setlocal
cd /d "%~dp0"

where pwsh.exe >nul 2>&1
if %errorlevel% equ 0 (
    pwsh.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-project.ps1" -Mode Local %*
) else (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-project.ps1" -Mode Local %*
)

set "START_EXIT=%errorlevel%"
if not "%START_EXIT%"=="0" (
    echo.
    echo Local IDE preparation failed. Review the diagnostic message above.
    pause
)
exit /b %START_EXIT%
