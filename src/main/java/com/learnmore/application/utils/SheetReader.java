package com.learnmore.application.utils;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;


public class SheetReader<T> {
    private final Supplier<T> supplier;
    private final List<BiConsumer<Cell, T>> populators;


    public SheetReader(Supplier<T> supplier, List<BiConsumer<Cell, T>> populators) {
        this.supplier = supplier;
        this.populators = populators;
    }


    public List<T> readSheet(MultipartFile file, final boolean hasHeader) throws IOException {
        Workbook offices = new XSSFWorkbook(file.getInputStream());
        Sheet worksheet = offices.getSheetAt(0);
        final Iterator<Row> rows = worksheet.iterator();
        if(hasHeader) {
            //skip first row
            rows.next();
        }
        final List<T> ts = new LinkedList<>();
        while(rows.hasNext()) {
            final Row row = rows.next();
            final T t = supplier.get();
            for(int i =0; i<populators.size();++i) {
                populators.get(i).accept(row.getCell(i), t);
            }
            ts.add(t);
        }
        return ts;
    }

}
