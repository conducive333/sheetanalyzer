package org.dataspread.sheetanalyzer.parser;

import org.apache.poi.hssf.usermodel.HSSFEvaluationWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.formula.FormulaParser;
import org.apache.poi.ss.formula.FormulaParsingWorkbook;
import org.apache.poi.ss.formula.FormulaRenderingWorkbook;
import org.apache.poi.ss.formula.FormulaType;
import org.apache.poi.ss.formula.ptg.*;
import org.apache.poi.ss.usermodel.*;
import org.dataspread.sheetanalyzer.util.*;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;

public class POIParser implements SpreadsheetParser {

    private Workbook workbook;
    private FormulaParsingWorkbook evalbook;
    private final HashMap<String, SheetData> sheetDataMap;
    private final String fileName;

    public POIParser(String filePath) throws SheetNotSupportedException {
        File fileItem = new File(filePath);
        fileName = fileItem.getName();
        sheetDataMap = new HashMap<>();

        try {
            this.workbook = WorkbookFactory.create(fileItem);
            this.evalbook = HSSFEvaluationWorkbook.create((HSSFWorkbook) workbook);
            parseSpreadsheet();
        } catch (Exception e) {
            e.printStackTrace();
            throw new SheetNotSupportedException("Parsing " + filePath + " failed");
        }

    }

    public String getFileName() {
        return fileName;
    }

    public HashMap<String, SheetData> getSheetData() {
        return sheetDataMap;
    }

    public boolean skipParsing(int threshold) {
        int totalRows = 0;
        for(Sheet sheet: workbook) {
            totalRows += sheet.getPhysicalNumberOfRows();
        }
        return totalRows <= threshold;
    }

    private void parseSpreadsheet() throws SheetNotSupportedException {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            SheetData sheetData = parseOneSheet(workbook.getSheetAt(i));
            sheetDataMap.put(workbook.getSheetAt(i).getSheetName(), sheetData);
        }
    }

    private SheetData parseOneSheet(Sheet sheet) throws SheetNotSupportedException {
        SheetData sheetData = new SheetData(sheet.getSheetName());
        int maxRows = 0;
        int maxCols = 0;
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell != null) {
                    if (cell.getCellType() == CellType.FORMULA) {
                        parseOneFormulaCell(sheetData, cell);
                    } else {
                        Ref dep = new RefImpl(cell.getRowIndex(), cell.getColumnIndex());
                        CellContent cellContent = new CellContent(getCellContentString(cell),
                                "", false);
                        sheetData.addContent(dep, cellContent);
                    }
                }
                if (cell.getColumnIndex() > maxCols) maxCols = cell.getColumnIndex();
            }
            if (row.getRowNum() > maxRows) maxRows = row.getRowNum();
        }
        return sheetData;
    }

    private String getCellContentString(Cell cell) {
        switch (cell.getCellType()) {
            case ERROR:
                return String.valueOf(cell.getErrorCellValue());
            case STRING:
                return cell.getStringCellValue();
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            default:
                return "";
        }
    }

    private void parseOneFormulaCell(SheetData sheetData, Cell cell) throws SheetNotSupportedException {
        Ptg[] tokens = this.getTokens(cell);
        Ref dep = new RefImpl(cell.getRowIndex(), cell.getColumnIndex());
        HashSet<Ref> precSet = new HashSet<>();
        if (tokens != null) {
            for (Ptg token : tokens) {
                if (token instanceof OperandPtg) {
                    Ref prec = parseOneToken(cell, (OperandPtg) token);
                    if (prec != null) precSet.add(prec);
                }
            }
        }
        if (!precSet.isEmpty()) {
            sheetData.addDeps(dep, precSet);
            CellContent cellContent = new CellContent("",
                    cell.getCellFormula(), true);
            sheetData.addContent(dep, cellContent);
        }
    }

    private Ref parseOneToken(Cell cell, OperandPtg token) throws SheetNotSupportedException {
        Sheet sheet = this.getDependentSheet(cell, token);
        if (sheet != null) {
            if (token instanceof Area2DPtgBase) {
                Area2DPtgBase ptg = (Area2DPtgBase) token;
                int rowStart = ptg.getFirstRow();
                int colStart = ptg.getFirstColumn();
                int rowEnd = ptg.getLastRow();
                int colEnd = ptg.getLastColumn();
                boolean validArea = true;
                for (int r = ptg.getFirstRow(); r <= ptg.getLastRow(); r++) {
                    for (int c = ptg.getFirstColumn(); c <= ptg.getLastColumn(); c++) {
                        Cell dep = this.getCellAt(sheet, r, c);
                        if (dep == null) validArea = false;
                    }
                }
                if (validArea) return new RefImpl(rowStart, colStart, rowEnd, colEnd);
            } else if (token instanceof RefPtg) {
                RefPtg ptg = (RefPtg) token;
                int row = ptg.getRow();
                int col = ptg.getColumn();
                Cell dep = this.getCellAt(sheet, row, col);
                if (dep != null) return new RefImpl(row, col, row, col);
            } else if (token instanceof Area3DPtg ||
                    token instanceof Area3DPxg ||
                    token instanceof Ref3DPtg ||
                    token instanceof Ref3DPxg) {
                throw new SheetNotSupportedException();
            }
        }

        return null;
    }

    private Sheet getDependentSheet (Cell src, OperandPtg opPtg) {
        Sheet sheet = null;
        if (opPtg instanceof RefPtg) {
            sheet = src.getSheet();
        } else if (opPtg instanceof Area2DPtgBase) {
            sheet = src.getSheet();
        } else if (opPtg instanceof Ref3DPtg) {
            sheet = this.workbook.getSheet(this.getSheetNameFrom3DRef((Ref3DPtg) opPtg));
        } else if (opPtg instanceof Area3DPtg) {
            sheet = this.workbook.getSheet(this.getSheetNameFrom3DRef((Area3DPtg) opPtg));
        }
        return sheet;
    }

    private String getSheetNameFrom3DRef (OperandPtg ptg) {
        String sheetName = null;
        if (ptg instanceof Ref3DPtg) {
            Ref3DPtg ptgRef3D = (Ref3DPtg) ptg;
            sheetName = ptgRef3D.toFormulaString((FormulaRenderingWorkbook) this.evalbook);
        } else if (ptg instanceof Area3DPtg) {
            Area3DPtg ptgArea3D = (Area3DPtg) ptg;
            sheetName = ptgArea3D.toFormulaString((FormulaRenderingWorkbook) this.evalbook);
        }
        return sheetName != null ? sheetName.substring(0, sheetName.indexOf('!')) : null;
    }

    private Cell getCellAt (Sheet sheet, int rowIdx, int colIdx) {
        Cell cell;
        try {
            cell = sheet.getRow(rowIdx).getCell(colIdx);
        } catch (NullPointerException e) {
            return null;
        }
        return cell;
    }

    private Ptg[] getTokens (Cell cell) {
        try {
            return FormulaParser.parse(
                    cell.getCellFormula(),
                    this.evalbook,
                    FormulaType.CELL,
                    this.workbook.getSheetIndex(cell.getSheet()),
                    cell.getRowIndex()
            );
        } catch (Exception e) { return null; }
    }
}