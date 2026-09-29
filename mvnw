#!/bin/sh
set -e
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
export MAVEN_PROJECTBASEDIR="$BASE_DIR"
JAVA_CMD="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if [ ! -x "$JAVA_CMD" ]; then
  JAVA_CMD=java
fi
exec "$JAVA_CMD" -Dmaven.multiModuleProjectDirectory="$BASE_DIR" -classpath "$BASE_DIR/.mvn/wrapper/maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain "$@"
