@echo off
chcp 65001 >nul 2>&1
setlocal

:: ============================================================
::  MiniMarket POS — Lanzador de Send.jar
::  Sincroniza datos locales hacia la carpeta DATOS/ (UNC)
::  Uso:
::    run_send.bat           → GUI Swing
::    run_send.bat headless  → Modo consola (sin ventana)
:: ============================================================

set "ROOT=%~dp0.."
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "JAR=%ROOT%\APPLICATION\dist\Send.jar"
set "MODE="
if /i "%~1"=="headless" set "MODE=--headless"

echo.
echo   MiniMarket POS ^| Modulo Send - Sincronizacion
echo   ==========================================

where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo   [ERROR] java no encontrado en PATH.
    pause & exit /b 1
)

if not exist "%JAR%" (
    echo   Send.jar no encontrado. Compilando...
    call "%ROOT%\build_client.bat" --no-pause
    if %ERRORLEVEL% neq 0 (
        echo   [ERROR] Compilacion fallida.
        pause & exit /b 1
    )
)

echo   Iniciando: %JAR% %MODE%
echo   ==========================================
echo.

set "MINIMARKET_HOME=%ROOT%"
java -jar "%JAR%" %MODE%

endlocal
