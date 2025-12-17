#!/bin/bash
echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
echo "Starting Piknik in DEBUG mode..."
echo "Debug port: 5005"
echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
echo ""

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Find application JAR (with dependencies)
APP_JAR=$(ls piknik-*-deps.jar 2>/dev/null | head -n 1)

if [ -z "$APP_JAR" ]; then
  echo "ERROR: Application JAR not found"
  exit 1
fi

echo "Application JAR: $APP_JAR"

# JRE binaries location
JAVA_BIN=$SCRIPT_DIR/jre/bin/java

# Build classpath - INCLUDE CONFIG DIRECTORY FOR LOG4J2
CLASSPATH="$APP_JAR:config"

echo ""

# Log4J2 config location (absolute path)
LOG4J2_CONFIG="$SCRIPT_DIR/config/log4j2.xml"

# Set Java options
JAVA_OPTS="-Xms256m -Xmx512m"
# Set Log4J2 configuration file location
JAVA_OPTS="$JAVA_OPTS -Dlog4j2.configurationFile=$LOG4J2_CONFIG"

# Set native library path for jSerialComm
NATIVE_LIB_DIR="$SCRIPT_DIR/res/bin"
if [ -d "$NATIVE_LIB_DIR" ]; then
  export LD_LIBRARY_PATH="$NATIVE_LIB_DIR:$LD_LIBRARY_PATH"
  JAVA_OPTS="$JAVA_OPTS -Djava.library.path=$NATIVE_LIB_DIR"
  echo "Native libraries: $NATIVE_LIB_DIR"
fi

# Remote debugging connection
JAVA_OPTS="$JAVA_OPTS -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

echo "Log4J2 config: $LOG4J2_CONFIG"
echo "HTML resources: $SCRIPT_DIR/res/html"
echo "Main class: pik.Piknik"
echo "Remote debug port: 5005"
echo ""

# Start application
# Use -cp and specify main class, NOT -jar
$JAVA_BIN $JAVA_OPTS -cp "$CLASSPATH" pik.Piknik

echo ""
echo "Application stopped"
