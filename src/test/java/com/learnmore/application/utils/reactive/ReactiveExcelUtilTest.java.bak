package com.learnmore.application.utils.reactive;

import com.learnmore.application.utils.ExcelColumn;
import com.learnmore.application.utils.ExcelUtil;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReactiveExcelUtilTest {

    private byte[] sampleWorkbook;

    @BeforeEach
    void setUp() throws Exception {
        sampleWorkbook = createSampleWorkbook();
    }

    @Test
    void shouldStreamExcelRowsReactively() {
        InputStream inputStream = new ByteArrayInputStream(sampleWorkbook);

    Flux<TestRecord> flux = ReactiveExcelUtil.processExcelReactive(
                inputStream,
        TestRecord.class,
                ExcelConfig.builder().build()
        );

        StepVerifier.create(flux)
                .expectNextCount(5)
                .verifyComplete();
    }

    @Test
    void reactiveProcessingShouldMatchSynchronousResults() throws ExcelProcessException {
        InputStream reactiveInput = new ByteArrayInputStream(sampleWorkbook);
        InputStream blockingInput = new ByteArrayInputStream(sampleWorkbook);

    List<TestRecord> reactiveResult = ReactiveExcelUtil.processExcelReactive(reactiveInput,
        TestRecord.class,
                ExcelConfig.builder().build())
            .collectList()
            .block();

    List<TestRecord> blockingResult = ExcelUtil.processExcel(blockingInput,
        TestRecord.class,
                ExcelConfig.builder().build());

        assertThat(reactiveResult).isNotNull();
        assertThat(blockingResult).isNotNull();
        assertThat(reactiveResult).hasSameSizeAs(blockingResult);
        assertThat(reactiveResult).containsExactlyElementsOf(blockingResult);
    }

    private byte[] createSampleWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("data");

            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("firstName");
            header.createCell(1).setCellValue("lastName");

            for (int i = 1; i <= 5; i++) {
                var row = sheet.createRow(i);
                row.createCell(0).setCellValue("First" + i);
                row.createCell(1).setCellValue("Last" + i);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
    static final class TestRecord {
        @ExcelColumn(name = "firstName")
        private String firstName;

        @ExcelColumn(name = "lastName")
        private String lastName;

        public TestRecord() {
        }

        public TestRecord(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }
    }
}
