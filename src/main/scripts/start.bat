@echo off
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
for %%f in (piknik_*_jar_deps.jar) do set APP_JAR=%%f

if "%APP_JAR%"=="" (
    echo ERROR: Application JAR not found
    pause
    exit /b 1
)

echo Application JAR: %APP_JAR%
echo.

REM Build classpath
set CLASSPATH=%APP_JAR%

REM Add all JARs from resources\lib\
if exist "resources\lib" (
    echo Adding external JavaPOS libraries...
    for %%j in (resources\lib\*.jar) do (
        set CLASSPATH=!CLASSPATH!;%%j
        echo   + %%~nxj
    )
) else (
    echo ERROR: resources\lib\ directory not found
    pause
    exit /b 1
)

echo.

REM JavaPOS native libraries location
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

REM Set JavaPOS configuration file (jpos.xml) location
set JAVAPOS_CONFIG=%SCRIPT_DIR%config\jpos.xml
set JAVA_OPTS=%JAVA_OPTS% -Djpos.config.populatorFile=file:///%JAVAPOS_CONFIG:\=/%

echo JavaPOS config: %JPOS_CONFIG%
echo HTML resources: %SCRIPT_DIR%resources\html
echo Native libraries: %JAVAPOS_BIN%
echo.

REM Check if Java is available
%JAVA_BIN% -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java is not installed or not in PATH
    echo Please install Java 21 or higher
    pause
    exit /b 1
)

echo Starting with JAR: %JAR_FILE%
echo Configuration: config\application.properties
echo.

REM Start the application using external classpath
REM CRITICAL: Use -cp and specify main class, NOT -jar
java %JAVA_OPTS% -cp "%CLASSPATH%" pik.Piknik

pause
