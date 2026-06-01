@echo off
setlocal

set "JAVA_EXE=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
set "CP=out\dwh;lib\sqlite-jdbc.jar;lib\slf4j-api.jar;lib\slf4j-nop.jar"

if not exist "out\dwh\ViewCrossTab.class" (
    echo [ERROR] No existe out\dwh\ViewCrossTab.class.
    echo Ejecute primero: build_all.bat
    pause
    exit /b 1
)

if not exist "SERVIDOR_DWH\repositorio_analitico\ventas_2d.ctab" (
    echo [ERROR] No existe SERVIDOR_DWH\repositorio_analitico\ventas_2d.ctab.
    echo Ejecute primero: run_cluster_analytics.bat
    pause
    exit /b 1
)

if not exist "SERVIDOR_DWH\repositorio_analitico\ventas_3d.ctab" (
    echo [ERROR] No existe SERVIDOR_DWH\repositorio_analitico\ventas_3d.ctab.
    echo Ejecute primero: run_cluster_analytics.bat
    pause
    exit /b 1
)

echo ==========================================
echo Visualizando Cubo 2D: Producto x Mes
echo ==========================================
"%JAVA_EXE%" -cp "%CP%" ViewCrossTab ventas_2d
if errorlevel 1 goto error

echo.
echo ==========================================
echo Visualizando Cubo 3D: Producto x Mes x Sucursal
echo ==========================================
"%JAVA_EXE%" -cp "%CP%" ViewCrossTab ventas_3d
if errorlevel 1 goto error

pause
exit /b 0

:error
echo [ERROR] No se pudieron visualizar los cubos .ctab.
pause
exit /b 1
