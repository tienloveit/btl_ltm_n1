@echo off
setlocal
cd /d "%~dp0"
if not exist out mkdir out
powershell -NoProfile -Command "$sources = @(Get-ChildItem -LiteralPath 'src' -Filter '*.java' -Recurse | ForEach-Object { $_.FullName }); & javac --release 17 -encoding UTF-8 -d out $sources; exit $LASTEXITCODE"
if errorlevel 1 exit /b 1
java -cp out tank.client.app.ClientApp %*
