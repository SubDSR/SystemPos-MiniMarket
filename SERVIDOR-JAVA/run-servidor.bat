@echo off
:: ============================================================
:: MiniMarket POS — Ejecutar servidor Java
:: ============================================================
:: Requiere: Java 17+, Maven 3.8+
:: Ejecucion: run-servidor.bat (desde la carpeta SERVIDOR-JAVA\)
:: ============================================================

setlocal

set "PROJECT_ROOT=%~dp0.."

echo Iniciando Servidor Central MiniMarket (Java)...
echo Proyecto: %PROJECT_ROOT%

cd /d "%~dp0"
mvn javafx:run -q

if %ERRORLEVEL% neq 0 (
    echo.
    echo ERROR: No se pudo iniciar el servidor.
    echo Asegurese de tener Java 17+ y Maven 3.8+ instalados.
    pause
)
endlocal
