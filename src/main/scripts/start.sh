#!/bin/bash
########################################
# Piknik POS Controller - Startup Script
########################################

echo "Starting Piknik POS Controller..."
echo ""

# Set Java options
JAVA_OPTS="-Xms256m -Xmx512m"

# Check if Java is available
if ! command -v java &> /dev/null; then
  echo "ERROR: Java is not installed or not in PATH"
  echo "Please install Java 21 or higher"
  exit 1
fi

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
