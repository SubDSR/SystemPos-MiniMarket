@echo off
echo ==========================================
echo Compilando Sistema POS MiniMarket CS + DWH
echo ==========================================
if not exist out mkdir out
if not exist out\cliente mkdir out\cliente
if not exist out\servidor mkdir out\servidor
if not exist out\dwh mkdir out\dwh
if not exist CLIENTE_POS\dist mkdir CLIENTE_POS\dist
if not exist SERVIDOR_APLICACIONES\dist mkdir SERVIDOR_APLICACIONES\dist

:: Compilar cliente -> out\cliente
dir /s /b CLIENTE_POS\src\main\java\*.java > sources_cliente.txt
javac --release 17 -encoding UTF-8 -d out\cliente @sources_cliente.txt
if errorlevel 1 goto error

:: Compilar servidor -> out\servidor
dir /s /b SERVIDOR_APLICACIONES\src\main\java\*.java > sources_servidor.txt
javac --release 17 -encoding UTF-8 -cp lib\sqlite-jdbc.jar -d out\servidor @sources_servidor.txt
if errorlevel 1 goto error

:: Compilar DWH -> out\dwh
dir /s /b SERVIDOR_DWH\src\*.java > sources_dwh.txt
javac --release 17 -encoding UTF-8 -cp lib\sqlite-jdbc.jar -d out\dwh @sources_dwh.txt
if errorlevel 1 goto error

:: Empaquetar JARs - ambos se despliegan en SERVIDOR_APLICACIONES\dist\
jar cfm SERVIDOR_APLICACIONES\dist\POSClient.jar manifest_cliente.mf -C out\cliente .
if errorlevel 1 goto error
jar cfm SERVIDOR_APLICACIONES\dist\Server.jar  manifest_server.mf  -C out\servidor .
if errorlevel 1 goto error

echo Compilacion finalizada.
pause
exit /b 0

:error
echo Error durante la compilacion.
pause
exit /b 1
