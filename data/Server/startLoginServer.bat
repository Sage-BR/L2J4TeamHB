@echo off
title L2J4Team LoginServer Console

:: Limpa possivel processo Java orfao de sessoes anteriores
for /f "skip=1" %%p in ('wmic process where "name='java.exe' and commandline like '%%net.sf.l2j.loginserver.L2LoginServer%%'" get processid 2^>nul') do (
    if %%p neq 0 taskkill /f /pid %%p >nul 2>&1
)

:start
echo Starting L2J4Team Login Server.
echo.
java -Xms32m -Xmx64m -server -XX:+UseSerialGC -cp "%~dp0lib\*;%~dp0l2jserver.jar" net.sf.l2j.loginserver.L2LoginServer
set "EXIT_CODE=%ERRORLEVEL%"

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
