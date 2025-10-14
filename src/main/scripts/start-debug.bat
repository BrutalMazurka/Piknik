@echo off
echo Starting Piknik in DEBUG mode...
echo Debug port: 5005
echo.

set JAVAPOS_BIN=C:\Program Files\Epson\JavaPOS\bin

set JAVA_OPTS=-Xms256m -Xmx512m
set JAVA_OPTS=%JAVA_OPTS% -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
set JAVA_OPTS=%JAVA_OPTS% -Djava.library.path="%JAVAPOS_BIN%"

for %%f in (piknik-*-jar-with-dependencies.jar) do set JAR_FILE=%%f

if "%JAR_FILE%"=="" (
    echo ERROR: Piknik JAR file not found
    pause
    exit /b 1
)

echo Listening for debugger on port 5005...
echo.

java %JAVA_OPTS% -jar %JAR_FILE%

pause
