@echo off

SETLOCAL enabledelayedexpansion
TITLE FSCrawler ${project.version}

set SCRIPT_DIR=%~dp0
REM change into script directory
PUSHD "%SCRIPT_DIR%\.."
for %%I in ("%SCRIPT_DIR%..") do set FS_HOME=%%~dpfI

IF DEFINED JAVA_HOME (
  set JAVA="%JAVA_HOME%\bin\java.exe"
) ELSE (
  FOR %%I IN (java.exe) DO set JAVA=%%~$PATH:I
)
IF NOT EXIST %JAVA% (
  ECHO Could not find any executable java binary. Please install java in your PATH or set JAVA_HOME 1>&2
  EXIT /B 1
)

REM set to headless, just in case
set JAVA_OPTS=%JAVA_OPTS% -Djava.awt.headless=true

REM Ensure UTF-8 encoding by default (e.g. filenames)
set JAVA_OPTS=%JAVA_OPTS% -Dfile.encoding=UTF-8
set JAVA_OPTS=%JAVA_OPTS% -Dsun.jnu.encoding=UTF-8

REM Use LOG4J2 instead of java.util.logging
set JAVA_OPTS=%JAVA_OPTS% -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager

REM Define LOG4J2 config file
set JAVA_OPTS=%JAVA_OPTS% -Dlog4j.configurationFile=%FS_HOME%/config/log4j2.xml

REM Fix for CVE-2021-44228
set JAVA_OPTS=%JAVA_OPTS% -Dlog4j2.formatMsgNoLookups=true

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

%JAVA% %JAVA_OPTS% -cp "%FS_CLASSPATH%" "${distribution.mainClassName}" !newparams!
REM Return to original directory
POPD
ENDLOCAL
