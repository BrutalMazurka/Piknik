#!/bin/bash
echo "Starting Piknik in DEBUG mode..."
echo "Debug port: 5005"
echo ""

JAVA_OPTS="-Xms256m -Xmx512m -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

JAR_FILE=$(ls piknik-*-jar-with-dependencies.jar 2>/dev/null | head -n 1)

if [ -z "$JAR_FILE" ]; then
  echo "ERROR: Piknik JAR file not found"
  exit 1
fi

java $JAVA_OPTS -jar "$JAR_FILE"
