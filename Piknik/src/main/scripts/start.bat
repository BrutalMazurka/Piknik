@echo off
setlocal enabledelayedexpansion
REM ========================================
REM Piknik POS Controller - Startup Script
REM ========================================

echo.
echo Starting Piknik POS Controller...
echo.

REM Get the directory where this script is located
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

REM Find application JAR
for %%f in (piknik-*-deps.jar) do set APP_JAR=%%f

if "%APP_JAR%"=="" (
    echo ERROR: Application JAR not found
    pause
    exit /b 1
)

echo Application JAR: %APP_JAR%
echo.

REM Build classpath - INCLUDE CONFIG DIRECTORY FOR LOG4J2
set CLASSPATH=%APP_JAR%;config

echo.

REM JRE binaries location
set JAVA_BIN=%SCRIPT_DIR%jre\bin\java

REM Set Java memory limits
set JAVA_OPTS=-Xms256m -Xmx512m

REM Set Log4J2 configuration file (log4j2.xml) location
set LOG4J2_CONFIG=%SCRIPT_DIR%config\log4j2.xml
set JAVA_OPTS=%JAVA_OPTS% -Dlog4j2.configurationFile=%LOG4J2_CONFIG%

REM Set native library path for jSerialComm
set NATIVE_LIB_DIR=%SCRIPT_DIR%res\bin
if exist "%NATIVE_LIB_DIR%" (
    set JAVA_OPTS=%JAVA_OPTS% -Djava.library.path="%NATIVE_LIB_DIR%"
    echo Native libraries: %NATIVE_LIB_DIR%
)

echo Log4J2 config: %LOG4J2_CONFIG%
echo HTML resources: %SCRIPT_DIR%res\html
echo.

REM Check if Java is available
%JAVA_BIN% -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java is not installed or not in PATH
    echo Please install Java 21 or higher
    pause
    exit /b 1
)

echo Starting with configuration: config\application.properties
echo.

REM Start the application using external classpath
REM Use -cp and specify main class, NOT -jar
"%JAVA_BIN%" %JAVA_OPTS% -cp "%CLASSPATH%" pik.Piknik

pause
