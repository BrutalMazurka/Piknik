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
APP_JAR=$(ls piknik_*_deps.jar 2>/dev/null | head -n 1)

if [ -z "$APP_JAR" ]; then
  echo "ERROR: Application JAR not found"
  exit 1
fi

echo "Application JAR: $APP_JAR"

# JRE binaries location
JAVA_BIN=$SCRIPT_DIR/jre/bin/java

# Build classpath
CLASSPATH="$APP_JAR"

# Add all JavaPOS JARs from resources/lib/
if [ -d "resources/lib" ]; then
  for jar in resources/lib/*.jar; do
    if [ -f "$jar" ]; then
      CLASSPATH="$CLASSPATH:$jar"
      echo "  + $(basename $jar)"
    fi
  done
else
  echo "ERROR: resources/lib/ directory not found"
  exit 1
fi

echo ""

# JavaPOS native libraries
JAVAPOS_BIN="$SCRIPT_DIR/resources/bin"
if [ -d "$JAVAPOS_BIN" ]; then
  export LD_LIBRARY_PATH="$JAVAPOS_BIN:$LD_LIBRARY_PATH"
  echo "Native libraries: $JAVAPOS_BIN"
else
  echo "⚠ Warning: Native libraries not found at $JAVAPOS_BIN"
fi

# JavaPOS config location
JAVAPOS_CONFIG="$SCRIPT_DIR/config/jpos.xml"

# Set Java options
JAVA_OPTS="-Xms256m -Xmx512m"
JAVA_OPTS="$JAVA_OPTS -Djpos.config.populatorFile=file://$JAVAPOS_CONFIG"
JAVA_OPTS="$JAVA_OPTS -Djava.library.path=$JAVAPOS_BIN"

echo "JavaPOS config: $JAVAPOS_CONFIG"
echo "HTML resources: $SCRIPT_DIR/resources/html"
echo "Main class: pik.Piknik"
echo ""

# Start application
# CRITICAL: Use -cp and specify main class, NOT -jar
$JAVA_BIN $JAVA_OPTS -cp "$CLASSPATH" pik.Piknik

echo ""
echo "Application stopped"
