@echo off
echo Starting Piknik in DEBUG mode...
echo Debug port: 5005
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
set JAVA_OPTS=%JAVA_OPTS% -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
set JAVA_OPTS=%JAVA_OPTS% -Djava.library.path="%JAVAPOS_BIN%"

REM Set java.library.path for JNI
set JAVA_OPTS=%JAVA_OPTS% -Djava.library.path="%JAVAPOS_BIN%"

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
echo Listening for debugger on port 5005...
echo.

java %JAVA_OPTS% -jar %JAR_FILE%

pause
