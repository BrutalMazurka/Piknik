@echo off
setlocal enabledelayedexpansion
echo !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
echo Starting Piknik in DEBUG mode...
echo Debug port: 5005
echo !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
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

REM Build classpath - INCLUDE CONFIG DIRECTORY FOR LOGBACK
set CLASSPATH=%APP_JAR%;config

REM Add all JARs from res\lib\
if exist "res\lib" (
    echo Adding external JavaPOS libraries...
    for %%j in (res\lib\*.jar) do (
        set CLASSPATH=!CLASSPATH!;%%j
        echo   + %%~nxj
    )
) else (
    echo ERROR: res\lib\ directory not found
    pause
    exit /b 1
)

echo.

REM JavaPOS native libraries location
set JAVAPOS_BIN=%SCRIPT_DIR%res\bin

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

REM Set JavaPOS configuration file (jpos.xml) location
set JAVAPOS_CONFIG=%SCRIPT_DIR%config\jpos.xml
REM CRITICAL: Pass file path WITHOUT file:// prefix
set JAVA_OPTS=%JAVA_OPTS% -Djpos.config.populatorFile=%JAVAPOS_CONFIG%
REM Specify the populator class explicitly
set JAVA_OPTS=%JAVA_OPTS% -Djpos.config.populator.class=jpos.config.simple.xml.SimpleXmlRegPopulator

REM Remote debugging connection
set JAVA_OPTS=%JAVA_OPTS% -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005

REM JavaPOS debugging options
set JAVA_OPTS=%JAVA_OPTS% -Djpos.tracing=ON
set JAVA_OPTS=%JAVA_OPTS% -Djpos.traceLevel=4
set JAVA_OPTS=%JAVA_OPTS% -Djpos.config.populatorFile.debug=true

echo JavaPOS config: %JAVAPOS_CONFIG%
echo HTML resources: %SCRIPT_DIR%res\html
echo Native libraries: %JAVAPOS_BIN%
echo Remote debug port: 5005
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
REM CRITICAL: Use -cp and specify main class, NOT -jar
"%JAVA_BIN%" %JAVA_OPTS% -cp "%CLASSPATH%" pik.Piknik

pause
