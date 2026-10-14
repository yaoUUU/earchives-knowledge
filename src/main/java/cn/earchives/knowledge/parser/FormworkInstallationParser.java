package cn.earchives.knowledge.parser;

import cn.earchives.data.excel.model.book.WorkBookData;
import cn.earchives.data.excel.model.sheet.SheetData;
import cn.earchives.knowledge.domain.KnowledgeParseResult;
import com.earchives.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cn.earchives.knowledge.parser.SheetContent.*;

/** 模板安装检验批固定版式 v1；先校验表头/项目行，再解释业务字段。 */
@Component
public class FormworkInstallationParser implements KnowledgeTableParser {
    public static final String ID = "formwork-installation-v1";
    public static final String TITLE = "模板分项工程模板安装检验批质量验收记录";
    private static final String[] MAIN_ITEMS = {
        "模板及支架用材料", "现浇混凝土结构模板及支架的安装质量", "后浇带处模板及支架设置",
        "支架竖杆或竖向模板安装在土层上要求", "模板安装", "隔离剂的品种和涂刷方法", "模板起拱",
        "现浇混凝土结构多层连续支模", "预埋件和预留孔洞安装", "现浇结构模板安装", "预制构件模板安装"
    };
    private static final String[] DETAIL_ONE = {
        "梁", "板", "预埋板中心线位置（3mm）", "预埋管、预留孔中心线位置（3mm）",
        "中心线位置（5mm）", "外露长度（+10，0）mm", "中心线位置（2mm）", "外露长度（+10，0）mm",
        "中心线位置（10mm）", "尺寸（+10，0）mm", "轴线位置（5mm）", "底模上表面标高（±5mm）",
        "基础（±10mm）", "柱、墙、梁（±5mm）", "楼梯相邻踏步高差（5mm）", "层高≤6m（8mm）",
        "层高＞6m（10mm）", "相邻模板表面高差（2mm）", "表面平整度（5mm）",
        "梁、板（±4mm）", "薄腹梁、桁架（±8mm）", "柱（0，-10）mm", "墙板（0，-5）mm"
    };
    private static final String[] DETAIL_TWO = {
        "板、墙板（0，-5）mm", "梁、薄腹梁、桁架（+2，-5）mm", "板（+2，-3）mm", "墙板（0，-5）mm",
        "梁、薄腹梁、桁架、柱（+2，-5）mm", "梁、板、柱（L/1000且≤15mm）", "墙板、薄腹梁、桁架（L/1500且≤15mm）",
        "板的表面平整度（3mm）", "相邻模板表面高差（1mm）", "板（7mm）", "墙板（5mm）", "板、墙板（L/1500）", "薄腹梁、桁架、梁（±3mm）"
    };

    @Override public String id() { return ID; }

    @Override public boolean supports(WorkBookData workbook) {
        if (workbook == null || workbook.getSheets() == null) return false;
        return workbook.getSheets().values().stream().filter(Objects::nonNull)
            .anyMatch(s -> "主表".equals(s.getName()) && TITLE.equals(text(s, 1, 1)));
    }

    @Override public KnowledgeParseResult parse(WorkBookData workbook) {
        SheetData main = sheet(workbook, "主表");
        SheetData first = sheet(workbook, "检查记录-1");
        SheetData second = sheet(workbook, "检查记录-2");
        validateMain(main);
        String number = IntStream.of(18, 22, 23, 24).mapToObj(c -> text(main, 2, c))
            .collect(Collectors.joining()).replaceFirst("^编号[：:]", "");
        if (!number.matches("010203\\d{3}")) throw new ServiceException("检验批编号格式不匹配：" + number);
        String suffix = text(main, 2, 25);
        String fullNumber = number + suffix;
        List<String> warnings = new ArrayList<>();
        if (workbook.getSheets().size() != 3) {
            throw new ServiceException("本解析器仅支持主表及两张检查记录，共 3 张工作表");
        }
        Map<String, Object> info = map(
            "单位（子单位）工程名称", value(main, 4, 5), "分部（子分部）工程名称", value(main, 4, 15),
            "分项工程名称", value(main, 4, 23), "施工单位", value(main, 5, 5), "项目负责人", value(main, 5, 15),
            "检验批容量", value(main, 5, 23), "分包单位", value(main, 6, 5), "分包单位项目负责人", value(main, 6, 15),
            "检验批部位", value(main, 6, 23), "施工依据", value(main, 7, 5), "验收依据", value(main, 7, 15));
        List<Map<String, Object>> primary = new ArrayList<>();
        List<Map<String, Object>> general = new ArrayList<>();
        for (int r = 9; r <= 19; r++) {
            Map<String, Object> item = map("序号", Integer.parseInt(text(main, r, 2)), "验收项目", value(main, r, 3),
                "设计要求及规范规定", value(main, r, 11), "最小抽样数量", value(main, r, 15),
                "实际抽样数量", value(main, r, 17), "检查记录", cells(main, r, r, 18, 24),
                "检查结果", cells(main, r, r, 25, 26), "来源", source(main, r, 2));
            (r <= 12 ? primary : general).add(item);
        }
        List<Map<String, Object>> records = List.of(
            details(first, DETAIL_ONE, fullNumber), details(second, DETAIL_TWO, fullNumber));
        for (SheetData s : List.of(first, second)) {
            if (!Objects.equals(value(main, 4, 5), value(s, 4, 5))) {
                warnings.add("工程名称不一致：主表=" + value(main, 4, 5) + "；" + s.getName() + "=" + value(s, 4, 5));
            }
        }
        List<Map<String, Object>> extra = new ArrayList<>(cells(main, 7, 7, 27, 27));
        extra.addAll(cells(first, 30, 30, 1, 26));
        if (!extra.isEmpty()) warnings.add("存在未确认业务归属的单元格，已保留在其他原文中，请审核后入库");
        Map<String, Object> content = map("标题", TITLE, "编号", number, "编号后缀", suffix.isEmpty() ? null : suffix,
            "完整编号", fullNumber, "基本信息", info, "主控项目", primary, "一般项目", general,
            "施工单位检查结果", value(main, 20, 7), "监理单位验收结论", value(main, 24, 7),
            "建设单位验收结论", value(main, 27, 7), "检查记录", records,
            "签字及日期原文", List.of(
                map("区域", "施工单位", "单元格", cells(main, 20, 23, 16, 26)),
                map("区域", "监理单位", "单元格", cells(main, 24, 26, 16, 26)),
                map("区域", "建设单位", "单元格", cells(main, 27, 29, 16, 26)),
                map("区域", "检查记录-2", "单元格", cells(second, 23, 24, 1, 26))),
            "注释", value(second, 25, 1), "其他原文", extra);
        return new KnowledgeParseResult(ID, TITLE, number, content, List.copyOf(warnings));
    }

    private void validateMain(SheetData main) {
        require(main, 1, 1, TITLE);
        require(main, 4, 1, "单位（子单位）工程名称");
        require(main, 4, 11, "分部（子分部）工程名称");
        require(main, 4, 19, "分项工程名称");
        String[][] labels = {{"施工单位", "项目负责人", "检验批容量"}, {"分包单位", "分包单位项目负责人", "检验批部位"}};
        for (int r = 0; r < labels.length; r++) {
            for (int c = 0; c < 3; c++) require(main, r + 5, new int[]{1, 11, 19}[c], labels[r][c]);
        }
        require(main, 7, 1, "施工依据"); require(main, 7, 11, "验收依据");
        require(main, 8, 1, "验收项目"); require(main, 8, 11, "设计要求及规范规定");
        require(main, 8, 15, "最小/实际抽样数量"); require(main, 8, 18, "检查记录"); require(main, 8, 25, "检查结果");
        require(main, 9, 1, "主控项目"); require(main, 13, 1, "一般项目");
        require(main, 20, 1, "施工单位检查结果"); require(main, 24, 1, "监理单位验收结论"); require(main, 27, 1, "建设单位验收结论");
        for (int i = 0; i < MAIN_ITEMS.length; i++) {
            require(main, i + 9, 3, MAIN_ITEMS[i]);
            require(main, i + 9, 2, Integer.toString(i < 4 ? i + 1 : i - 3));
        }
    }

    private Map<String, Object> details(SheetData s, String[] expectedItems, String fullNumber) {
        require(s, 1, 1, "模板分项工程模板安装检验批质量验收的检查记录");
        require(s, 4, 1, "单位（子单位）工程名称"); require(s, 5, 1, "检验批名称");
        require(s, 5, 5, TITLE); require(s, 5, 14, "检验批编号"); require(s, 5, 18, fullNumber);
        require(s, 6, 1, "编号"); require(s, 6, 4, "验收项目"); require(s, 6, 11, "验收部位"); require(s, 6, 15, "检查原始记录");
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < expectedItems.length; i++) {
            int r = i + 7;
            String parent = mergedText(s, r, 4);
            String child = mergedText(s, r, 6);
            if (!expectedItems[i].equals(child)) {
                throw new ServiceException("模板检查明细不匹配：" + source(s, r, 6));
            }
            String clause = mergedText(s, r, 1);
            if (!clause.matches("第4\\.2\\.(7|9|10|11)条")) {
                throw new ServiceException("检查明细依据条款缺失或版式变化：" + source(s, r, 1));
            }
            String label = parent.isEmpty() || parent.equals(child) ? child : parent + " / " + child;
            items.add(map("依据条款", clause, "验收项目", label, "验收部位", cells(s, r, r, 11, 14),
                "检查原始记录", cells(s, r, r, 15, 24), "备注", cells(s, r, r, 25, 26), "来源", source(s, r, 1)));
        }
        return map("工作表", s.getName(), "标题", value(s, 1, 1), "单位（子单位）工程名称", value(s, 4, 5),
            "检验批名称", value(s, 5, 5), "检验批编号", fullNumber, "检查明细", items);
    }
}
