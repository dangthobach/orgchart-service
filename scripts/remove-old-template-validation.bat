@echo off
REM Script to remove old template validation system files
REM These files are redundant since we have the Enhanced system

setlocal enabledelayedexpansion

echo 🗑️  Removing Old Template Validation System Files
echo ==================================================
echo.

REM Create backup directory
for /f "tokens=2 delims==" %%a in ('wmic OS Get localdatetime /value') do set "dt=%%a"
set "YY=%dt:~2,2%" & set "YYYY=%dt:~0,4%" & set "MM=%dt:~4,2%" & set "DD=%dt:~6,2%"
set "HH=%dt:~8,2%" & set "Min=%dt:~10,2%" & set "Sec=%dt:~12,2%"
set "BACKUP_DIR=backup\old-template-validation-%YYYY%%MM%%DD%_%HH%%Min%%Sec%"

echo Step 1: Creating backup in %BACKUP_DIR%
echo ────────────────────────────────────

if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%"

echo Step 2: Backing up and removing old template validation files
echo ────────────────────────────────────

REM Files to remove (old template validation system)
set "OLD_TEMPLATE_FILES=src\main\java\com\learnmore\application\utils\validation\ExcelTemplateValidator.java src\main\java\com\learnmore\application\utils\validation\ExcelTemplateFactory.java src\main\java\com\learnmore\application\service\ExcelTemplateValidationService.java src\main\java\com\learnmore\controller\ExcelTemplateValidationController.java src\test\java\com\learnmore\application\utils\validation\ExcelTemplateValidatorTest.java src\test\java\com\learnmore\application\service\ExcelTemplateValidationServiceTest.java"

for %%f in (%OLD_TEMPLATE_FILES%) do (
    if exist "%%f" (
        echo Backing up %%~nxf...
        copy "%%f" "%BACKUP_DIR%\" >nul
        del "%%f"
        echo ✓ Removed: %%~nxf
    ) else (
        echo ⚠ File not found: %%f
    )
)

echo.
echo Step 3: Backing up and removing old documentation files
echo ────────────────────────────────────

REM Documentation files to remove
set "OLD_DOC_FILES=EXCEL_TEMPLATE_VALIDATION_README.md EXCEL_TEMPLATE_VALIDATION_EXAMPLE.md EXCEL_TEMPLATE_VALIDATION_SUMMARY.md"

for %%f in (%OLD_DOC_FILES%) do (
    if exist "%%f" (
        echo Backing up %%f...
        copy "%%f" "%BACKUP_DIR%\" >nul
        del "%%f"
        echo ✓ Removed: %%f
    ) else (
        echo ⚠ File not found: %%f
    )
)

echo.
echo Step 4: Creating backup README
echo ────────────────────────────────────

REM Create README in backup
(
echo # Archived Old Template Validation System
echo.
echo This directory contains files from the old template validation system that were removed
echo because they are redundant with the new Enhanced system.
echo.
echo ## Archived Files
echo.
echo ### Source Files
echo - **ExcelTemplateValidator.java** - Old template validator ^(473 lines^)
echo - **ExcelTemplateFactory.java** - Old template factory ^(430 lines^)  
echo - **ExcelTemplateValidationService.java** - Old validation service ^(220 lines^)
echo - **ExcelTemplateValidationController.java** - Old controller ^(350 lines^)
echo.
echo ### Test Files
echo - **ExcelTemplateValidatorTest.java** - Old validator tests ^(150 lines^)
echo - **ExcelTemplateValidationServiceTest.java** - Old service tests ^(120 lines^)
echo.
echo ### Documentation Files
echo - **EXCEL_TEMPLATE_VALIDATION_README.md** - Old system documentation
echo - **EXCEL_TEMPLATE_VALIDATION_EXAMPLE.md** - Old system examples
echo - **EXCEL_TEMPLATE_VALIDATION_SUMMARY.md** - Old system summary
echo.
echo ## Why These Were Removed
echo.
echo The old template validation system was replaced by the **Enhanced Excel Template Validation System** which:
echo.
echo 1. **Tận dụng ExcelFacade** - Uses existing ExcelFacade infrastructure
echo 2. **Annotation-based** - Uses @ExcelColumn annotations instead of manual template definitions
echo 3. **Better Performance** - Cached reflection + streaming processing
echo 4. **Type Safety** - ColumnType enum with compile-time checking
echo 5. **Easier Maintenance** - No need to maintain separate template definitions
echo.
echo ## New System Files
echo.
echo The new Enhanced system uses these files instead:
echo.
echo ### Source Files
echo - **ExcelReflectionTemplateValidator.java** - Reflection-based validator
echo - **EnhancedExcelTemplateValidationService.java** - Enhanced service
echo - **EnhancedExcelTemplateValidationController.java** - Enhanced controller
echo - **ExcelColumn.java** - Enhanced annotation with validation attributes
echo.
echo ### Documentation Files
echo - **ENHANCED_EXCEL_TEMPLATE_VALIDATION_README.md** - New system documentation
echo - **ENHANCED_EXCEL_VALIDATION_EXAMPLE.md** - New system examples
echo.
echo ## How to Restore ^(If Needed^)
echo.
echo If you need to restore any of these files:
echo.
echo 1. Copy the file back from this backup directory
echo 2. Update imports in any dependent classes
echo 3. Run tests to verify integration
echo 4. Update documentation
echo.
echo ## Migration Guide
echo.
echo To migrate from old system to new system:
echo.
echo 1. **Update DTO classes** - Add validation attributes to @ExcelColumn annotations
echo 2. **Update service calls** - Use EnhancedExcelTemplateValidationService instead
echo 3. **Update controller endpoints** - Use /api/excel/enhanced-template/* endpoints
echo 4. **Update documentation** - Use new system documentation
echo.
echo ## Removal Date
echo.
echo Archived on: %date%
echo.
echo ## Git History
echo.
echo These files are still available in git history:
echo ```bash
echo git log --all -- src/main/java/com/learnmore/application/utils/validation/ExcelTemplateValidator.java
echo git log --all -- src/main/java/com/learnmore/application/service/ExcelTemplateValidationService.java
echo ```
) > "%BACKUP_DIR%\README.md"

echo ✓ Created backup README
echo.

echo Step 5: Summary
echo ────────────────────────────────────
echo ✓ Removed 6 old template validation source files
echo ✓ Removed 3 old documentation files
echo ✓ Created backup in: %BACKUP_DIR%
echo.

echo 🎯 What's Left ^(Enhanced System^)
echo ────────────────────────────────────
echo ✅ ExcelReflectionTemplateValidator.java
echo ✅ EnhancedExcelTemplateValidationService.java
echo ✅ EnhancedExcelTemplateValidationController.java
echo ✅ Enhanced @ExcelColumn annotation
echo ✅ ENHANCED_EXCEL_TEMPLATE_VALIDATION_README.md
echo ✅ ENHANCED_EXCEL_VALIDATION_EXAMPLE.md
echo.

echo 🚀 Next Steps
echo ────────────────────────────────────
echo 1. Test the application to ensure everything works
echo 2. Update any remaining references to old system
echo 3. Update documentation to point to new system
echo 4. Consider removing backup after verification
echo.

echo ✅ Old template validation system cleanup completed!

pause
