@echo off

SETLOCAL enabledelayedexpansion
TITLE FSCrawler ${project.version}

set SCRIPT_DIR=%~dp0
for %%I in ("%SCRIPT_DIR%..") do set FS_HOME=%%~dpfI

REM set to headless, just in case
set JAVA_OPTS=%JAVA_OPTS% -Djava.awt.headless=true

REM Ensure UTF-8 encoding by default (e.g. filenames)
set JAVA_OPTS=%JAVA_OPTS% -Dfile.encoding=UTF-8

REM If the user defined FS_JAVA_OPTS, we will use it to start the crawler
set JAVA_OPTS=%JAVA_OPTS% %FS_JAVA_OPTS%

set FS_CLASSPATH=%FS_HOME%/lib/*

SET params='%*'

:loop
FOR /F "usebackq tokens=1* delims= " %%A IN (!params!) DO (
    SET current=%%A
    SET params='%%B'
	SET silent=N

	IF "!current!" == "-s" (
		SET silent=Y
	)
	IF "!current!" == "--silent" (
		SET silent=Y
	)

	IF "!silent!" == "Y" (
		SET nopauseonerror=Y
	) ELSE (
	    IF "x!newparams!" NEQ "x" (
	        SET newparams=!newparams! !current!
        ) ELSE (
            SET newparams=!current!
        )
	)

    IF "x!params!" NEQ "x" (
		GOTO loop
	)
)

"%JAVA_HOME%\bin\java" %JAVA_OPTS% -cp "%FS_CLASSPATH%" -jar "%FS_HOME%/lib/${project.build.finalName}.jar" !newparams!

ENDLOCAL
