#!/bin/bash
echo "Starting Piknik in DEBUG mode..."
echo "Debug port: 5005"
echo ""

# JavaPOS native libraries location
JAVAPOS_BIN="/opt/EpsonJavaPOS/bin"

# Set library paths
if [ -d "$JAVAPOS_BIN" ]; then
  export LD_LIBRARY_PATH="$JAVAPOS_BIN:$LD_LIBRARY_PATH"
  echo "Native libraries path: $JAVAPOS_BIN"
fi

# Java options with debug enabled
JAVA_OPTS="-Xms256m -Xmx512m"
JAVA_OPTS="$JAVA_OPTS -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
JAVA_OPTS="$JAVA_OPTS -Djava.library.path=$JAVAPOS_BIN"

JAR_FILE=$(ls piknik-*-jar-with-dependencies.jar 2>/dev/null | head -n 1)

if [ -z "$JAR_FILE" ]; then
  echo "ERROR: Piknik JAR file not found"
  exit 1
fi

echo "Listening for debugger on port 5005..."
echo ""

java $JAVA_OPTS -jar "$JAR_FILE"
