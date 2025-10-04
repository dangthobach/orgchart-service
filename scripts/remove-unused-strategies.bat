@echo off
REM Script to remove unused Excel strategy classes (Windows version)
REM Run this script from project root directory
REM
REM Usage: scripts\remove-unused-strategies.bat

echo ======================================
echo Remove Unused Excel Strategies
echo ======================================
echo.

REM Check if we're in project root
if not exist "pom.xml" (
    echo Error: Must run from project root directory
    exit /b 1
)

REM Create backup directory
set BACKUP_DIR=backup\strategies_%date:~-4,4%%date:~-10,2%%date:~-7,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set BACKUP_DIR=%BACKUP_DIR: =0%
mkdir "%BACKUP_DIR%" 2>nul

echo Step 1: Creating backup...

REM Backup strategy files
xcopy /E /I /Q src\main\java\com\learnmore\application\excel\strategy "%BACKUP_DIR%\strategy\" >nul
echo [OK] Backup created: %BACKUP_DIR%
echo.

REM Create archive directory
set ARCHIVE_DIR=src\main\java\com\learnmore\application\excel\strategy\archive
mkdir "%ARCHIVE_DIR%" 2>nul

echo Step 2: Moving unused strategies to archive...

REM Move unused strategy files
set FILES_MOVED=0

if exist "src\main\java\com\learnmore\application\excel\strategy\impl\TemplateWriteStrategy.java" (
    move "src\main\java\com\learnmore\application\excel\strategy\impl\TemplateWriteStrategy.java" "%ARCHIVE_DIR%\" >nul
    echo [OK] Moved: TemplateWriteStrategy.java
    set /a FILES_MOVED+=1
) else (
    echo [SKIP] File not found: TemplateWriteStrategy.java
)

if exist "src\main\java\com\learnmore\application\excel\strategy\impl\StyledWriteStrategy.java" (
    move "src\main\java\com\learnmore\application\excel\strategy\impl\StyledWriteStrategy.java" "%ARCHIVE_DIR%\" >nul
    echo [OK] Moved: StyledWriteStrategy.java
    set /a FILES_MOVED+=1
) else (
    echo [SKIP] File not found: StyledWriteStrategy.java
)

if exist "src\main\java\com\learnmore\application\excel\strategy\impl\CachedReadStrategy.java" (
    move "src\main\java\com\learnmore\application\excel\strategy\impl\CachedReadStrategy.java" "%ARCHIVE_DIR%\" >nul
    echo [OK] Moved: CachedReadStrategy.java
    set /a FILES_MOVED+=1
) else (
    echo [SKIP] File not found: CachedReadStrategy.java
)

if exist "src\main\java\com\learnmore\application\excel\strategy\impl\ValidatingReadStrategy.java" (
    move "src\main\java\com\learnmore\application\excel\strategy\impl\ValidatingReadStrategy.java" "%ARCHIVE_DIR%\" >nul
    echo [OK] Moved: ValidatingReadStrategy.java
    set /a FILES_MOVED+=1
) else (
    echo [SKIP] File not found: ValidatingReadStrategy.java
)

echo.
echo [OK] %FILES_MOVED% strategies moved to archive
echo.

REM Create README in archive
echo # Archived Excel Strategies > "%ARCHIVE_DIR%\README.md"
echo. >> "%ARCHIVE_DIR%\README.md"
echo This directory contains Excel strategy classes that were removed from active use >> "%ARCHIVE_DIR%\README.md"
echo due to lack of actual usage in production code. >> "%ARCHIVE_DIR%\README.md"
echo. >> "%ARCHIVE_DIR%\README.md"
echo ## Archived Strategies >> "%ARCHIVE_DIR%\README.md"
echo. >> "%ARCHIVE_DIR%\README.md"
echo ### TemplateWriteStrategy >> "%ARCHIVE_DIR%\README.md"
echo - Reason: Requires config.getTemplatePath which is never set >> "%ARCHIVE_DIR%\README.md"
echo - Lines: 377 >> "%ARCHIVE_DIR%\README.md"
echo. >> "%ARCHIVE_DIR%\README.md"
echo ### StyledWriteStrategy >> "%ARCHIVE_DIR%\README.md"
echo - Reason: Requires config.getStyleTemplate which is never used >> "%ARCHIVE_DIR%\README.md"
echo - Lines: 420 >> "%ARCHIVE_DIR%\README.md"
echo. >> "%ARCHIVE_DIR%\README.md"
echo ### CachedReadStrategy >> "%ARCHIVE_DIR%\README.md"
echo - Reason: Requires CacheManager bean which is not configured >> "%ARCHIVE_DIR%\README.md"
echo - Lines: 288 >> "%ARCHIVE_DIR%\README.md"
echo. >> "%ARCHIVE_DIR%\README.md"
echo ### ValidatingReadStrategy >> "%ARCHIVE_DIR%\README.md"
echo - Reason: Requires spring-boot-starter-validation dependency >> "%ARCHIVE_DIR%\README.md"
echo - Lines: 329 >> "%ARCHIVE_DIR%\README.md"
echo. >> "%ARCHIVE_DIR%\README.md"
echo ## How to Restore >> "%ARCHIVE_DIR%\README.md"
echo. >> "%ARCHIVE_DIR%\README.md"
echo 1. Move the file back to impl/ directory >> "%ARCHIVE_DIR%\README.md"
echo 2. Update imports in selector classes >> "%ARCHIVE_DIR%\README.md"
echo 3. Run tests to verify integration >> "%ARCHIVE_DIR%\README.md"

echo [OK] Created archive README
echo.

echo Step 3: Summary
echo ------------------------------------
echo Removed strategies:
echo   - TemplateWriteStrategy (377 lines^)
echo   - StyledWriteStrategy (420 lines^)
echo   - CachedReadStrategy (288 lines^)
echo   - ValidatingReadStrategy (329 lines^)
echo.
echo Total lines removed: ~1414 lines
echo.
echo Backup location: %BACKUP_DIR%
echo Archive location: %ARCHIVE_DIR%
echo.
echo [OK] Cleanup completed successfully!
echo.
echo Next steps:
echo 1. Run: mvnw clean compile
echo 2. Run: mvnw test
echo 3. Verify application starts: mvnw spring-boot:run
echo.
echo If everything works, commit the changes:
echo   git add .
echo   git commit -m "refactor: remove unused Excel strategy classes"
echo.

pause
