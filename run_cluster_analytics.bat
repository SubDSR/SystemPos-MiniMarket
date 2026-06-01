@echo off
set "JAVA_EXE=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
set "CP=out\dwh;lib\sqlite-jdbc.jar;lib\slf4j-api.jar;lib\slf4j-nop.jar"

"%JAVA_EXE%" -cp "%CP%" GenerarDatawareHouse SERVIDOR_DATOS\minimarket.db SERVIDOR_DWH\data\dwh.db
"%JAVA_EXE%" -cp "%CP%" CreateCrossTab
"%JAVA_EXE%" -cp "%CP%" ViewCrossTab ventas_2d
"%JAVA_EXE%" -cp "%CP%" ViewCrossTab ventas_3d
