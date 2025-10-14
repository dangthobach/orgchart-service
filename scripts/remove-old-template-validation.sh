#!/bin/bash

# Script to remove old template validation system files
# These files are redundant since we have the Enhanced system

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🗑️  Removing Old Template Validation System Files${NC}"
echo "=================================================="
echo ""

# Create backup directory
BACKUP_DIR="backup/old-template-validation-$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BACKUP_DIR"

echo -e "${YELLOW}Step 1: Creating backup in $BACKUP_DIR${NC}"
echo "────────────────────────────────────"

# Files to remove (old template validation system)
OLD_TEMPLATE_FILES=(
    "src/main/java/com/learnmore/application/utils/validation/ExcelTemplateValidator.java"
    "src/main/java/com/learnmore/application/utils/validation/ExcelTemplateFactory.java"
    "src/main/java/com/learnmore/application/service/ExcelTemplateValidationService.java"
    "src/main/java/com/learnmore/controller/ExcelTemplateValidationController.java"
    "src/test/java/com/learnmore/application/utils/validation/ExcelTemplateValidatorTest.java"
    "src/test/java/com/learnmore/application/service/ExcelTemplateValidationServiceTest.java"
)

# Documentation files to remove
OLD_DOC_FILES=(
    "EXCEL_TEMPLATE_VALIDATION_README.md"
    "EXCEL_TEMPLATE_VALIDATION_EXAMPLE.md"
    "EXCEL_TEMPLATE_VALIDATION_SUMMARY.md"
)

echo -e "${YELLOW}Step 2: Backing up and removing old template validation files${NC}"
echo "────────────────────────────────────"

# Backup and remove source files
for file in "${OLD_TEMPLATE_FILES[@]}"; do
    if [ -f "$file" ]; then
        filename=$(basename "$file")
        echo "Backing up $filename..."
        cp "$file" "$BACKUP_DIR/"
        rm "$file"
        echo -e "${GREEN}✓ Removed: $filename${NC}"
    else
        echo -e "${YELLOW}⚠ File not found: $file${NC}"
    fi
done

echo ""
echo -e "${YELLOW}Step 3: Backing up and removing old documentation files${NC}"
echo "────────────────────────────────────"

# Backup and remove documentation files
for file in "${OLD_DOC_FILES[@]}"; do
    if [ -f "$file" ]; then
        filename=$(basename "$file")
        echo "Backing up $filename..."
        cp "$file" "$BACKUP_DIR/"
        rm "$file"
        echo -e "${GREEN}✓ Removed: $filename${NC}"
    else
        echo -e "${YELLOW}⚠ File not found: $file${NC}"
    fi
done

echo ""
echo -e "${YELLOW}Step 4: Creating backup README${NC}"
echo "────────────────────────────────────"

# Create README in backup
cat > "$BACKUP_DIR/README.md" << 'EOF'
# Archived Old Template Validation System

This directory contains files from the old template validation system that were removed
because they are redundant with the new Enhanced system.

## Archived Files

### Source Files
- **ExcelTemplateValidator.java** - Old template validator (473 lines)
- **ExcelTemplateFactory.java** - Old template factory (430 lines)  
- **ExcelTemplateValidationService.java** - Old validation service (220 lines)
- **ExcelTemplateValidationController.java** - Old controller (350 lines)

### Test Files
- **ExcelTemplateValidatorTest.java** - Old validator tests (150 lines)
- **ExcelTemplateValidationServiceTest.java** - Old service tests (120 lines)

### Documentation Files
- **EXCEL_TEMPLATE_VALIDATION_README.md** - Old system documentation
- **EXCEL_TEMPLATE_VALIDATION_EXAMPLE.md** - Old system examples
- **EXCEL_TEMPLATE_VALIDATION_SUMMARY.md** - Old system summary

## Why These Were Removed

The old template validation system was replaced by the **Enhanced Excel Template Validation System** which:

1. **Tận dụng ExcelFacade** - Uses existing ExcelFacade infrastructure
2. **Annotation-based** - Uses @ExcelColumn annotations instead of manual template definitions
3. **Better Performance** - Cached reflection + streaming processing
4. **Type Safety** - ColumnType enum with compile-time checking
5. **Easier Maintenance** - No need to maintain separate template definitions

## New System Files

The new Enhanced system uses these files instead:

### Source Files
- **ExcelReflectionTemplateValidator.java** - Reflection-based validator
- **EnhancedExcelTemplateValidationService.java** - Enhanced service
- **EnhancedExcelTemplateValidationController.java** - Enhanced controller
- **ExcelColumn.java** - Enhanced annotation with validation attributes

### Documentation Files
- **ENHANCED_EXCEL_TEMPLATE_VALIDATION_README.md** - New system documentation
- **ENHANCED_EXCEL_VALIDATION_EXAMPLE.md** - New system examples

## How to Restore (If Needed)

If you need to restore any of these files:

1. Copy the file back from this backup directory
2. Update imports in any dependent classes
3. Run tests to verify integration
4. Update documentation

## Migration Guide

To migrate from old system to new system:

1. **Update DTO classes** - Add validation attributes to @ExcelColumn annotations
2. **Update service calls** - Use EnhancedExcelTemplateValidationService instead
3. **Update controller endpoints** - Use /api/excel/enhanced-template/* endpoints
4. **Update documentation** - Use new system documentation

## Removal Date

Archived on: $(date +%Y-%m-%d)

## Git History

These files are still available in git history:
```bash
git log --all -- src/main/java/com/learnmore/application/utils/validation/ExcelTemplateValidator.java
git log --all -- src/main/java/com/learnmore/application/service/ExcelTemplateValidationService.java
```
EOF

echo -e "${GREEN}✓ Created backup README${NC}"
echo ""

echo -e "${YELLOW}Step 5: Summary${NC}"
echo "────────────────────────────────────"
echo -e "${GREEN}✓ Removed ${#OLD_TEMPLATE_FILES[@]} old template validation source files${NC}"
echo -e "${GREEN}✓ Removed ${#OLD_DOC_FILES[@]} old documentation files${NC}"
echo -e "${GREEN}✓ Created backup in: $BACKUP_DIR${NC}"
echo ""

echo -e "${BLUE}🎯 What's Left (Enhanced System)${NC}"
echo "────────────────────────────────────"
echo -e "${GREEN}✅ ExcelReflectionTemplateValidator.java${NC}"
echo -e "${GREEN}✅ EnhancedExcelTemplateValidationService.java${NC}"
echo -e "${GREEN}✅ EnhancedExcelTemplateValidationController.java${NC}"
echo -e "${GREEN}✅ Enhanced @ExcelColumn annotation${NC}"
echo -e "${GREEN}✅ ENHANCED_EXCEL_TEMPLATE_VALIDATION_README.md${NC}"
echo -e "${GREEN}✅ ENHANCED_EXCEL_VALIDATION_EXAMPLE.md${NC}"
echo ""

echo -e "${BLUE}🚀 Next Steps${NC}"
echo "────────────────────────────────────"
echo "1. Test the application to ensure everything works"
echo "2. Update any remaining references to old system"
echo "3. Update documentation to point to new system"
echo "4. Consider removing backup after verification"
echo ""

echo -e "${GREEN}✅ Old template validation system cleanup completed!${NC}"
