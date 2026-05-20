@echo off
chcp 65001 >nul 2>&1
setlocal EnableDelayedExpansion

:: ============================================================
::  MiniMarket POS — Build Servidor
::  Genera: Server.jar  y  Update.jar  (fat JARs con SQLite)
::  Requisito: JDK 17+  (sin Maven, puro javac + jar)
::  Dependencia: sqlite-jdbc-3.45.3.0.jar (auto-descarga)
:: ============================================================

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

set "SRV_SRC=%ROOT%\SERVIDOR\src\main\java"
set "SRV_BUILD=%ROOT%\SERVIDOR\build\classes"
set "SRV_TMP=%ROOT%\SERVIDOR\build\tmp"
set "SRV_SQLITE=%ROOT%\SERVIDOR\build\sqlite"
set "SRV_DIST=%ROOT%\SERVIDOR\dist"
set "SRV_LIB=%ROOT%\SERVIDOR\lib"
set "SQLITE_JAR=%SRV_LIB%\sqlite-jdbc.jar"
set "SLF4J_API=%SRV_LIB%\slf4j-api.jar"
set "SLF4J_NOP=%SRV_LIB%\slf4j-nop.jar"
set "SQLITE_URL=https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.3.0/sqlite-jdbc-3.45.3.0.jar"
set "SLF4J_API_URL=https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.6/slf4j-api-2.0.6.jar"
set "SLF4J_NOP_URL=https://repo1.maven.org/maven2/org/slf4j/slf4j-nop/2.0.6/slf4j-nop-2.0.6.jar"

echo.
echo   +--------------------------------------------------+
echo   ^|   MiniMarket POS  ^|  Compilacion Servidor       ^|
echo   ^|   Server.jar  +  Update.jar  (fat JARs)         ^|
echo   +--------------------------------------------------+
echo.

:: ── Verificar JDK ──────────────────────────────────────────
where javac >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo   [ERROR] javac no encontrado.
    echo   Instale JDK 17+ y agregue JAVA_HOME\bin al PATH.
    pause & exit /b 1
)
for /f "tokens=3" %%v in ('javac -version 2^>^&1') do (
    echo   JDK detectado: %%v
)
echo.

:: ── Paso 0: Gestionar dependencias (SQLite + SLF4J) ─────────
echo   [0/5] Verificando dependencias...
if not exist "%SRV_LIB%" mkdir "%SRV_LIB%"

:: sqlite-jdbc
if exist "%SQLITE_JAR%" (
    echo         sqlite-jdbc.jar: OK
) else (
    echo         Descargando sqlite-jdbc 3.45.3.0...
    powershell -NoProfile -Command "Invoke-WebRequest -Uri '%SQLITE_URL%' -OutFile '%SQLITE_JAR%' -UseBasicParsing"
    if not exist "%SQLITE_JAR%" (
        echo   [ERROR] No se pudo descargar sqlite-jdbc.jar
        echo   URL: %SQLITE_URL%
        echo   Descarguela manualmente a: SERVIDOR\lib\sqlite-jdbc.jar
        pause & exit /b 1
    )
    echo         sqlite-jdbc.jar: descargado OK
)

:: slf4j-api (requerido por sqlite-jdbc 3.45+)
if exist "%SLF4J_API%" (
    echo         slf4j-api.jar: OK
) else (
    echo         Descargando slf4j-api 2.0.6...
    powershell -NoProfile -Command "Invoke-WebRequest -Uri '%SLF4J_API_URL%' -OutFile '%SLF4J_API%' -UseBasicParsing"
    if not exist "%SLF4J_API%" (
        echo   [ERROR] No se pudo descargar slf4j-api.jar
        pause & exit /b 1
    )
    echo         slf4j-api.jar: descargado OK
)

:: slf4j-nop (implementacion NOP para silenciar logging de sqlite)
if exist "%SLF4J_NOP%" (
    echo         slf4j-nop.jar: OK
) else (
    echo         Descargando slf4j-nop 2.0.6...
    powershell -NoProfile -Command "Invoke-WebRequest -Uri '%SLF4J_NOP_URL%' -OutFile '%SLF4J_NOP%' -UseBasicParsing"
    if not exist "%SLF4J_NOP%" (
        echo   [ERROR] No se pudo descargar slf4j-nop.jar
        pause & exit /b 1
    )
    echo         slf4j-nop.jar: descargado OK
)

:: ── Paso 1: Limpiar y crear directorios ────────────────────
echo   [1/5] Preparando directorios de build...
if exist "%SRV_BUILD%"  rmdir /s /q "%SRV_BUILD%"
if exist "%SRV_TMP%"    rmdir /s /q "%SRV_TMP%"
if exist "%SRV_SQLITE%" rmdir /s /q "%SRV_SQLITE%"
if exist "%SRV_DIST%"   rmdir /s /q "%SRV_DIST%"
mkdir "%SRV_BUILD%"  2>nul
mkdir "%SRV_TMP%"    2>nul
mkdir "%SRV_SQLITE%" 2>nul
mkdir "%SRV_DIST%"   2>nul
echo         OK

:: Crear directorios de runtime del servidor
if not exist "%ROOT%\SERVIDOR\database" mkdir "%ROOT%\SERVIDOR\database"
if not exist "%ROOT%\SERVIDOR\logs"     mkdir "%ROOT%\SERVIDOR\logs"
if not exist "%ROOT%\SERVIDOR\update"   mkdir "%ROOT%\SERVIDOR\update"
if not exist "%ROOT%\DATOS"            mkdir "%ROOT%\DATOS"

:: ── Paso 2: Recopilar fuentes .java (PowerShell → forward slashes, sin BOM) ──
echo   [2/5] Recopilando fuentes Java del servidor...
powershell -NoProfile -Command "$src='%SRV_SRC%'; $out='%SRV_TMP%\sources.txt'; $files = Get-ChildItem -Path $src -Filter '*.java' -Recurse | ForEach-Object { $_.FullName.Replace('\','/') }; if (-not $files) { exit 1 }; [System.IO.File]::WriteAllLines($out, $files, [System.Text.Encoding]::ASCII); Write-Host ('  ' + $files.Count + ' archivos')"
if %ERRORLEVEL% neq 0 (
    echo   [ERROR] No se encontraron fuentes en: %SRV_SRC%
    pause & exit /b 1
)
echo         OK - fuentes encontradas

:: ── Paso 3: Compilar con javac (classpath: sqlite-jdbc + slf4j) ─
echo   [3/5] Compilando con javac --release 17...
javac --release 17 -encoding UTF-8 -cp "%SQLITE_JAR%;%SLF4J_API%;%SLF4J_NOP%" -d "%SRV_BUILD%" "@%SRV_TMP%/sources.txt"
if %ERRORLEVEL% neq 0 (
    echo.
    echo   [ERROR] Compilacion fallida.
    echo   Revise los errores de javac arriba.
    pause & exit /b 1
)
echo         OK - compilacion exitosa

:: ── Paso 4: Extraer deps (sqlite + slf4j) para fat JAR ──────
echo   [4/5] Extrayendo dependencias para fat JAR...
pushd "%SRV_SQLITE%"
jar xf "%SQLITE_JAR%"
jar xf "%SLF4J_API%"
jar xf "%SLF4J_NOP%"
if %ERRORLEVEL% neq 0 (
    popd
    echo   [ERROR] No se pudo extraer dependencias
    pause & exit /b 1
)
popd
:: Eliminar MANIFEST.MF de deps para evitar conflicto con el nuestro
if exist "%SRV_SQLITE%\META-INF\MANIFEST.MF" del /f /q "%SRV_SQLITE%\META-INF\MANIFEST.MF"
echo         OK - dependencias extraidas (sqlite + slf4j)

:: ── Paso 5: Empaquetar fat JARs ────────────────────────────
echo   [5/5] Empaquetando fat JARs...

:: ── Crear manifests con PowerShell (sin BOM) ────────────────
powershell -NoProfile -Command "[System.IO.File]::WriteAllText('%SRV_TMP%\mf_server.mf', 'Main-Class: com.minimarket.server.ServerApp`r`n`r`n',       [System.Text.Encoding]::ASCII)"
powershell -NoProfile -Command "[System.IO.File]::WriteAllText('%SRV_TMP%\mf_update.mf', 'Main-Class: com.minimarket.server.update.Update`r`n`r`n', [System.Text.Encoding]::ASCII)"

:: ── Server.jar (fat: clases servidor + sqlite-jdbc) ────────
jar cfm "%SRV_DIST%\Server.jar" "%SRV_TMP%\mf_server.mf" ^
    -C "%SRV_BUILD%"  . ^
    -C "%SRV_SQLITE%" .
if %ERRORLEVEL% neq 0 (
    echo   [ERROR] No se pudo crear Server.jar
    pause & exit /b 1
)
echo         Server.jar - OK

:: ── Update.jar (fat: mismas clases, distinto Main-Class) ───
jar cfm "%SRV_DIST%\Update.jar" "%SRV_TMP%\mf_update.mf" ^
    -C "%SRV_BUILD%"  . ^
    -C "%SRV_SQLITE%" .
if %ERRORLEVEL% neq 0 (
    echo   [ERROR] No se pudo crear Update.jar
    pause & exit /b 1
)
echo         Update.jar - OK

:: ── Resumen ────────────────────────────────────────────────
echo.
echo   +--------------------------------------------------+
echo   ^|  BUILD SERVIDOR COMPLETADO EXITOSAMENTE         ^|
echo   +--------------------------------------------------+
echo   ^|                                                  ^|
echo   ^|  Server.jar -> SERVIDOR\dist\Server.jar          ^|
echo   ^|  Update.jar -> SERVIDOR\dist\Update.jar          ^|
echo   ^|                                                  ^|
echo   ^|  Ejecutar servidor: java -jar SERVIDOR\dist\Server.jar  ^|
echo   ^|  Ejecutar update:   java -jar SERVIDOR\dist\Update.jar  ^|
echo   ^|  Update headless:   java -jar SERVIDOR\dist\Update.jar --headless ^|
echo   ^|  Update scan once:  java -jar SERVIDOR\dist\Update.jar --once     ^|
echo   ^|                                                  ^|
echo   +--------------------------------------------------+
echo.
endlocal
if /i "%~1"=="--no-pause" exit /b 0
pause
