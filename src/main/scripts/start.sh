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

# JavaPOS native libraries location
JAVAPOS_BIN="$SCRIPT_DIR/resources/bin"

# Check and set native library path
if [ -d "$JAVAPOS_BIN" ]; then
  export LD_LIBRARY_PATH="$JAVAPOS_BIN:$LD_LIBRARY_PATH"
  echo "Native libraries path: $JAVAPOS_BIN"
else
  echo "⚠ Warning: JavaPOS native libraries not found at $JAVAPOS_BIN"
  echo "Printer functionality may not work properly"
fi

# JRE binaries location
JAVA_BIN=$SCRIPT_DIR/jre/bin/java

# Set Java memory limits
JAVA_OPTS="-Xms256m -Xmx512m"

# Set java.library.path for JNI
JAVA_OPTS="$JAVA_OPTS -Djava.library.path=$JAVAPOS_BIN"

# Set JavaPOS configuration file (jpos.xml) location
#JAVA_OPTS="$JAVA_OPTS -Djpos.config.populatorFile=file:./config/jpos.xml"
JPOS_CONFIG="$SCRIPT_DIR/config/jpos.xml"
JAVA_OPTS="$JAVA_OPTS -Djpos.config.populatorFile=file://$JPOS_CONFIG"

echo "JavaPOS config: $JPOS_CONFIG"

# Find the JAR file
JAR_FILE=$(ls piknik-*-jar-with-dependencies.jar 2>/dev/null | head -n 1)

if [ -z "$JAR_FILE" ]; then
  echo "ERROR: Piknik JAR file not found"
  exit 1
fi

echo "Starting with JAR: $JAR_FILE"
echo "Configuration: config/application.properties"
echo ""

# Start the application
$JAVA_BIN $JAVA_OPTS -jar "$JAR_FILE"

echo ""
echo "Application stopped"
