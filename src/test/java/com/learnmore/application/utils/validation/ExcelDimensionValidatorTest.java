package com.learnmore.application.utils.validation;

import com.learnmore.application.utils.exception.ExcelProcessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases cho ExcelDimensionValidator
 */
public class ExcelDimensionValidatorTest {
    
    @Test
    public void testWrapWithBuffer_RegularInputStream() {
        // Test wrapping regular InputStream
        ByteArrayInputStream inputStream = new ByteArrayInputStream("test".getBytes());
        var bufferedStream = ExcelDimensionValidator.wrapWithBuffer(inputStream);
        
        assertNotNull(bufferedStream);
        assertTrue(bufferedStream.markSupported());
    }
    
    @Test
    public void testWrapWithBuffer_AlreadyBuffered() {
        // Test wrapping already buffered InputStream
        ByteArrayInputStream inputStream = new ByteArrayInputStream("test".getBytes());
        var bufferedStream1 = ExcelDimensionValidator.wrapWithBuffer(inputStream);
        var bufferedStream2 = ExcelDimensionValidator.wrapWithBuffer(bufferedStream1);
        
        // Should return the same instance if already buffered
        assertSame(bufferedStream1, bufferedStream2);
    }
    
    @Test
    public void testValidateRowCount_UnsupportedMarkReset() {
        // Test with InputStream that doesn't support mark/reset
        ByteArrayInputStream inputStream = new ByteArrayInputStream("test".getBytes());
        
        assertThrows(ExcelProcessException.class, () -> {
            ExcelDimensionValidator.validateRowCount(inputStream, 100, 1);
        });
    }
    
    @Test
    public void testValidateRowCount_MaxRowsZero() throws IOException {
        // Test với maxRows = 0 (không giới hạn)
        // Tạo mock Excel data - này sẽ fail vì không phải Excel file thật
        ByteArrayInputStream inputStream = new ByteArrayInputStream("fake excel".getBytes());
        var bufferedStream = ExcelDimensionValidator.wrapWithBuffer(inputStream);
        
        // Nên throw exception vì không phải Excel file hợp lệ
        assertThrows(ExcelProcessException.class, () -> {
            ExcelDimensionValidator.validateRowCount(bufferedStream, 0, 1);
        });
    }
    
    /**
     * Test helper method để tạo mock Excel file
     * Note: Để test đầy đủ cần Excel file thật với dimension
     */
    private MockMultipartFile createMockExcelFile(String filename, String content) {
        return new MockMultipartFile(
            "file",
            filename,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            content.getBytes()
        );
    }
}
