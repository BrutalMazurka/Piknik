@echo off
REM ========================================
REM Piknik POS Controller - Startup Script
REM ========================================

echo Starting Piknik POS Controller...
echo.

REM JavaPOS native libraries location
REM set JAVAPOS_BIN=C:\Program Files\Epson\JavaPOS\bin
set JAVAPOS_BIN=resources\bin

REM Check if native libraries exist
if not exist "%JAVAPOS_BIN%" (
    echo Warning: JavaPOS native libraries not found at %JAVAPOS_BIN%
    echo Printer functionality may not work properly
)

REM Set Java options
set JAVA_OPTS=-Xms256m -Xmx512m
REM Set JavaPOS configuration file (jpos.xml) location
JAVA_OPTS="$JAVA_OPTS -Djpos.config.populatorFile=file:config\jpos.xml"
REM Set java.library.path for JNI
set JAVA_OPTS=%JAVA_OPTS% -Djava.library.path="%JAVAPOS_BIN%"

REM Check if Java is available
java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java is not installed or not in PATH
    echo Please install Java 21 or higher
    pause
    exit /b 1
)

REM Find the JAR file
for %%f in (piknik-*-jar-with-dependencies.jar) do set JAR_FILE=%%f

if "%JAR_FILE%"=="" (
    echo ERROR: Piknik JAR file not found
    pause
    exit /b 1
)

echo Starting with JAR: %JAR_FILE%
echo Configuration: config\application.properties
echo.

REM Start the application
java %JAVA_OPTS% -jar %JAR_FILE%

pause
