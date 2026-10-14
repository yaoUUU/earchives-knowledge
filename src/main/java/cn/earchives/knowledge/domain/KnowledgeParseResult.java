package cn.earchives.knowledge.domain;

import java.util.List;
import java.util.Map;

/** 业务内容与解析警告；调用方应审核 warnings 后决定是否入库。 */
public record KnowledgeParseResult(String parserId, String title, String number,
                                   Map<String, Object> content, List<String> warnings) {
}
