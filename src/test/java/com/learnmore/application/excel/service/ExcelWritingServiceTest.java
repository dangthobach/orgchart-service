package com.learnmore.application.excel.service;

import com.learnmore.application.dto.migration.ExcelRowDTO;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.config.ExcelConfigFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Collections;

public class ExcelWritingServiceTest {

    private final ExcelWritingService service = new ExcelWritingService(
            new com.learnmore.application.excel.strategy.selector.WriteStrategySelector(
                    java.util.List.of()
            ),
            new com.learnmore.application.excel.helper.ExcelWriteHelper()
    );

    @Test
    public void testWriteToBytes_WithConfig_EmptyData_WritesHeader() throws Exception {
        ExcelConfig cfg = ExcelConfigFactory.createProductionConfig();
        cfg.setOutputBeanClassName(ExcelRowDTO.class.getName());

        byte[] bytes = service.writeToBytes(Collections.emptyList(), cfg);
        Assertions.assertNotNull(bytes);
        Assertions.assertTrue(bytes.length > 0);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = wb.getSheetAt(0);
            Assertions.assertNotNull(sheet.getRow(0));
        }
    }
}


