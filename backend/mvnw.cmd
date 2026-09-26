@REM ----------------------------------------------------------------------------
@REM Maven Start Up Batch script
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set MAVEN_PROJECTBASEDIR=%DIRNAME%

set MAVEN_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
set MAVEN_DIR=%DIRNAME%\.mvn\apache-maven-3.9.6

if exist "%MAVEN_DIR%\bin\mvn.cmd" goto run

echo Downloading Maven 3.9.6...
powershell -Command "New-Item -ItemType Directory -Force -Path '%MAVEN_DIR%' | Out-Null; $zip = '%DIRNAME%\.mvn\maven.zip'; Invoke-WebRequest -Uri '%MAVEN_URL%' -OutFile $zip; Expand-Archive -Path $zip -DestinationPath '%DIRNAME%\.mvn' -Force; Remove-Item $zip"

:run
"%MAVEN_DIR%\bin\mvn.cmd" %*
