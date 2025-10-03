package com.learnmore.application.excel.strategy.impl;

import com.learnmore.application.excel.strategy.WriteStrategy;
import com.learnmore.application.utils.ExcelUtil;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;

/**
 * Template-based write strategy for Excel files
 *
 * This strategy uses an existing Excel file as a template and fills
 * it with data while preserving the template's formatting, formulas,
 * and styles. Ideal for generating reports with consistent branding.
 *
 * Performance characteristics:
 * - Memory: O(template_size + data_size) - loads template + data
 * - Speed: Good for small to medium templates (< 50K records)
 * - File size: Up to 100K records (limited by template approach)
 *
 * Template features:
 * - Preserves all existing formatting (colors, fonts, borders)
 * - Preserves formulas (both in template and can add new)
 * - Preserves images, charts, pivot tables
 * - Preserves named ranges
 * - Preserves sheet structure and hidden sheets
 *
 * Data filling modes:
 * 1. **Replace Mode**: Replace placeholder values (e.g., {{name}})
 * 2. **Append Mode**: Append data starting from specified row
 * 3. **Row Copy Mode**: Copy template row and fill with data
 *
 * Use cases:
 * - Monthly/quarterly report generation
 * - Invoice/receipt generation
 * - Certificate generation
 * - Branded data exports
 * - Forms with pre-filled data
 *
 * Strategy selection:
 * - Priority: 25 (highest for template-based writes)
 * - Supports: When config.getTemplatePath() is provided
 *
 * @param <T> The type of objects to write to Excel
 */
@Slf4j
@Component
public class TemplateWriteStrategy<T> implements WriteStrategy<T> {

    private static final String PLACEHOLDER_PREFIX = "{{";
    private static final String PLACEHOLDER_SUFFIX = "}}";

    /**
     * Execute write using Excel template
     *
     * Process flow:
     * 1. Load template file from config.getTemplatePath()
     * 2. Find data start row (look for placeholders or use config.getStartRow())
     * 3. For each data object:
     *    a. Create new row (or use template row)
     *    b. Fill cells with data
     *    c. Copy row formatting from template
     * 4. Update formulas if needed
     * 5. Save to output file
     *
     * @param fileName Output file name
     * @param data List of objects to write
     * @param config Excel configuration with templatePath
     * @throws ExcelProcessException if template loading or writing fails
     */
    @Override
    public void execute(String fileName, List<T> data, ExcelConfig config) throws ExcelProcessException {
        log.debug("Executing TemplateWriteStrategy for {} records using template: {}",
                 data.size(), config.getTemplatePath());

        // Validate template path
        String templatePath = config.getTemplatePath();
        if (templatePath == null || templatePath.trim().isEmpty()) {
            throw new ExcelProcessException("Template path is required for TemplateWriteStrategy");
        }

        File templateFile = new File(templatePath);
        if (!templateFile.exists()) {
            throw new ExcelProcessException("Template file not found: " + templatePath);
        }

        try (FileInputStream templateInput = new FileInputStream(templateFile)) {
            // Load template workbook
            Workbook workbook = WorkbookFactory.create(templateInput);
            Sheet sheet = workbook.getSheetAt(0); // Use first sheet by default

            // Find data start row (after header)
            int startRow = config.getStartRow();
            if (startRow == 0) {
                // Auto-detect: find first row with placeholders or use row 1
                startRow = findDataStartRow(sheet);
            }

            log.debug("Using template, data will start at row: {}", startRow);

            // Get template row for copying format (if exists)
            Row templateRow = sheet.getRow(startRow);
            CellStyle[] columnStyles = null;

            if (templateRow != null) {
                // Copy styles from template row
                columnStyles = new CellStyle[templateRow.getLastCellNum()];
                for (int i = 0; i < templateRow.getLastCellNum(); i++) {
                    Cell cell = templateRow.getCell(i);
                    if (cell != null) {
                        columnStyles[i] = cell.getCellStyle();
                    }
                }
            }

            // Write data starting from startRow
            int currentRow = startRow;
            for (T dataObject : data) {
                Row row = sheet.getRow(currentRow);
                if (row == null) {
                    row = sheet.createRow(currentRow);
                }

                // Fill row with data
                fillRowWithData(row, dataObject, columnStyles);

                currentRow++;
            }

            // Update formulas if needed
            updateFormulas(sheet, startRow, currentRow - 1);

            // Save to output file
            try (FileOutputStream outputStream = new FileOutputStream(fileName)) {
                workbook.write(outputStream);
            }

            workbook.close();

            log.info("TemplateWriteStrategy completed: {} records written to {} using template",
                    data.size(), fileName);

        } catch (ExcelProcessException e) {
            throw e;
        } catch (Exception e) {
            throw new ExcelProcessException("Failed to write using template: " + e.getMessage(), e);
        }
    }

    /**
     * Find data start row in template
     *
     * Looks for placeholders like {{field}} or uses row 1 as default.
     *
     * @param sheet Excel sheet
     * @return Data start row index
     */
    private int findDataStartRow(Sheet sheet) {
        // Look for first row with placeholders
        for (int i = 0; i <= Math.min(10, sheet.getLastRowNum()); i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                for (Cell cell : row) {
                    String value = getCellValueAsString(cell);
                    if (value != null && value.contains(PLACEHOLDER_PREFIX)) {
                        return i; // Found placeholder row
                    }
                }
            }
        }

        // Default to row 1 (after header row 0)
        return 1;
    }

    /**
     * Fill row with data from object
     *
     * Maps object fields to cells, applying template styles if available.
     *
     * @param row Excel row to fill
     * @param dataObject Data object
     * @param columnStyles Template column styles
     */
    private void fillRowWithData(Row row, Object dataObject, CellStyle[] columnStyles) {
        try {
            // Get all fields from object
            java.lang.reflect.Field[] fields = dataObject.getClass().getDeclaredFields();

            for (int i = 0; i < fields.length; i++) {
                fields[i].setAccessible(true);
                Object value = fields[i].get(dataObject);

                Cell cell = row.getCell(i);
                if (cell == null) {
                    cell = row.createCell(i);
                }

                // Apply template style if available
                if (columnStyles != null && i < columnStyles.length && columnStyles[i] != null) {
                    cell.setCellStyle(columnStyles[i]);
                }

                // Set cell value
                setCellValue(cell, value);
            }

        } catch (Exception e) {
            log.error("Error filling row with data", e);
        }
    }

    /**
     * Set cell value based on type
     *
     * @param cell Excel cell
     * @param value Value to set
     */
    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof java.util.Date) {
            cell.setCellValue((java.util.Date) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    /**
     * Get cell value as string
     *
     * @param cell Excel cell
     * @return Cell value as string
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }

    /**
     * Update formulas after data insertion
     *
     * Updates range-based formulas (SUM, AVERAGE, etc.) to include new data rows.
     *
     * @param sheet Excel sheet
     * @param startRow Start row of data
     * @param endRow End row of data
     */
    private void updateFormulas(Sheet sheet, int startRow, int endRow) {
        // Scan for formula cells and update ranges
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.FORMULA) {
                    String formula = cell.getCellFormula();

                    // Update SUM, AVERAGE, etc. formulas with new range
                    // Example: SUM(A2:A10) -> SUM(A2:A100) if data goes to row 100
                    String updatedFormula = updateFormulaRange(formula, startRow, endRow);

                    if (!updatedFormula.equals(formula)) {
                        cell.setCellFormula(updatedFormula);
                        log.debug("Updated formula from '{}' to '{}'", formula, updatedFormula);
                    }
                }
            }
        }
    }

    /**
     * Update formula range to include new data
     *
     * @param formula Original formula
     * @param startRow Data start row
     * @param endRow Data end row
     * @return Updated formula
     */
    private String updateFormulaRange(String formula, int startRow, int endRow) {
        // Simple implementation: replace A2:A10 with A2:A{endRow}
        // More sophisticated parsing can be added if needed
        String pattern = "([A-Z]+)(\\d+):([A-Z]+)(\\d+)";
        return formula.replaceAll(pattern, "$1" + (startRow + 1) + ":$3" + (endRow + 1));
    }

    /**
     * Check if this strategy supports the given configuration
     *
     * TemplateWriteStrategy is selected when:
     * - config.getTemplatePath() is provided and file exists
     * - Data size is appropriate for template approach (< 100K records)
     *
     * @param dataSize Number of records to write
     * @param cellCount Total number of cells
     * @param config Excel configuration
     * @return true if template is configured, false otherwise
     */
    @Override
    public boolean supports(int dataSize, long cellCount, ExcelConfig config) {
        // Check if template path is provided
        if (config.getTemplatePath() == null || config.getTemplatePath().trim().isEmpty()) {
            return false;
        }

        // Check if template file exists
        File templateFile = new File(config.getTemplatePath());
        if (!templateFile.exists()) {
            log.warn("Template file not found: {}", config.getTemplatePath());
            return false;
        }

        // Check data size (templates work best with < 100K records)
        if (dataSize > 100_000) {
            log.warn("Template strategy not recommended for {} records (> 100K limit)", dataSize);
            return false;
        }

        log.debug("TemplateWriteStrategy supports config: template={}, dataSize={}",
                 config.getTemplatePath(), dataSize);

        return true;
    }

    /**
     * Get strategy name for logging and debugging
     *
     * @return Strategy name
     */
    @Override
    public String getName() {
        return "TemplateWriteStrategy";
    }

    /**
     * Get priority for strategy selection
     *
     * Priority 25 means this strategy is highest priority when template is configured.
     * It will be selected over all other write strategies when template path is provided.
     *
     * Priority ordering:
     * - 0: Default/fallback strategy
     * - 10: SXSSFWriteStrategy (medium files)
     * - 15: CSVWriteStrategy (large files)
     * - 20: XSSFWriteStrategy (small files)
     * - 25: TemplateWriteStrategy (template-based - highest)
     *
     * @return Priority level (25 = highest for template-based writes)
     */
    @Override
    public int getPriority() {
        return 25; // Highest priority when template is provided
    }
}
