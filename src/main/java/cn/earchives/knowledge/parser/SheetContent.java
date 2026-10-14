package cn.earchives.knowledge.parser;

import cn.earchives.data.excel.model.book.WorkBookData;
import cn.earchives.data.excel.model.cell.CellContent;
import cn.earchives.data.excel.model.sheet.MergeData;
import cn.earchives.data.excel.model.sheet.SheetData;
import com.earchives.common.core.exception.ServiceException;
import org.apache.poi.ss.util.CellReference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 行列从 0 开始；合并区域只用于确定归属，不修改原始单元格。 */
public final class SheetContent {
    private SheetContent() { }

    public static SheetData sheet(WorkBookData book, String name) {
        if (book == null || book.getSheets() == null) {
            throw new ServiceException("工作簿为空");
        }
        List<SheetData> found = book.getSheets().values().stream()
            .filter(Objects::nonNull).filter(s -> name.equals(s.getName())).toList();
        if (found.size() != 1) {
            throw new ServiceException("需要唯一的工作表：" + name);
        }
        return found.get(0);
    }

    public static Object value(SheetData sheet, int row, int col) {
        if (sheet.getCellData() == null || sheet.getCellData().get(row) == null) return null;
        CellContent cell = sheet.getCellData().get(row).get(col);
        if (cell == null) return null;
        Object raw = cell.getV();
        if (raw == null && cell.getP() != null && cell.getP().getBody() != null) {
            raw = cell.getP().getBody().getDataStream();
        }
        if (raw instanceof String text) {
            text = text.replace("\r\n", "\n").replace('\r', '\n').strip();
            return text.isEmpty() ? null : text;
        }
        return raw;
    }

    public static String text(SheetData sheet, int row, int col) {
        Object v = value(sheet, row, col);
        if (v instanceof Number) return new java.math.BigDecimal(v.toString()).stripTrailingZeros().toPlainString();
        return v == null ? "" : v.toString().replace("\n", "").strip();
    }

    public static Object mergedValue(SheetData sheet, int row, int col) {
        if (sheet.getMergeData() != null) {
            for (MergeData m : sheet.getMergeData()) {
                if (row >= m.getStartRow() && row <= m.getEndRow()
                    && col >= m.getStartColumn() && col <= m.getEndColumn()) {
                    return value(sheet, m.getStartRow(), m.getStartColumn());
                }
            }
        }
        return value(sheet, row, col);
    }

    public static String mergedText(SheetData sheet, int row, int col) {
        Object v = mergedValue(sheet, row, col);
        return v == null ? "" : v.toString().replace("\n", "").strip();
    }

    public static void require(SheetData sheet, int row, int col, String expected) {
        if (!expected.equals(text(sheet, row, col))) {
            throw new ServiceException("模板结构不匹配：" + source(sheet, row, col) + " 应为「" + expected + "」");
        }
    }

    /** 保留范围内所有实际填写值及坐标，避免只读第一个格子丢失实测点。 */
    public static List<Map<String, Object>> cells(SheetData sheet, int firstRow, int lastRow, int firstCol, int lastCol) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int r = firstRow; r <= lastRow; r++) {
            for (int c = firstCol; c <= lastCol; c++) {
                Object v = value(sheet, r, c);
                if (v != null) result.add(map("来源", source(sheet, r, c), "内容", v));
            }
        }
        return result;
    }

    public static String source(SheetData sheet, int row, int col) {
        return sheet.getName() + "!" + new CellReference(row, col).formatAsString();
    }

    public static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }
}
