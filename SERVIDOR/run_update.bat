@echo off
chcp 65001 >nul 2>&1
setlocal

:: ============================================================
::  MiniMarket POS — Lanzador de Update.jar
::  Monitorea DATOS/ y actualiza SQLite del servidor
::  Uso:
::    run_update.bat           → GUI Swing
::    run_update.bat headless  → Monitoreo continuo (consola)
::    run_update.bat once      → Un scan y termina (consola)
:: ============================================================

set "ROOT=%~dp0.."
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "JAR=%ROOT%\SERVIDOR\dist\Update.jar"
set "MODE="
if /i "%~1"=="headless" set "MODE=--headless"
if /i "%~1"=="once"     set "MODE=--once"

echo.
echo   MiniMarket POS ^| Modulo Update - Procesador
echo   ==========================================

where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo   [ERROR] java no encontrado en PATH.
    pause & exit /b 1
)

if not exist "%JAR%" (
    echo   Update.jar no encontrado. Compilando...
    call "%ROOT%\build_server.bat" --no-pause
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
