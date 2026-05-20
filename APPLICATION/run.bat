@echo off
:: ============================================================
:: MiniMarket POS — Ejecutar cliente Java (Swing / Java SE)
:: Requiere: Java 17+, Maven 3.8+
:: Ejecucion: run.bat (desde la carpeta APPLICATION\)
:: ============================================================
setlocal
cd /d "%~dp0"
echo Iniciando MiniMarket POS (Java SE + Swing)...
mvn compile exec:java -q
if %ERRORLEVEL% neq 0 (
    echo.
    echo ERROR: No se pudo iniciar la aplicacion.
    echo Asegurese de tener Java 17+ y Maven 3.8+ instalados.
    pause
)
endlocal
