@echo off
title L2J4Team LoginServer Console
:start
echo Starting L2J4Team Login Server.
echo.
start "L2J4Team LoginServer" /wait java -Xms32m -Xmx64m -server -XX:+UseSerialGC -cp "%~dp0lib\*;%~dp0l2jserver.jar" net.sf.l2j.loginserver.L2LoginServer
set "EXIT_CODE=%ERRORLEVEL%"

:: Limpa possivel processo Java orfao
taskkill /f /fi "WINDOWTITLE eq L2J4Team LoginServer" /im java.exe >nul 2>&1

if %EXIT_CODE%==2 goto restart
if %EXIT_CODE%==1 goto error
goto end
:restart
echo.
echo Admin Restart ...
echo.
goto start
:error
echo.
echo Server terminated abnormaly
echo.
:end
echo.
echo server terminated
echo.
pause
