@rem ----------------------------------------------------------------------------
@rem  Gradle start up script for WRN (Windows)
@rem ----------------------------------------------------------------------------

@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto init

echo.
echo ERROR: JAVA_HOME not found in your environment.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java compiler installation.
echo.
goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto init

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java compiler installation.
echo.
goto fail

:init
@rem Get command-line arguments, handling Windows variants
if not "%OS%" == "Windows_NT" goto win9xME_args

:win9xME_args
@rem Slurp the command line arguments.
set CMD_LINE_ARGS=
set _ARG_LINE=
:win9xME_args_loop
if "%~1" == "" goto endargs_loop
set ARG=%*
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift /1
goto :win9xME_args_loop

:endargs_loop

:win9xME_args
@rem Slurp the command line arguments
set CMD_LINE_ARGS=%*

@rem Locate the Gradle distribution
if exist "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" (
    call :run_wrapper
) else (
    echo ERROR: gradle-wrapper.jar not found. Please run:
    echo   gradle wrapper --gradle-version 8.5
    goto fail
)

:run_wrapper
"%JAVA_HOME%\bin\java.exe" %JAVA_OPTS% %GRADLE_OPTS% ^
    -classpath "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" ^
    org.gradle.wrapper.GradleWrapperMain %CMD_LINE_ARGS%
goto end

:end
@endlocal & set EXIT_CODE=%EXIT_CODE%
if %EXIT_CODE% equ 0 exit /b %EXIT_CODE%
exit /b %EXIT_CODE%

:fail
echo.
echo ERROR: Gradle build failed.
echo.
endlocal & set EXIT_CODE=1
exit /b 1
