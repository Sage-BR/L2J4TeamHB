@echo off
title L2J4Team GameServer Console
:start
echo Starting L2J4Team Game Server.
echo.
REM -------------------------------------
REM Default parameters for a basic server (Java 25 / G1GC).
java -Xms512m -Xmx2g -server -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ParallelRefProcEnabled -XX:+UseStringDeduplication -cp "%~dp0lib\*;%~dp0l2jserver.jar" net.sf.l2j.gameserver.GameServer
REM
REM If you have a big server and lots of memory, you could experiment for example with
REM java -server -Xms1536m -Xmx1536m -XX:+UseG1GC -XX:MaxGCPauseMillis=150 -XX:G1HeapRegionSize=8m -XX:+ParallelRefProcEnabled -XX:+UseStringDeduplication -XX:+AlwaysPreTouch -cp "%~dp0lib\*;%~dp0l2jserver.jar" net.sf.l2j.gameserver.GameServer
REM -------------------------------------
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