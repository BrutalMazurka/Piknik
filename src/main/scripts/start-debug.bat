@echo off
echo !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
echo Starting Piknik in DEBUG mode...
echo Debug port: 5005
echo !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
echo.

REM Get the directory where this script is located
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

REM JavaPOS native libraries location
REM set JAVAPOS_BIN=C:\Program Files\Epson\JavaPOS\bin
set JAVAPOS_BIN=%SCRIPT_DIR%resources\bin

REM Check if native libraries exist
if not exist "%JAVAPOS_BIN%" (
    echo Warning: JavaPOS native libraries not found at %JAVAPOS_BIN%
    echo Printer functionality may not work properly
)

REM JRE binaries location
set JAVA_BIN=%SCRIPT_DIR%jre\bin\java

REM Set Java memory limits
set JAVA_OPTS=-Xms256m -Xmx512m

REM Set java.library.path for JNI
set JAVA_OPTS=%JAVA_OPTS% -Djava.library.path="%JAVAPOS_BIN%"

REM Set JavaPOS configuration file (jpos.xml) location - USE ABSOLUTE PATH
REM set JAVA_OPTS=%JAVA_OPTS% -Djpos.config.populatorFile=file:./config/jpos.xml
set JPOS_CONFIG=%SCRIPT_DIR%config\jpos.xml
set JAVA_OPTS=%JAVA_OPTS% -Djpos.config.populatorFile=file:///%JPOS_CONFIG:\=/%

echo JavaPOS config: %JPOS_CONFIG%

REM Connection transport for remote debugging
set JAVA_OPTS=%JAVA_OPTS% -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
REM JavaPOS debugging option
set JAVA_OPTS=%AVA_OPTS% -Djpos.tracing=ON
set JAVA_OPTS=%JAVA_OPTS% -Djpos.traceLevel=4

REM Check if Java is available
%JAVA_BIN% -version >nul 2>&1
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
%JAVA_BIN% %JAVA_OPTS% -jar %JAR_FILE%

pause
