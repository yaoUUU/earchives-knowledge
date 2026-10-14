package cn.earchives.knowledge.service;

import cn.earchives.data.excel.buz.reader.ExcelReader;
import cn.earchives.data.excel.model.book.WorkBookData;
import cn.earchives.data.excel.model.cell.CellContent;
import cn.earchives.data.excel.model.sheet.SheetData;
import cn.earchives.knowledge.domain.KnowledgeParseResult;
import cn.earchives.knowledge.parser.KnowledgeTableParser;
import com.earchives.common.core.exception.ServiceException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** 项目文件读取、任务调度及知识库同步可直接复用该服务，无需 Node.js。 */
@Service
public class KnowledgeParseService {
    private static final int MAX_FILE_BYTES = 20 * 1024 * 1024;
    private final List<KnowledgeTableParser> parsers;

    public KnowledgeParseService(List<KnowledgeTableParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    public KnowledgeParseResult parseWorkbook(WorkBookData workbook) {
        if (workbook == null || workbook.getSheets() == null || workbook.getSheets().isEmpty()) {
            throw new ServiceException("工作簿不能为空");
        }
        for (SheetData sheet : workbook.getSheets().values()) {
            if (sheet == null || sheet.getCellData() == null) throw new ServiceException("工作表内容不能为空");
            for (Map<Integer, CellContent> row : sheet.getCellData().values()) {
                if (row == null) continue;
                for (CellContent cell : row.values()) {
                    if (cell != null && cell.getF() != null && !cell.getF().isBlank()) {
                        throw new ServiceException("当前解析器尚未验证公式结果，请先提供已确认的值版本");
                    }
                }
            }
        }
        List<KnowledgeTableParser> matched = parsers.stream().filter(p -> p.supports(workbook)).toList();
        if (matched.isEmpty()) throw new ServiceException("未找到匹配的模板解析器，当前仅支持模板安装检验批固定版式 v1");
        if (matched.size() != 1) throw new ServiceException("多个模板解析器匹配，请检查解析器配置");
        return matched.get(0).parse(workbook);
    }

    /** 不关闭调用方流。临时文件在成功或失败时均清理。 */
    public KnowledgeParseResult parseExcel(InputStream input) {
        Path temp = null;
        try {
            byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAX_FILE_BYTES) throw new ServiceException("Excel 文件不能为空且不能超过 20MB");
            temp = Files.createTempFile("earchives-knowledge-", ".xlsx");
            Files.write(temp, bytes);
            // 现有 ExcelReader 不处理公式；读取之前拦截，防止公式单元格静默丢失。
            try (InputStream check = Files.newInputStream(temp); XSSFWorkbook workbook = new XSSFWorkbook(check)) {
                for (Sheet sheet : workbook) for (Row row : sheet) for (Cell cell : row) {
                    if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR) {
                        throw new ServiceException("存在尚未支持的公式或错误单元格：" + sheet.getSheetName() + "!" + cell.getAddress());
                    }
                }
                if (!workbook.getAllPictures().isEmpty()) {
                    throw new ServiceException("文件含嵌入图片，当前版本仅提取单元格内容，请先单独处理图片");
                }
            }
            return parseWorkbook(ExcelReader.readMultSheet(temp.toString()));
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException("Excel 读取失败，请检查文件是否为有效且未加密的 xlsx");
        } finally {
            if (temp != null) {
                try { Files.deleteIfExists(temp); }
                catch (java.io.IOException ex) { temp.toFile().deleteOnExit(); }
            }
        }
    }
}
