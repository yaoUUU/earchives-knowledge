package cn.earchives.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.earchives.knowledge.domain.KnowledgeParseResult;
import cn.earchives.knowledge.service.KnowledgeParseService;
import com.earchives.common.core.domain.R;
import com.earchives.common.core.exception.ServiceException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** 上传文件解析入口；不接受服务端路径或任意文件 URL。 */
@RestController
@RequestMapping("/knowledge/tables")
public class KnowledgeTableController {
    private final KnowledgeParseService parseService;

    public KnowledgeTableController(KnowledgeParseService parseService) {
        this.parseService = parseService;
    }

    @SaCheckPermission("knowledge:table:parse")
    @PostMapping(value = "/parse", consumes = "multipart/form-data")
    public R<KnowledgeParseResult> parse(@RequestParam("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        if (file.isEmpty() || name == null || !name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new ServiceException("请上传非空的 xlsx 文件");
        }
        try (InputStream input = file.getInputStream()) {
            return R.ok(parseService.parseExcel(input));
        }
    }
}
