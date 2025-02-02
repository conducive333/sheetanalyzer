package org.dataspread.sheetanalyzer.tacoTest;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import org.dataspread.sheetanalyzer.util.Ref;
import org.dataspread.sheetanalyzer.util.RefImpl;
import org.dataspread.sheetanalyzer.util.SheetNotSupportedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.dataspread.sheetanalyzer.util.TestUtil;
import org.dataspread.sheetanalyzer.SheetAnalyzer;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;


public class TestRRPattern {

    private static SheetAnalyzer sheetAnalyzer;
    private static final String sheetName = "RRSheet";
    private static final int maxRows = 1000;

    private static File createRRSheet() throws IOException {
        Workbook workbook = new HSSFWorkbook();
        Sheet sheet = workbook.createSheet(sheetName);
        int colA = 0, colB = 1;
        for (int i = 0; i < maxRows; i++) {
            Row row = sheet.createRow(i);
            Cell cellA = row.createCell(colA);
            Cell cellB = row.createCell(colB);
            cellA.setCellValue(1);
            cellB.setCellFormula("SUM(A" + (i + 1) + ":" + "A" + (i + 2) + ")");
        }
        TestUtil.createAnEmptyRowWithTwoCols(sheet, maxRows, colA, colB);

        File xlsTempFile = TestUtil.createXlsTempFile();
        FileOutputStream outputStream = new FileOutputStream(xlsTempFile);
        workbook.write(outputStream);
        workbook.close();

        return xlsTempFile;
    }

    @BeforeAll
    public static void setUp() throws IOException, SheetNotSupportedException {
        File xlsTempFile = createRRSheet();
        sheetAnalyzer = SheetAnalyzer.createSheetAnalyzer(xlsTempFile.getAbsolutePath());
    }

    @Test
    public void verifyDependencyA() {
        int queryRow = 1, queryColumn = 0;
        Ref queryRef = new RefImpl(queryRow, queryColumn);
        Set<Ref> queryResult = sheetAnalyzer.getDependents(sheetName, queryRef);

        Set<Ref> groundTruth = new HashSet<>();
        int firstRow = 0, firstColumn = 1;
        int lastRow = 1, lastColumn = 1;
        groundTruth.add(new RefImpl(firstRow, firstColumn, lastRow, lastColumn));

        Assertions.assertTrue(TestUtil.hasSameRefs(groundTruth, queryResult));
    }

    @Test
    public void verifyDependencyB() {
        int queryRow = maxRows - 1, queryColumn = 0;
        Ref queryRef = new RefImpl(queryRow, queryColumn);
        Set<Ref> queryResult = sheetAnalyzer.getDependents(sheetName, queryRef);

        Set<Ref> groundTruth = new HashSet<>();
        int firstRow = maxRows - 2, firstColumn = 1;
        int lastRow = maxRows - 1, lastColumn = 1;
        groundTruth.add(new RefImpl(firstRow, firstColumn, lastRow, lastColumn));

        Assertions.assertTrue(TestUtil.hasSameRefs(groundTruth, queryResult));
    }

    @Test
    public void verifyDependencyC() {
        int queryRow = 0, queryColumn = 0;
        Ref queryRef = new RefImpl(queryRow, queryColumn);
        Set<Ref> queryResult = sheetAnalyzer.getDependents(sheetName, queryRef);

        Set<Ref> groundTruth = new HashSet<>();
        int firstRow = 0, firstColumn = 1;
        int lastRow = 0, lastColumn = 1;
        groundTruth.add(new RefImpl(firstRow, firstColumn, lastRow, lastColumn));

        Assertions.assertTrue(TestUtil.hasSameRefs(groundTruth, queryResult));
    }
}
