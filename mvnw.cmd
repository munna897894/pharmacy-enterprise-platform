@ECHO OFF
SET MAVEN_PROJECTBASEDIR=%~dp0
IF EXIST "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" (
  GOTO start
)
ECHO Maven wrapper JAR not found.
EXIT /B 1
:start
java -classpath "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain %*
