@echo off
setlocal
set GRADLE_VERSION=8.10.2
set DIST_DIR=%USERPROFILE%\.gradle\wrapper\dists\gradle-%GRADLE_VERSION%-bin
set WRAPPER_JAR=%~dp0gradle\wrapper\gradle-wrapper.jar

if not exist "%WRAPPER_JAR%" (
  echo ERROR: gradle-wrapper.jar not found. Re-download from GitHub.
  exit /b 1
)

java -Xmx64m -Xms64m -Dfile.encoding=UTF-8 -classpath "%WRAPPER_JAR%" ^
  org.gradle.wrapper.GradleWrapperMain %*
endlocal