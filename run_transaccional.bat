@echo off
set "JAVA_EXE=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

:: 1. Arrancar el Servidor de Aplicaciones
START "AppServer" "%JAVA_EXE%" -jar SERVIDOR_APLICACIONES\dist\Server.jar
timeout /t 3 /nobreak

:: 2. ACCESO DIRECTO - el cliente lanza el JAR que reside en el servidor
:: Simula: terminal hace doble-clic en acceso_pos.bat -> ejecuta JAR de SERVIDOR_APLICACIONES
:: Internamente el JAR se conecta al AppServer por Socket TCP puerto 9090
START "Cliente_01" "%JAVA_EXE%" -jar SERVIDOR_APLICACIONES\dist\POSClient.jar
START "Cliente_02" "%JAVA_EXE%" -jar SERVIDOR_APLICACIONES\dist\POSClient.jar
