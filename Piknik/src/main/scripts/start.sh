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

# Add all JavaPOS JARs from res/lib/
if [ -d "res/lib" ]; then
  echo "Adding external JavaPOS libraries..."
  for jar in res/lib/*.jar; do
    if [ -f "$jar" ]; then
      CLASSPATH="$CLASSPATH:$jar"
      echo "  + $(basename $jar)"
    fi
  done
else
  echo "ERROR: res/lib/ directory not found"
  exit 1
fi

echo ""

# JavaPOS native libraries
JAVAPOS_BIN="$SCRIPT_DIR/res/bin"
if [ -d "$JAVAPOS_BIN" ]; then
  export LD_LIBRARY_PATH="$JAVAPOS_BIN:$LD_LIBRARY_PATH"
  echo "Native libraries: $JAVAPOS_BIN"
else
  echo "⚠ Warning: Native libraries not found at $JAVAPOS_BIN"
fi

# JavaPOS config location (absolute path)
JAVAPOS_CONFIG="$SCRIPT_DIR/config/jpos.xml"

# Log4J2 config location (absolute path)
LOG4J2_CONFIG="$SCRIPT_DIR/config/log4j2.xml"

# Set Java options
JAVA_OPTS="-Xms256m -Xmx512m"
# CRITICAL: Pass file path WITHOUT file:// prefix
JAVA_OPTS="$JAVA_OPTS -Djpos.config.populatorFile=$JAVAPOS_CONFIG"
# Specify the populator class explicitly
JAVA_OPTS="$JAVA_OPTS -Djpos.config.populator.class=jpos.config.simple.xml.SimpleXmlRegPopulator"
JAVA_OPTS="$JAVA_OPTS -Djava.library.path=$JAVAPOS_BIN"
# Set Log4J2 configuration file location
JAVA_OPTS="$JAVA_OPTS -Dlog4j2.configurationFile=$LOG4J2_CONFIG"

echo "JavaPOS config: $JAVAPOS_CONFIG"
echo "Log4J2 config: $LOG4J2_CONFIG"
echo "HTML resources: $SCRIPT_DIR/res/html"
echo "Main class: pik.Piknik"
echo ""

# Start application
# CRITICAL: Use -cp and specify main class, NOT -jar
$JAVA_BIN $JAVA_OPTS -cp "$CLASSPATH" pik.Piknik

echo ""
echo "Application stopped"
