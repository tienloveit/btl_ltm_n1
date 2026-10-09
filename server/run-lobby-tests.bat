@echo off
setlocal
cd /d "%~dp0"
if not exist test-out mkdir test-out
powershell -NoProfile -Command "$sources = @(Get-ChildItem -LiteralPath 'src','../client/src','test' -Filter '*.java' -Recurse | ForEach-Object { $_.FullName }); & javac --release 17 -encoding UTF-8 -d test-out $sources; exit $LASTEXITCODE"
if errorlevel 1 exit /b 1
java -cp test-out tank.server.LobbyIntegrationTest
