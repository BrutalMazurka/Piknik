#!/bin/bash
########################################
# Piknik POS Controller - Startup Script
########################################

echo "Starting Piknik POS Controller..."
echo ""

# JavaPOS native libraries location
JAVAPOS_BIN="./resources/bin"

# Check and set native library path
if [ -d "$JAVAPOS_BIN" ]; then
  export LD_LIBRARY_PATH="$JAVAPOS_BIN:$LD_LIBRARY_PATH"
  echo "Native libraries path: $JAVAPOS_BIN"
else
  echo "⚠ Warning: JavaPOS native libraries not found at $JAVAPOS_BIN"
  echo "Printer functionality may not work properly"
fi

# Java options
JAVA_OPTS="-Xms256m -Xmx512m"
# Set JavaPOS configuration file (jpos.xml) location
JAVA_OPTS="$JAVA_OPTS -Djpos.config.populatorFile=file:./config/jpos.xml"
# Set java.library.path for JNI
JAVA_OPTS="$JAVA_OPTS -Djava.library.path=$JAVAPOS_BIN"

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
java $JAVA_OPTS -jar "$JAR_FILE"

echo ""
echo "Application stopped"
