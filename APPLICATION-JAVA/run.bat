@echo off
:: ============================================================
:: MiniMarket POS — Ejecutar cliente Java
:: ============================================================
:: Requiere: Java 17+, Maven 3.8+
:: Ejecucion: run.bat (desde la carpeta APPLICATION-JAVA\)
:: ============================================================

setlocal

:: Ruta raiz del proyecto (padre de APPLICATION-JAVA)
set "PROJECT_ROOT=%~dp0.."

:: Opcionalmente, establecer MINIMARKET_HOME si la deteccion automatica falla
:: set "MINIMARKET_HOME=%PROJECT_ROOT%"

echo Iniciando MiniMarket POS (Java)...
echo Proyecto: %PROJECT_ROOT%

cd /d "%~dp0"
mvn javafx:run -q

if %ERRORLEVEL% neq 0 (
    echo.
    echo ERROR: No se pudo iniciar la aplicacion.
    echo Asegurese de tener Java 17+ y Maven 3.8+ instalados.
    pause
)
endlocal
