package com.learnmore.application.utils.sax;

import com.learnmore.application.utils.ExcelUtil.MultiSheetResult;
import com.learnmore.application.utils.config.ExcelConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStringsTable;
import org.apache.poi.xssf.model.StylesTable;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.util.*;

/**
 * SAX-based multi-sheet processor for large Excel files
 */
@Slf4j
public class SAXMultiSheetProcessor {
    private final Map<String, Class<?>> sheetClassMap;
    private final ExcelConfig config;

    public SAXMultiSheetProcessor(Map<String, Class<?>> sheetClassMap, ExcelConfig config) {
        this.sheetClassMap = sheetClassMap;
        this.config = config;
    }

    public Map<String, MultiSheetResult> process(InputStream inputStream) throws Exception {
        Map<String, MultiSheetResult> results = new HashMap<>();
        try (OPCPackage opcPackage = OPCPackage.open(inputStream)) {
            XSSFReader xssfReader = new XSSFReader(opcPackage);
            org.apache.poi.xssf.model.SharedStringsTable sharedStringsTable = (org.apache.poi.xssf.model.SharedStringsTable) xssfReader.getSharedStringsTable();
            StylesTable stylesTable = xssfReader.getStylesTable();
            XSSFReader.SheetIterator sheetIterator = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
            DataFormatter dataFormatter = new DataFormatter();
            while (sheetIterator.hasNext()) {
                try (InputStream sheetStream = sheetIterator.next()) {
                    String sheetName = sheetIterator.getSheetName();
                    Class<?> beanClass = sheetClassMap.get(sheetName);
                    if (beanClass == null) {
                        log.warn("Sheet '{}' not mapped to POJO, skipping", sheetName);
                        continue;
                    }
                    java.util.Map<String, java.lang.reflect.Field> fieldMapping = new java.util.HashMap<>();
                    com.learnmore.application.utils.sax.SAXExcelProcessor.SAXExcelContentHandler contentHandler =
                        new com.learnmore.application.utils.sax.SAXExcelProcessor.SAXExcelContentHandler(
                            beanClass, fieldMapping, config, null, null, new java.util.ArrayList<>()
                        );
                    XMLReader xmlReader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
                    XSSFSheetXMLHandler sheetHandler = new XSSFSheetXMLHandler(
                        stylesTable, sharedStringsTable, contentHandler, dataFormatter, false
                    );
                    xmlReader.setContentHandler(sheetHandler);
                    xmlReader.parse(new InputSource(sheetStream));
                    // Build result
                    MultiSheetResult result = new MultiSheetResult(
                        new ArrayList<>(contentHandler.result),
                        new ArrayList<>(),
                        (int) contentHandler.processedRows.get(),
                        "OK"
                    );
                    results.put(sheetName, result);
                }
            }
        }
        return results;
    }
}
