@echo off
title L2J4Team - GeoDataPatcher
setlocal enabledelayedexpansion

:: Garante que esta no diretorio do .bat (essencial para duplo-clique)
cd /d "%~dp0"

:: Caminhos
set "GEODATA_DIR=data\geodata"
set "BACKUP_DIR=%GEODATA_DIR%\backup"
set "PROJECT_ROOT=..\.."
set "CLASSES_DIR=%PROJECT_ROOT%\classes"
set "PATCHER_CLASS=Dev.SpecialMods.GeoDataPatcher"
set "JAVA_CMD=java"
set "THRESHOLD=96"
set "DRY_RUN=false"

:: Cores ANSI (PowerShell, mais confiavel que prompt $E)
set "ESC="
for /f %%a in ('powershell -noprofile -command "write-host ([char]27)" 2^>nul') do if not defined ESC set "ESC=%%a"
if defined ESC (
  set "C_RESET=%ESC%[0m"
  set "C_GREEN=%ESC%[32m"
  set "C_YELLOW=%ESC%[33m"
  set "C_CYAN=%ESC%[36m"
  set "C_RED=%ESC%[31m"
  set "C_BOLD=%ESC%[1m"
  set "C_DIM=%ESC%[2m"
) else (
  set "C_RESET="
  set "C_GREEN="
  set "C_YELLOW="
  set "C_CYAN="
  set "C_RED="
  set "C_BOLD="
  set "C_DIM="
)

:: Usa apenas ASCII (+ - |) para compatibilidade total com code pages

:main_menu
cls
echo %C_BOLD%%C_CYAN%+---------------------------------------------+%C_RESET%
echo %C_BOLD%%C_CYAN%^|         L2J4Team - GeoDataPatcher          ^|%C_RESET%
echo %C_BOLD%%C_CYAN%+---------------------------------------------+%C_RESET%
echo %C_BOLD%%C_CYAN%^| Local: %GEODATA_DIR%%C_RESET%
echo %C_BOLD%%C_CYAN%^| Threshold: %THRESHOLD%   Dry-Run: %DRY_RUN%%C_RESET%
echo %C_BOLD%%C_CYAN%+---------------------------------------------+%C_RESET%
echo.
echo %C_BOLD%Menu Principal:%C_RESET%
echo.
echo  %C_GREEN%[1]%C_RESET% Consertar UM arquivo .l2j (digitar regiao)
echo  %C_GREEN%[2]%C_RESET% Consertar TODOS os arquivos .l2j (bulk)
echo  %C_GREEN%[3]%C_RESET% Listar arquivos .l2j disponiveis
echo  %C_GREEN%[4]%C_RESET% Configurar threshold (atual: %THRESHOLD%)
echo  %C_GREEN%[5]%C_RESET% Alternar Dry-Run (atual: %DRY_RUN%)
echo  %C_GREEN%[6]%C_RESET% Fazer backup dos geodata
echo  %C_GREEN%[7]%C_RESET% Restaurar backup
echo  %C_GREEN%[8]%C_RESET% Compilar GeoDataPatcher.java
echo  %C_GREEN%[0]%C_RESET% Sair
echo.
set /p "menu_op=> Escolha: "

if "%menu_op%"=="1" goto fix_single
if "%menu_op%"=="2" goto fix_bulk
if "%menu_op%"=="3" goto list_files
if "%menu_op%"=="4" goto config_threshold
if "%menu_op%"=="5" goto toggle_dryrun
if "%menu_op%"=="6" goto do_backup
if "%menu_op%"=="7" goto restore_backup
if "%menu_op%"=="8" goto compile_patcher
if "%menu_op%"=="0" goto :eof
goto main_menu

:: ------------------------------------------------------------------
:fix_single
cls
echo %C_BOLD%%C_CYAN%+--------- Consertar UM arquivo -----------------+%C_RESET%
echo.
echo %C_DIM%Digite o nome da regiao (ex: 16_19) ou o caminho completo.
echo Deixe em branco para voltar.%C_RESET%
echo.
echo %C_YELLOW%Exemplo:%C_RESET% 16_19
echo.
set /p "region=> Regiao: "
if "%region%"=="" goto main_menu

:: Se for apenas "Rx_Ry", monta caminho completo
set "fpath=%region%"
if not "%region:.l2j=%"=="%region%" set "fpath=%GEODATA_DIR%\%region%"
if "%region:.l2j=%"=="%region%" set "fpath=%GEODATA_DIR%\%region%.l2j"

if not exist "%fpath%" (
    echo %C_RED%Arquivo nao encontrado: %fpath%%C_RESET%
    pause
    goto main_menu
)

echo.
echo %C_YELLOW%Iniciando backup antes do patch...%C_RESET%
call :do_backup_silent

echo.
echo %C_GREEN%Aplicando GeoDataPatcher em: %fpath%%C_RESET%
echo.
cd /d "%PROJECT_ROOT%"

if /i "%DRY_RUN%"=="true" (
    echo %C_YELLOW%MODO DRY-RUN: nenhum arquivo sera modificado.%C_RESET%
    %JAVA_CMD% -cp "%CLASSES_DIR%;%~dp0l2jserver.jar" %PATCHER_CLASS% "%fpath%" -t %THRESHOLD% -o "%TEMP%\geodatapatcher_dryrun_output.l2j"
) else (
    %JAVA_CMD% -cp "%CLASSES_DIR%;%~dp0l2jserver.jar" %PATCHER_CLASS% "%fpath%" -t %THRESHOLD%
)

if !ERRORLEVEL! neq 0 (
    echo %C_RED%Erro ao executar o GeoDataPatcher (codigo: !ERRORLEVEL!)%C_RESET%
) else (
    echo.
    echo %C_GREEN%Operacao concluida! Verifique o resultado acima.%C_RESET%
)
echo.
pause
goto main_menu

:: ------------------------------------------------------------------
:fix_bulk
cls
echo %C_BOLD%%C_CYAN%+------- Consertar TODOS os .l2j -----------------+%C_RESET%
echo.
echo %C_YELLOW%ATENCAO: Isso vai processar TODOS os arquivos .l2j na pasta:%C_RESET%
echo %GEODATA_DIR%
echo.
echo %C_YELLOW%Isso pode levar varios minutos dependendo da quantidade.%C_RESET%
echo.
echo   %C_GREEN%[1]%C_RESET% Sim, quero consertar todos
echo   %C_GREEN%[2]%C_RESET% Nao, voltar ao menu
echo.
set /p "bulk_op=> Escolha: "
if not "%bulk_op%"=="1" goto main_menu

echo.
echo %C_YELLOW%Iniciando backup antes do patch em massa...%C_RESET%
call :do_backup_silent

echo.
set "count=0"
set "errors=0"
cd /d "%PROJECT_ROOT%"
for %%f in ("%GEODATA_DIR%\*.l2j") do (
    set /a count+=1
    echo.
    echo %C_CYAN%[!count!] Processando: %%f ...%C_RESET%
    if /i "%DRY_RUN%"=="true" (
        %JAVA_CMD% -cp "%CLASSES_DIR%;%~dp0l2jserver.jar" %PATCHER_CLASS% "%%f" -t %THRESHOLD% -o "%TEMP%\geodatapatcher_dryrun_%%~nxf" >nul
    ) else (
        %JAVA_CMD% -cp "%CLASSES_DIR%;%~dp0l2jserver.jar" %PATCHER_CLASS% "%%f" -t %THRESHOLD%
    )
    if !ERRORLEVEL! neq 0 set /a errors+=1
)

echo.
echo %C_BOLD%%C_GREEN%+---------------------------------------------+%C_RESET%
echo %C_BOLD%%C_GREEN%^|  Bulk concluido! Processados: !count!  Erros: !errors!      ^|%C_RESET%
echo %C_BOLD%%C_GREEN%+---------------------------------------------+%C_RESET%
echo.
pause
goto main_menu

:: ------------------------------------------------------------------
:list_files
cls
echo %C_BOLD%%C_CYAN%+------- Arquivos .l2j Disponiveis ---------------+%C_RESET%
echo.
echo %C_DIM%Local: %GEODATA_DIR%%C_RESET%
echo.
dir /b "%GEODATA_DIR%\*.l2j" 2>nul | find /c /v "" >nul
if !ERRORLEVEL! neq 0 (
    echo %C_RED%Nenhum arquivo .l2j encontrado.%C_RESET%
) else (
    set "idx=0"
    for %%f in ("%GEODATA_DIR%\*.l2j") do (
        set /a idx+=1
        call :format_size %%f
        echo  !idx!. %%f (!fsize!)
    )
)
echo.
echo %C_YELLOW%Pressione qualquer tecla para voltar ao menu.%C_RESET%
pause >nul
goto main_menu

:: ------------------------------------------------------------------
:config_threshold
cls
echo %C_BOLD%%C_CYAN%+------- Configurar Threshold --------------------+%C_RESET%
echo.
echo Threshold atual: %C_YELLOW%%THRESHOLD%%C_RESET% unidades
echo.
echo O threshold define a diferenca maxima de altura entre celulas
echo vizinhas para considerar que e uma rampa (e nao uma parede).
echo.
echo   %C_GREEN%[1]%C_RESET% 32 (conservador - paredes baixas)
echo   %C_GREEN%[2]%C_RESET% 64 (moderado)
echo   %C_GREEN%[3]%C_RESET% 96 (padrao - recomendado)
echo   %C_GREEN%[4]%C_RESET% 128 (agressivo - rampas ingremes)
echo   %C_GREEN%[5]%C_RESET% Valor personalizado
echo   %C_GREEN%[0]%C_RESET% Voltar
echo.
set /p "thresh_op=> Threshold: "
if "%thresh_op%"=="1" set "THRESHOLD=32" & goto threshold_done
if "%thresh_op%"=="2" set "THRESHOLD=64" & goto threshold_done
if "%thresh_op%"=="3" set "THRESHOLD=96" & goto threshold_done
if "%thresh_op%"=="4" set "THRESHOLD=128" & goto threshold_done
if "%thresh_op%"=="5" goto custom_threshold
if "%thresh_op%"=="0" goto main_menu
echo Opcao invalida & timeout /t 2 >nul & goto config_threshold

:custom_threshold
set /p "THRESHOLD=> Digite o valor personalizado (8-500): "
if "%THRESHOLD%"=="" set "THRESHOLD=96"
:threshold_done
echo.
echo %C_GREEN%Threshold alterado para: %THRESHOLD%%C_RESET%
timeout /t 2 >nul
goto main_menu

:: ------------------------------------------------------------------
:toggle_dryrun
if /i "%DRY_RUN%"=="true" (set "DRY_RUN=false") else (set "DRY_RUN=true")
echo.
echo %C_GREEN%Dry-Run alterado para: %DRY_RUN%%C_RESET%
timeout /t 2 >nul
goto main_menu

:: ------------------------------------------------------------------
:do_backup
cls
echo %C_BOLD%%C_CYAN%+------- Backup dos Geodata ----------------------+%C_RESET%
call :do_backup_silent
echo.
echo %C_GREEN%Backup concluido!%C_RESET%
echo %C_DIM%Local: %BACKUP_DIR%\%date:/=-%_%time::=-%%C_RESET%
echo.
pause
goto main_menu

:do_backup_silent
if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%" 2>nul
set "backup_name=%date:/=-%_%time::=-%"
set "backup_name=%backup_name: =0%"
set "backup_path=%BACKUP_DIR%\%backup_name%"
mkdir "%backup_path%" 2>nul
xcopy "%GEODATA_DIR%\*.l2j" "%backup_path%\" /q 2>nul
echo %C_DIM%  Backup salvo em: %backup_path%%C_RESET%
goto :eof

:: ------------------------------------------------------------------
:restore_backup
cls
echo %C_BOLD%%C_CYAN%+------- Restaurar Backup ------------------------+%C_RESET%
echo.
if not exist "%BACKUP_DIR%" (
    echo %C_RED%Nenhum backup encontrado.%C_RESET%
    pause
    goto main_menu
)

echo %C_DIM%Backups disponiveis:%C_RESET%
echo.
set "idx=0"
for /d %%d in ("%BACKUP_DIR%\*") do (
    set /a idx+=1
    set "dir[!idx!]=%%~nxd"
    echo  %C_GREEN%[!idx!]%C_RESET% %%~nxd
)
if %idx% equ 0 (
    echo %C_RED%Nenhum backup encontrado.%C_RESET%
    pause
    goto main_menu
)
echo.
echo  %C_GREEN%[R]%C_RESET% Restaurar o MAIS RECENTE
echo  %C_GREEN%[0]%C_RESET% Voltar
echo.
set /p "restore_op=> Escolha o backup: "

if /i "%restore_op%"=="R" (
    set "latest="
    for /d %%d in ("%BACKUP_DIR%\*") do set "latest=%%d"
    if defined latest (
        echo.
        echo %C_YELLOW%Restaurando de: !latest!%C_RESET%
        xcopy "!latest!\*.l2j" "%GEODATA_DIR%\" /y /q
        echo %C_GREEN%Restauracao concluida!%C_RESET%
    )
    pause
    goto main_menu
)

if "%restore_op%"=="0" goto main_menu

set "restore_num=!dir[%restore_op%]!"
if not defined restore_num (
    echo %C_RED%Opcao invalida.%C_RESET%
    timeout /t 2 >nul
    goto restore_backup
)

echo.
echo %C_YELLOW%Restaurando de: %restore_num%%C_RESET%
xcopy "%BACKUP_DIR%\%restore_num%\*.l2j" "%GEODATA_DIR%\" /y /q
if !ERRORLEVEL! equ 0 (
    echo %C_GREEN%Restauracao concluida!%C_RESET%
) else (
    echo %C_RED%Erro ao restaurar.%C_RESET%
)
echo.
pause
goto main_menu

:: ------------------------------------------------------------------
:compile_patcher
cls
echo %C_BOLD%%C_CYAN%+------- Compilar GeoDataPatcher -----------------+%C_RESET%
echo.
cd /d "%PROJECT_ROOT%"
if not exist "%CLASSES_DIR%" mkdir "%CLASSES_DIR%" 2>nul
echo %C_DIM%Compilando GeoDataPatcher.java...%C_RESET%
javac -cp "%~dp0l2jserver.jar;java" -d "%CLASSES_DIR%" java/Dev/SpecialMods/GeoDataPatcher.java
if !ERRORLEVEL! equ 0 (
    echo.
    echo %C_GREEN%Compilacao bem-sucedida!%C_RESET%
) else (
    echo.
    echo %C_RED%Erro na compilacao (codigo: !ERRORLEVEL!)%C_RESET%
)
echo.
pause
goto main_menu

:: ------------------------------------------------------------------
:format_size
set "fsize=%~z1"
if %fsize% GEQ 1073741824 (
    set /a "fsize=%~z1 / 1073741824" & set "fsize=!fsize! GB"
) else if %fsize% GEQ 1048576 (
    set /a "fsize=%~z1 / 1048576" & set "fsize=!fsize! MB"
) else if %fsize% GEQ 1024 (
    set /a "fsize=%~z1 / 1024" & set "fsize=!fsize! KB"
) else (
    set "fsize=!fsize! B"
)
goto :eof
