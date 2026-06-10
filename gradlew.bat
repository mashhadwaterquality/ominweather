@rem =========================================================================
@rem Gradle Wrapper Proxy for Windows (AI Studio Export)
@rem =========================================================================
@echo off

where gradle >nul 2>nul
if %ERRORLEVEL% equ 0 (
    gradle %*
) else (
    echo.
    echo =========================================================================
    echo                      Gradle Wrapper Proxy Finder
    echo =========================================================================
    echo Gradle was not found in your system's PATH.
    echo Please install Gradle (v9.3.1 recommended) or open this project
    echo inside Android Studio, which manages Gradle automatically.
    echo =========================================================================
    echo.
    pause
    exit /b 1
)
