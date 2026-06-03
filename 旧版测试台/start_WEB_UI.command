#!/bin/bash

cd "$(dirname "$0")" || exit 1

PORT="${1:-8080}"

if ! command -v javac >/dev/null 2>&1; then
    if [ -x /usr/libexec/java_home ]; then
        JAVA_HOME_FOUND=$(/usr/libexec/java_home -v 17 2>/dev/null)
        if [ -n "$JAVA_HOME_FOUND" ] && [ -x "$JAVA_HOME_FOUND/bin/javac" ]; then
            export JAVA_HOME="$JAVA_HOME_FOUND"
            export PATH="$JAVA_HOME/bin:$PATH"
        fi
    fi
fi

if ! command -v javac >/dev/null 2>&1; then
    echo "JDK 17 or newer is required."
    echo "Install a JDK, then run this file again."
    read -r -p "Press Enter to close this window..."
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    echo "The java command was not found."
    echo "Install a JDK, then run this file again."
    read -r -p "Press Enter to close this window..."
    exit 1
fi

mkdir -p out

echo "Compiling Java Warrior Arena..."
javac --release 17 -encoding UTF-8 -d out src/*.java
if [ $? -ne 0 ]; then
    echo
    echo "Compile failed. Fix the Java errors above, then run this file again."
    read -r -p "Press Enter to close this window..."
    exit 1
fi

URL="http://localhost:${PORT}"
echo "Starting Web debug UI..."
echo "$URL"

if command -v open >/dev/null 2>&1; then
    open "$URL"
fi

java -cp out WebDebugMain "$PORT"
