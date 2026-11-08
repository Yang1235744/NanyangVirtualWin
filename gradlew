#!/bin/sh
#
# Gradle start up script for UN*X
#
# ----- NOTE -----
# This is a stub. Run once via Android Studio or manually downloaded Gradle to
# generate a full wrapper:
#   gradle wrapper --gradle-version 8.5
# Then copy the generated gradlew, gradlew.bat and gradle/wrapper/gradle-wrapper.jar
# into this project.
# ----------------

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")
APP_HOME=$(cd "$(dirname "$0")" && pwd)

DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

JAVA_HOME=${JAVA_HOME:-$(command -v java 2>/dev/null || echo /usr/lib/jvm/default-java)}
JAVA="$JAVA_HOME/bin/java"

if [ ! -x "$JAVA" ]; then
    echo "Error: JAVA_HOME not found or java binary missing." >&2
    exit 1
fi

WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Error: $WRAPPER_JAR not found." >&2
    echo "Run: gradle wrapper --gradle-version 8.5" >&2
    exit 1
fi

exec "$JAVA" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    -classpath "$WRAPPER_JAR" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
