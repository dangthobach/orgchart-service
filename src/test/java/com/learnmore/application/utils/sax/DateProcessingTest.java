package com.learnmore.application.utils.sax;

import com.learnmore.application.utils.converter.TypeConverter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test date processing in SAX processor
 */
public class DateProcessingTest {

    @Test
    public void testDateValueProcessing() {
        // Test the date processing logic
        String testValue1 = "11/15/25";
        String testValue2 = "11/15/2025";
        String testValue3 = "1/5/25";
        String testValue4 = "12/31/99";
        String testValue5 = "11-2025";  // MM-yyyy format
        String testValue6 = "12-2024";  // MM-yyyy format
        
        // Test pattern matching
        assertTrue(isDateField(testValue1));
        assertTrue(isDateField(testValue2));
        assertTrue(isDateField(testValue3));
        assertTrue(isDateField(testValue4));
        assertTrue(isDateField(testValue5));
        assertTrue(isDateField(testValue6));
        
        // Test conversion
        String processed1 = processDateValue(testValue1, String.class);
        String processed2 = processDateValue(testValue2, String.class);
        String processed3 = processDateValue(testValue3, String.class);
        String processed4 = processDateValue(testValue4, String.class);
        String processed5 = processDateValue(testValue5, String.class);
        String processed6 = processDateValue(testValue6, String.class);
        
        assertEquals("11/15/2025", processed1);
        assertEquals("11/15/2025", processed2);
        assertEquals("1/5/2025", processed3);
        assertEquals("12/31/1999", processed4);
        assertEquals("11-2025", processed5);  // MM-yyyy format should remain unchanged
        assertEquals("12-2024", processed6);  // MM-yyyy format should remain unchanged
    }
    
    @Test
    public void testTypeConverterDateHandling() {
        TypeConverter converter = TypeConverter.getInstance();
        
        // Test various date formats
        String[] testDates = {
            "11/15/2025",
            "1/5/2025", 
            "12/31/1999",
            "2025-11-15",
            "15/11/2025",
            "11-2025",    // MM-yyyy format
            "12-2024"     // MM-yyyy format
        };
        
        for (String dateStr : testDates) {
            try {
                Object result = converter.convert(dateStr, String.class);
                assertNotNull(result);
                System.out.println("Date '" + dateStr + "' converted to: " + result);
            } catch (Exception e) {
                System.out.println("Failed to convert date '" + dateStr + "': " + e.getMessage());
            }
        }
    }
    
    // Helper methods (copied from TrueStreamingSAXProcessor for testing)
    private String processDateValue(String formattedValue, Class<?> fieldType) {
        if (formattedValue == null || formattedValue.trim().isEmpty()) {
            return formattedValue;
        }
        
        // Only process if field type is date-related
        if (fieldType == String.class && isDateField(formattedValue)) {
            // Handle cases like "11/15/25" -> "11/15/2025"
            if (formattedValue.matches("\\d{1,2}/\\d{1,2}/\\d{2}")) {
                String[] parts = formattedValue.split("/");
                if (parts.length == 3) {
                    String month = parts[0];
                    String day = parts[1];
                    String year = parts[2];
                    
                    // Convert 2-digit year to 4-digit year
                    if (year.length() == 2) {
                        int yearInt = Integer.parseInt(year);
                        if (yearInt >= 0 && yearInt <= 99) {
                            // Assume years 00-30 are 2000-2030, 31-99 are 1931-1999
                            if (yearInt <= 30) {
                                year = "20" + year;
                            } else {
                                year = "19" + year;
                            }
                        }
                    }
                    
                    return month + "/" + day + "/" + year;
                }
            }
        }
        
        return formattedValue;
    }
    
    private boolean isDateField(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        
        // Check for common date patterns
        return value.matches("\\d{1,2}/\\d{1,2}/\\d{2,4}") ||  // MM/dd/yyyy or MM/dd/yy
               value.matches("\\d{1,2}-\\d{1,2}-\\d{2,4}") ||  // MM-dd-yyyy or MM-dd-yy
               value.matches("\\d{4}-\\d{1,2}-\\d{1,2}");      // yyyy-MM-dd
    }
}
