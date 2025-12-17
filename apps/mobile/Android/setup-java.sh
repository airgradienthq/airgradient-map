#!/bin/bash

# Java/JDK Setup Script for AG-MAP Android Development

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

echo "✅ Java environment configured:"
echo "   JAVA_HOME: $JAVA_HOME"
echo "   Java version: $(java -version 2>&1 | head -1)"

# Add to current shell environment
echo "🔧 To make this permanent, add to your ~/.bashrc:"
echo "   echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' >> ~/.bashrc"
echo "   echo 'export PATH=\$JAVA_HOME/bin:\$PATH' >> ~/.bashrc"