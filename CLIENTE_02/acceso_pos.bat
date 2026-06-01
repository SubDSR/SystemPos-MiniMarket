@echo off
:: ================================================
:: ACCESO DIRECTO AL SERVIDOR DE APLICACIONES
:: El cliente NO tiene la app instalada localmente.
:: Intenta ejecutar el JAR desde la ruta UNC de red.
:: Si la carpeta compartida no existe, usa ruta local
:: (simulacion en una sola laptop).
:: ================================================

pushd "%~dp0.."
set "JAVA_EXE=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

:: Ruta UNC - carpeta compartida en red (escenario real)
set JAR=\\%COMPUTERNAME%\APLICACIONES\POSClient.jar

:: Fallback - ruta local (simulacion en una laptop)
if not exist "%JAR%" set JAR=SERVIDOR_APLICACIONES\dist\POSClient.jar

echo [Cliente] Hostname: %COMPUTERNAME%
echo [Cliente] Ejecutando JAR desde: %JAR%
"%JAVA_EXE%" -jar "%JAR%"
popd
pause
