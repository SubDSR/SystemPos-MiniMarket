@echo off
chcp 65001 >nul 2>&1
setlocal

:: ============================================================
::  MiniMarket POS — Rebuild Completo
::  Compila CLIENTE + SERVIDOR y genera los 4 JARs:
::    POSClient.jar  Send.jar  Server.jar  Update.jar
:: ============================================================

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

echo.
echo  =======================================================
echo   MiniMarket POS  ^|  Rebuild Completo del Sistema
echo  =======================================================
echo   Timestamp: %DATE% %TIME%
echo   Directorio: %ROOT%
echo  =======================================================
echo.

:: ── Verificar estructura del proyecto ──────────────────────
if not exist "%ROOT%\APPLICATION\src\main\java" (
    echo  [ERROR] Estructura del proyecto incorrecta.
    echo  Asegurese de ejecutar este script desde la raiz del proyecto:
    echo  C:\ProyectosUniversidad\SystemPos-MiniMarket\
    pause & exit /b 1
)
if not exist "%ROOT%\SERVIDOR\src\main\java" (
    echo  [ERROR] Carpeta SERVIDOR\src\main\java no encontrada.
    pause & exit /b 1
)

:: ── Compilar Cliente ───────────────────────────────────────
echo  [PASO 1/2] Compilando modulo CLIENTE...
echo  -------------------------------------------------------
call "%ROOT%\build_client.bat" --no-pause
if %ERRORLEVEL% neq 0 (
    echo.
    echo  [FALLO] Build del cliente fallido.
    pause & exit /b 1
)

echo.
echo  [PASO 2/2] Compilando modulo SERVIDOR...
echo  -------------------------------------------------------
call "%ROOT%\build_server.bat" --no-pause
if %ERRORLEVEL% neq 0 (
    echo.
    echo  [FALLO] Build del servidor fallido.
    pause & exit /b 1
)

:: ── Verificar JARs generados ───────────────────────────────
echo.
echo  =======================================================
echo   Verificando artefactos generados...
echo  =======================================================
echo.

set ERRORS=0

call :check_jar "%ROOT%\APPLICATION\dist\POSClient.jar" "POSClient.jar"
call :check_jar "%ROOT%\APPLICATION\dist\Send.jar"      "Send.jar"
call :check_jar "%ROOT%\SERVIDOR\dist\Server.jar"       "Server.jar"
call :check_jar "%ROOT%\SERVIDOR\dist\Update.jar"       "Update.jar"

if %ERRORS% gtr 0 (
    echo.
    echo  [ADVERTENCIA] %ERRORS% artefacto(s) no generados correctamente.
    echo  Revise los errores de compilacion.
    pause & exit /b 1
)

:: ── Resumen final ──────────────────────────────────────────
echo.
echo  =======================================================
echo   REBUILD COMPLETO EXITOSO
echo  =======================================================
echo.
echo   JARs del CLIENTE (APPLICATION\dist\):
echo     java -jar APPLICATION\dist\POSClient.jar
echo     java -jar APPLICATION\dist\Send.jar
echo     java -jar APPLICATION\dist\Send.jar --headless
echo.
echo   JARs del SERVIDOR (SERVIDOR\dist\):
echo     java -jar SERVIDOR\dist\Server.jar
echo     java -jar SERVIDOR\dist\Update.jar
echo     java -jar SERVIDOR\dist\Update.jar --headless
echo     java -jar SERVIDOR\dist\Update.jar --once
echo.
echo   Flujo de trabajo:
echo     1. Abrir cliente:  java -jar APPLICATION\dist\POSClient.jar
echo     2. Registrar ventas en la interfaz POS
echo     3. Sincronizar:    java -jar APPLICATION\dist\Send.jar
echo     4. En servidor:    java -jar SERVIDOR\dist\Server.jar
echo        (o Update.jar para procesamiento headless)
echo.
echo  =======================================================
echo.
pause
goto :eof

:: ── Subrutina de verificacion de JAR ───────────────────────
:check_jar
if exist "%~1" (
    for %%F in ("%~1") do echo   [OK]  %~2  ^(%%~zF bytes^)
) else (
    echo   [FAIL] %~2  --- NO GENERADO
    set /a ERRORS+=1
)
goto :eof
