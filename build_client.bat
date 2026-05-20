@echo off
chcp 65001 >nul 2>&1
setlocal EnableDelayedExpansion

:: ============================================================
::  MiniMarket POS — Build Cliente
::  Genera: POSClient.jar  y  Send.jar
::  Requisito: JDK 17+  (sin Maven, puro javac + jar)
:: ============================================================

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

set "APP_SRC=%ROOT%\APPLICATION\src\main\java"
set "APP_BUILD=%ROOT%\APPLICATION\build\classes"
set "APP_TMP=%ROOT%\APPLICATION\build\tmp"
set "APP_DIST=%ROOT%\APPLICATION\dist"

echo.
echo   +--------------------------------------------------+
echo   ^|   MiniMarket POS  ^|  Compilacion Cliente        ^|
echo   ^|   POSClient.jar  +  Send.jar                    ^|
echo   +--------------------------------------------------+
echo.

:: ── Verificar JDK ──────────────────────────────────────────
where javac >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo   [ERROR] javac no encontrado.
    echo   Instale JDK 17+ y agregue JAVA_HOME\bin al PATH.
    echo   Descarga: https://adoptium.net
    pause & exit /b 1
)
where jar >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo   [ERROR] jar no encontrado. Verifique JDK 17+ en PATH.
    pause & exit /b 1
)

for /f "tokens=3" %%v in ('javac -version 2^>^&1') do (
    echo   JDK detectado: %%v
)
echo   Fuentes:  %APP_SRC%
echo   Salida:   %APP_DIST%
echo.

:: ── Paso 1: Limpiar y crear directorios ────────────────────
echo   [1/4] Preparando directorios de build...
if exist "%APP_BUILD%" rmdir /s /q "%APP_BUILD%"
if exist "%APP_TMP%"   rmdir /s /q "%APP_TMP%"
if exist "%APP_DIST%"  rmdir /s /q "%APP_DIST%"
mkdir "%APP_BUILD%" 2>nul
mkdir "%APP_TMP%"   2>nul
mkdir "%APP_DIST%"  2>nul
echo         OK

:: Crear directorios de runtime si no existen
if not exist "%ROOT%\APPLICATION\logs"    mkdir "%ROOT%\APPLICATION\logs"
if not exist "%ROOT%\APPLICATION\exports" mkdir "%ROOT%\APPLICATION\exports"
if not exist "%ROOT%\DATA"               mkdir "%ROOT%\DATA"
if not exist "%ROOT%\DATOS"              mkdir "%ROOT%\DATOS"

:: ── Paso 2: Recopilar fuentes .java (con PowerShell → forward slashes, sin BOM) ──
echo   [2/4] Recopilando fuentes Java...
powershell -NoProfile -Command "$src='%APP_SRC%'; $out='%APP_TMP%\sources.txt'; $files = Get-ChildItem -Path $src -Filter '*.java' -Recurse | ForEach-Object { $_.FullName.Replace('\','/') }; if (-not $files) { exit 1 }; [System.IO.File]::WriteAllLines($out, $files, [System.Text.Encoding]::ASCII); Write-Host ('  ' + $files.Count + ' archivos encontrados')"
if %ERRORLEVEL% neq 0 (
    echo   [ERROR] No se encontraron fuentes en: %APP_SRC%
    pause & exit /b 1
)
echo         OK - lista de fuentes generada

:: ── Paso 3: Compilar con javac ─────────────────────────────
echo   [3/4] Compilando con javac --release 17...
javac --release 17 -encoding UTF-8 -d "%APP_BUILD%" "@%APP_TMP%/sources.txt"
if %ERRORLEVEL% neq 0 (
    echo.
    echo   [ERROR] Compilacion fallida.
    echo   Revise los errores de javac arriba.
    pause & exit /b 1
)
echo         OK - compilacion exitosa

:: ── Paso 4: Empaquetar JARs ────────────────────────────────
echo   [4/4] Empaquetando JARs ejecutables...

:: ── Crear manifests con PowerShell (sin BOM, encoding ASCII correcto) ──────
powershell -NoProfile -Command "[System.IO.File]::WriteAllText('%APP_TMP%\mf_pos.mf',  'Main-Class: com.minimarket.MainApp`r`n`r`n',  [System.Text.Encoding]::ASCII)"
powershell -NoProfile -Command "[System.IO.File]::WriteAllText('%APP_TMP%\mf_send.mf', 'Main-Class: com.minimarket.send.Send`r`n`r`n', [System.Text.Encoding]::ASCII)"

:: ── POSClient.jar ──────────────────────────────────────────
jar cfm "%APP_DIST%\POSClient.jar" "%APP_TMP%\mf_pos.mf" -C "%APP_BUILD%" .
if %ERRORLEVEL% neq 0 (
    echo   [ERROR] No se pudo crear POSClient.jar
    pause & exit /b 1
)
echo         POSClient.jar - OK

:: ── Send.jar ───────────────────────────────────────────────
jar cfm "%APP_DIST%\Send.jar" "%APP_TMP%\mf_send.mf" -C "%APP_BUILD%" .
if %ERRORLEVEL% neq 0 (
    echo   [ERROR] No se pudo crear Send.jar
    pause & exit /b 1
)
echo         Send.jar      - OK

:: ── Resumen ────────────────────────────────────────────────
echo.
echo   +--------------------------------------------------+
echo   ^|  BUILD CLIENTE COMPLETADO EXITOSAMENTE          ^|
echo   +--------------------------------------------------+
echo   ^|                                                  ^|
echo   ^|  POSClient.jar -> APPLICATION\dist\POSClient.jar ^|
echo   ^|  Send.jar      -> APPLICATION\dist\Send.jar      ^|
echo   ^|                                                  ^|
echo   ^|  Ejecutar POS:   java -jar APPLICATION\dist\POSClient.jar ^|
echo   ^|  Ejecutar Send:  java -jar APPLICATION\dist\Send.jar      ^|
echo   ^|                                                  ^|
echo   +--------------------------------------------------+
echo.
endlocal
if /i "%~1"=="--no-pause" exit /b 0
pause
