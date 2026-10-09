@echo off
setlocal
cd /d "%~dp0"
if not exist lib\mysql-connector-j-9.4.0.jar (
	if not exist lib mkdir lib
	if exist "%USERPROFILE%\.m2\repository\com\mysql\mysql-connector-j\9.4.0\mysql-connector-j-9.4.0.jar" (
		copy /Y "%USERPROFILE%\.m2\repository\com\mysql\mysql-connector-j\9.4.0\mysql-connector-j-9.4.0.jar" "lib\mysql-connector-j-9.4.0.jar" >nul
	)
)
if not exist lib\mysql-connector-j-9.4.0.jar (
	echo Downloading MySQL Connector/J 9.4.0...
	powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference = 'Stop'; Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar' -OutFile 'lib\mysql-connector-j-9.4.0.jar'"
	if errorlevel 1 exit /b 1
)
if not exist out mkdir out
powershell -NoProfile -Command "$sources = @(Get-ChildItem -LiteralPath 'src' -Filter '*.java' -Recurse | ForEach-Object { $_.FullName }); & javac --release 17 -encoding UTF-8 -cp 'lib/*' -d out $sources; exit $LASTEXITCODE"
if errorlevel 1 exit /b 1
java -cp "out;lib\*" tank.server.app.ServerMain %*
