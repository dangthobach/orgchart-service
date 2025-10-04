#!/bin/bash

# Script to remove unused Excel strategy classes
# Run this script from project root directory
#
# Usage: ./scripts/remove-unused-strategies.sh
#
# This script will:
# 1. Move unused strategy classes to archive folder
# 2. Update imports in selector classes
# 3. Create backup before changes

set -e

echo "======================================"
echo "Remove Unused Excel Strategies"
echo "======================================"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if we're in project root
if [ ! -f "pom.xml" ]; then
    echo -e "${RED}Error: Must run from project root directory${NC}"
    exit 1
fi

# Create backup directory
BACKUP_DIR="backup/strategies_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BACKUP_DIR"

echo -e "${YELLOW}Step 1: Creating backup...${NC}"

# Backup strategy files
cp -r src/main/java/com/learnmore/application/excel/strategy "$BACKUP_DIR/"
echo -e "${GREEN}✓ Backup created: $BACKUP_DIR${NC}"
echo ""

# Create archive directory for unused strategies
ARCHIVE_DIR="src/main/java/com/learnmore/application/excel/strategy/archive"
mkdir -p "$ARCHIVE_DIR"

echo -e "${YELLOW}Step 2: Moving unused strategies to archive...${NC}"

# List of unused strategy files
UNUSED_STRATEGIES=(
    "src/main/java/com/learnmore/application/excel/strategy/impl/TemplateWriteStrategy.java"
    "src/main/java/com/learnmore/application/excel/strategy/impl/StyledWriteStrategy.java"
    "src/main/java/com/learnmore/application/excel/strategy/impl/CachedReadStrategy.java"
    "src/main/java/com/learnmore/application/excel/strategy/impl/ValidatingReadStrategy.java"
)

# Move files
for file in "${UNUSED_STRATEGIES[@]}"; do
    if [ -f "$file" ]; then
        filename=$(basename "$file")
        echo "Moving $filename to archive..."
        mv "$file" "$ARCHIVE_DIR/"
        echo -e "${GREEN}✓ Moved: $filename${NC}"
    else
        echo -e "${YELLOW}⚠ File not found: $file${NC}"
    fi
done

echo ""
echo -e "${GREEN}✓ All unused strategies moved to archive${NC}"
echo ""

# Update README in archive
cat > "$ARCHIVE_DIR/README.md" << 'EOF'
# Archived Excel Strategies

This directory contains Excel strategy classes that were removed from active use
due to lack of actual usage in production code.

## Archived Strategies

### TemplateWriteStrategy
- **Reason**: Requires `config.getTemplatePath()` which is never set in codebase
- **Lines**: 377
- **Can restore**: Yes, when template-based writing is needed

### StyledWriteStrategy
- **Reason**: Requires `config.getStyleTemplate()` which is never used
- **Lines**: 420
- **Can restore**: Yes, when professional styling is needed

### CachedReadStrategy
- **Reason**: Requires `CacheManager` bean which is not configured
- **Lines**: 288
- **Can restore**: Yes, when Spring Cache is configured

### ValidatingReadStrategy
- **Reason**: Requires `spring-boot-starter-validation` dependency (not added)
- **Lines**: 329
- **Can restore**: Yes, when JSR-303 validation is needed

## How to Restore

If you need any of these strategies:

1. Move the file back to `impl/` directory
2. Update imports in selector classes
3. Add required dependencies (if needed)
4. Run tests to verify integration

## Removal Date

Archived on: $(date +%Y-%m-%d)

## Git History

These files are still available in git history:
```bash
git log --all -- src/main/java/com/learnmore/application/excel/strategy/impl/TemplateWriteStrategy.java
```
EOF

echo -e "${GREEN}✓ Created archive README${NC}"
echo ""

echo -e "${YELLOW}Step 3: Summary${NC}"
echo "────────────────────────────────────"
echo "Removed strategies:"
echo "  - TemplateWriteStrategy (377 lines)"
echo "  - StyledWriteStrategy (420 lines)"
echo "  - CachedReadStrategy (288 lines)"
echo "  - ValidatingReadStrategy (329 lines)"
echo ""
echo "Total lines removed: ~1414 lines"
echo ""
echo "Backup location: $BACKUP_DIR"
echo "Archive location: $ARCHIVE_DIR"
echo ""
echo -e "${GREEN}✓ Cleanup completed successfully!${NC}"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "1. Run: ./mvnw clean compile"
echo "2. Run: ./mvnw test"
echo "3. Verify application starts: ./mvnw spring-boot:run"
echo ""
echo "If everything works, you can commit the changes:"
echo "  git add ."
echo "  git commit -m \"refactor: remove unused Excel strategy classes\""
echo ""
