@echo off
chcp 65001 >nul 2>&1
setlocal

:: ============================================================
::  MiniMarket POS — Lanzador del Cliente POS
::  Ejecuta POSClient.jar con java -jar (sin Maven)
::  Si el JAR no existe, compila primero automaticamente.
:: ============================================================

set "ROOT=%~dp0.."
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "JAR=%ROOT%\APPLICATION\dist\POSClient.jar"

echo.
echo   MiniMarket POS ^| Cliente
echo   ==========================================

:: Verificar Java
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo   [ERROR] java no encontrado en PATH.
    echo   Instale JRE 17+ y agregue java al PATH.
    pause & exit /b 1
)

:: Verificar JAR - si no existe, compilar
if not exist "%JAR%" (
    echo   POSClient.jar no encontrado. Compilando...
    echo.
    call "%ROOT%\build_client.bat" --no-pause
    if %ERRORLEVEL% neq 0 (
        echo   [ERROR] Compilacion fallida.
        pause & exit /b 1
    )
)

if not exist "%JAR%" (
    echo   [ERROR] POSClient.jar sigue sin existir tras la compilacion.
    pause & exit /b 1
)

echo   Iniciando: %JAR%
echo   ==========================================
echo.

:: Establecer MINIMARKET_HOME para resolver rutas correctamente
set "MINIMARKET_HOME=%ROOT%"
java -jar "%JAR%"

endlocal
