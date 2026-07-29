@echo off
title L2J4Team GameServer Console

:: Limpa possivel processo Java orfao de sessoes anteriores
for /f "skip=1" %%p in ('wmic process where "name='java.exe' and commandline like '%%net.sf.l2j.gameserver.GameServer%%'" get processid 2^>nul') do (
    if %%p neq 0 taskkill /f /pid %%p >nul 2>&1
)

:start
echo Starting L2J4Team Game Server.
echo.
REM -------------------------------------
REM Default parameters for a basic server (Java 25 / G1GC).
java -Xms1500m -Xmx2g -server -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ParallelRefProcEnabled -XX:+UseStringDeduplication -cp "%~dp0lib\*;%~dp0l2jserver.jar" net.sf.l2j.gameserver.GameServer
set "EXIT_CODE=%ERRORLEVEL%"
REM
REM If you have a big server and lots of memory, you could experiment for example with
REM java -server -Xms1536m -Xmx1536m -XX:+UseG1GC -XX:MaxGCPauseMillis=150 -XX:G1HeapRegionSize=8m -XX:+ParallelRefProcEnabled -XX:+UseStringDeduplication -XX:+AlwaysPreTouch -cp "%~dp0lib\*;%~dp0l2jserver.jar" net.sf.l2j.gameserver.GameServer
REM -------------------------------------

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