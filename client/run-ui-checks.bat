@echo off
setlocal
cd /d "%~dp0.."
if not exist client\out mkdir client\out
powershell -NoProfile -Command "$sources = @(Get-ChildItem -LiteralPath 'client/src','client/test' -Filter '*.java' -Recurse | ForEach-Object { $_.FullName }); & javac --release 17 -encoding UTF-8 -d client/out $sources; exit $LASTEXITCODE"
if errorlevel 1 exit /b 1
java -cp client/out tank.client.ui.LobbyUiCheck
