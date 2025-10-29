#!/usr/bin/env sh

DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
WRAPPER_JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Gradle wrapper JAR not found at $WRAPPER_JAR" >&2
  exit 1
fi

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_CMD="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
  JAVA_CMD="$(command -v java)"
else
  echo "Java executable not found. Please install Java 17 or newer." >&2
  exit 1
fi

exec "$JAVA_CMD" -Dorg.gradle.appname=gradlew -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
