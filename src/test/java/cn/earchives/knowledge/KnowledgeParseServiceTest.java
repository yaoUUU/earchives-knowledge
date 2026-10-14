package cn.earchives.knowledge;

import cn.earchives.data.excel.model.book.WorkBookData;
import cn.earchives.data.excel.model.cell.CellContent;
import cn.earchives.knowledge.domain.KnowledgeParseResult;
import cn.earchives.knowledge.parser.FormworkInstallationParser;
import cn.earchives.knowledge.parser.SheetContent;
import cn.earchives.knowledge.service.KnowledgeParseService;
import com.earchives.common.core.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeParseServiceTest {
    private final KnowledgeParseService service = new KnowledgeParseService(List.of(new FormworkInstallationParser()));

    private WorkBookData fixture() throws Exception {
        try (var in = getClass().getResourceAsStream("/formwork-template.json")) {
            return new ObjectMapper().readValue(in, WorkBookData.class);
        }
    }

    private void set(WorkBookData w, String sheet, int row, int col, Object value) {
        CellContent cell = new CellContent(); cell.setV(value);
        cell.setT(value instanceof Number ? 2 : 1);
        SheetContent.sheet(w, sheet).getCellData().computeIfAbsent(row, r -> new HashMap<>()).put(col, cell);
    }

    @Test void parsesAllThreeSheetsAndKeepsLeadingZero() throws Exception {
        KnowledgeParseResult r = service.parseWorkbook(fixture());
        assertEquals("010203001", r.number());
        assertEquals("010203001（1）", r.content().get("完整编号"));
        assertEquals(4, ((List<?>) r.content().get("主控项目")).size());
        assertEquals(7, ((List<?>) r.content().get("一般项目")).size());
        List<?> records = (List<?>) r.content().get("检查记录");
        assertEquals(23, ((List<?>) ((Map<?, ?>) records.get(0)).get("检查明细")).size());
        assertEquals(13, ((List<?>) ((Map<?, ?>) records.get(1)).get("检查明细")).size());
        assertTrue(r.warnings().isEmpty());
    }

    @Test void readsFilledConclusionsAndAllMeasurementCells() throws Exception {
        WorkBookData w = fixture();
        set(w, "主表", 20, 7, "检查合格"); set(w, "主表", 24, 7, "同意验收");
        set(w, "检查记录-1", 7, 15, 0); set(w, "检查记录-1", 7, 17, -3);
        KnowledgeParseResult r = service.parseWorkbook(w);
        assertEquals("检查合格", r.content().get("施工单位检查结果"));
        assertEquals("同意验收", r.content().get("监理单位验收结论"));
        assertNull(r.content().get("建设单位验收结论"));
        Map<?, ?> record = (Map<?, ?>) ((List<?>) r.content().get("检查记录")).get(0);
        Map<?, ?> item = (Map<?, ?>) ((List<?>) record.get("检查明细")).get(0);
        List<?> values = (List<?>) item.get("检查原始记录");
        assertEquals(2, values.size());
        assertEquals(0, ((Map<?, ?>) values.get(0)).get("内容"));
        assertEquals(-3, ((Map<?, ?>) values.get(1)).get("内容"));
    }

    @Test void calculatesWarningsFromActualProjectNames() throws Exception {
        WorkBookData w = fixture(); set(w, "主表", 4, 5, "新工程名称");
        KnowledgeParseResult r = service.parseWorkbook(w);
        assertEquals(2, r.warnings().size());
        assertTrue(r.warnings().get(0).contains("新工程名称"));
        assertFalse(r.warnings().get(0).contains("AAA建材"));
    }

    @Test void rejectsShiftedRowsAndMismatchedNumber() throws Exception {
        WorkBookData w = fixture(); set(w, "主表", 9, 3, "其他检查项");
        assertThrows(ServiceException.class, () -> service.parseWorkbook(w));
        WorkBookData number = fixture(); set(number, "检查记录-1", 5, 18, "010203002（1）");
        assertThrows(ServiceException.class, () -> service.parseWorkbook(number));
        WorkBookData detail = fixture(); set(detail, "检查记录-2", 9, 6, "新插入的行");
        assertThrows(ServiceException.class, () -> service.parseWorkbook(detail));
    }

    @Test void rejectsUnknownTemplateMissingSheetAndFormulaJson() throws Exception {
        WorkBookData unknown = fixture(); set(unknown, "主表", 1, 1, "其他验收表");
        assertThrows(ServiceException.class, () -> service.parseWorkbook(unknown));
        WorkBookData missing = fixture(); missing.getSheets().remove("SheetData2");
        assertThrows(ServiceException.class, () -> service.parseWorkbook(missing));
        WorkBookData formula = fixture(); formula.getSheets().get("SheetData0").getCellData().get(4).get(5).setF("=1+1");
        assertThrows(ServiceException.class, () -> service.parseWorkbook(formula));
    }

    @Test void findsSheetByNameInsteadOfGeneratedId() throws Exception {
        WorkBookData w = fixture(); var main = w.getSheets().remove("SheetData0"); w.getSheets().put("another-id", main);
        assertEquals("010203001", service.parseWorkbook(w).number());
    }

    @Test void acceptsNumericSequenceCellsWithoutAddingDecimalSuffixes() throws Exception {
        WorkBookData w = fixture();
        set(w, "主表", 2, 22, 0.0); set(w, "主表", 2, 23, 0.0); set(w, "主表", 2, 24, 1.0);
        set(w, "主表", 9, 2, 1.0);
        assertEquals("010203001", service.parseWorkbook(w).number());
    }

    @Test void rejectsFormulaBeforeLegacyReaderCanDropIt() throws Exception {
        try (XSSFWorkbook w = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            w.createSheet("主表").createRow(0).createCell(0).setCellFormula("1+1");
            w.write(out);
            ServiceException ex = assertThrows(ServiceException.class, () -> service.parseExcel(new ByteArrayInputStream(out.toByteArray())));
            assertTrue(ex.getMessage().contains("公式"));
        }
    }

    @Test void readsActualXlsxThroughExistingReader() throws Exception {
        WorkBookData input = fixture();
        try (XSSFWorkbook w = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String name : List.of("主表", "检查记录-1", "检查记录-2")) {
                var model = SheetContent.sheet(input, name); var s = w.createSheet(name);
                int lastRow = model.getCellData().keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
                for (int r = 0; r <= lastRow; r++) {
                    var row = s.createRow(r);
                    for (int c = 0; c <= 27; c++) {
                        var cell = row.createCell(c); Object value = SheetContent.value(model, r, c);
                        if (value != null) cell.setCellValue(value.toString());
                    }
                }
                for (var m : model.getMergeData()) s.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(
                    m.getStartRow(), m.getEndRow(), m.getStartColumn(), m.getEndColumn()));
            }
            w.write(out);
            assertEquals("010203001", service.parseExcel(new ByteArrayInputStream(out.toByteArray())).number());
        }
    }
}
