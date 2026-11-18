@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "POWERSHELL_EXE="

where powershell.exe >nul 2>&1
if not errorlevel 1 set "POWERSHELL_EXE=powershell.exe"

if not defined POWERSHELL_EXE (
    where pwsh.exe >nul 2>&1
    if not errorlevel 1 set "POWERSHELL_EXE=pwsh.exe"
)

if not defined POWERSHELL_EXE (
    echo ERROR: No se encontro Windows PowerShell ni PowerShell 7 en PATH. 1>&2
    exit /b 1
)

"%POWERSHELL_EXE%" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%setup-secrets.ps1"
set "SCRIPT_EXIT_CODE=%ERRORLEVEL%"

if not "%SCRIPT_EXIT_CODE%"=="0" (
    echo ERROR: No se pudo preparar la configuracion local. 1>&2
)

exit /b %SCRIPT_EXIT_CODE%
