#!/bin/bash
##########################################
# Piknik POS Controller - Startup Script #
##########################################

echo ""
echo "Starting Piknik POS Controller..."
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

echo "Log4J2 config: $LOG4J2_CONFIG"
echo "HTML resources: $SCRIPT_DIR/res/html"
echo "Main class: pik.Piknik"
echo ""

# Start application
# Use -cp and specify main class, NOT -jar
$JAVA_BIN $JAVA_OPTS -cp "$CLASSPATH" pik.Piknik

echo ""
echo "Application stopped"
