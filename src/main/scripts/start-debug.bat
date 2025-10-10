@echo off
echo Starting Piknik in DEBUG mode...
echo Debug port: 5005
echo.

set JAVA_OPTS=-Xms256m -Xmx512m -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005

for %%f in (piknik-*-jar-with-dependencies.jar) do set JAR_FILE=%%f

if "%JAR_FILE%"=="" (
    echo ERROR: Piknik JAR file not found
    pause
    exit /b 1
)

java %JAVA_OPTS% -jar %JAR_FILE%

pause
