@ECHO OFF
SETLOCAL
SET DIR=%~dp0
SET WRAPPER_JAR=%DIR%\gradle\wrapper\gradle-wrapper.jar

IF NOT EXIST "%WRAPPER_JAR%" (
  ECHO Gradle wrapper JAR not found at %WRAPPER_JAR%
  EXIT /B 1
)

IF DEFINED JAVA_HOME (
  SET "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
) ELSE (
  WHERE java >NUL 2>NUL
  IF %ERRORLEVEL% EQU 0 (
    FOR /F "delims=" %%G IN ('WHERE java') DO (
      SET "JAVA_CMD=%%G"
      GOTO run
    )
  )
  ECHO Java executable not found. Please install Java 17 or newer.
  EXIT /B 1
)

:run
"%JAVA_CMD%" -Dorg.gradle.appname=gradlew -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
ENDLOCAL
