package cn.earchives.knowledge.parser;

import cn.earchives.data.excel.model.book.WorkBookData;
import cn.earchives.knowledge.domain.KnowledgeParseResult;

/** 每种已适配的模板实现一个解析器；未匹配模板不自动猜测字段。 */
public interface KnowledgeTableParser {
    String id();

    boolean supports(WorkBookData workbook);

    KnowledgeParseResult parse(WorkBookData workbook);
}
