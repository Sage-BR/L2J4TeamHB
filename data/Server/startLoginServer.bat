@echo off
title L2J4Team LoginServer Console
:start
echo Starting L2J4Team Login Server.
echo.
java -Xmx64m -XX:+UseG1GC -XX:+TieredCompilation -XX:+UseCompressedOops -XX:SurvivorRatio=8 -XX:NewRatio=4 -cp "%~dp0lib\*;%~dp0l2jserver.jar" net.sf.l2j.loginserver.L2LoginServer
if ERRORLEVEL 2 goto restart
if ERRORLEVEL 1 goto error
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
