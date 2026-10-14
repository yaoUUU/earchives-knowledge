# earchives-knowledge 档案知识库模块

将表格读取和业务字段解析封装为 Spring 服务，供后续项目资料同步、WeKnora 接入复用。
已加入 `earchives-modules` Maven 聚合，并由 `earchives-admin` 引入，应用原有组件扫描会加载模块。

## 当前能力

- 上传 `.xlsx`，复用 `earchives-data` 的 `ExcelReader` / `WorkBookData` / `SheetData` / `CellContent`。
- 直接解析平台已存储的 `WorkBookData`，不必先导出 Excel。
- 纯 Java 的模板业务解析，不再运行 Node.js 或外部脚本。
- 初始解析器 `formwork-installation-v1`：模板安装检验批主表和两张检查记录。
- 中文业务 JSON：标题、编号、工程信息、4 项主控项目、7 项一般项目、36 条检查明细、实际填写的结论、签字日期原文和来源单元格。
- 实测区域保留全部非空单元格和坐标；零值保留；工程名称差异动态返回 warnings；未确认业务归属的内容单列。
- 未匹配模板、表头/明细行变化、页间编号不一致、公式或错误单元格不会静默生成业务结果。

原 `script/excel-json` 是本次示例的可复现脚本，保留不变。本模块不是任意表格通用识别器。

## HTTP 调用

`POST /knowledge/tables/parse`，`multipart/form-data`，文件字段 `file`。
复用原有 Sa-Token 登录校验，携带平台 `Authorization` 和 `clientid` 请求头。
需给调用角色配置权限 `knowledge:table:parse`（本次未自动修改菜单或角色数据）。

```bash
curl -X POST 'http://localhost:10010/knowledge/tables/parse' \
  -H 'Authorization: <平台登录凭据>' \
  -H 'clientid: <客户端ID>' \
  -F 'file=@example.xlsx'
```

返回 `R<KnowledgeParseResult>`，业务 JSON 位于 `data.content`。
`data.parserId` 标识规则版本，`data.warnings` 标识需审核的信息。
文件读取上限 20MB，同时受应用已有 multipart 上传限制约束。
接口仅处理用户本次上传内容，不读取任意服务端路径/URL或数据库项目文件。

## 内部服务调用

```java
// 注入 KnowledgeParseService
KnowledgeParseResult result = knowledgeParseService.parseWorkbook(workBookData);
// 或使用经过调用方权限校验取得的文件流
KnowledgeParseResult result = knowledgeParseService.parseExcel(inputStream);
```

调用方负责关闭输入流。服务清理内部临时文件。空结论为 null，检查值区域为空时为 []。
工程访问权限仍由项目业务入口判断，不能以获得知识库编号替代授权。

## 新增模板

实现 `KnowledgeTableParser` 并标注 `@Component`：

1. `id()` 返回稳定的模板与规则版本标识。
2. `supports()` 判断模板身份。
3. `parse()` 在读取业务字段之前校验版式，返回 `KnowledgeParseResult`。

通过 `SheetContent` 读取普通/富文本单元格、合并单元格归属和来源范围。
当前规则校验多个关键表头、全部明细子项目和页间编号；不保证识别所有合并区域变更，需要对新模板版本补充样例测试。
保留字段中的换行，不根据标准条款自行计算合格结论。

## 后续 WeKnora 接入位置

本次没有建立 WeKnora 连接、同步任务或自动上传行为。建议后续在本模块增加：

1. 项目文件来源适配：经项目权限校验，读取最新表格 JSON 或对象存储文件。
2. 同步任务及映射表：工程 ID → 知识库 ID、表格 ID＋版本 → 文档 ID；保存重试状态。
3. WeKnora 客户端：服务端保管凭据，对解析结果做知识文档转换，再上传/更新/删除。
4. 在表格保存事务提交后登记任务；解析不匹配或存在需审核内容时不自动发布。

模块只依赖数据模块，避免反向依赖工程模块形成循环；后续可在应用层订阅项目事件。

## 已知范围

- 现有读取器不完整支持公式、图片和复杂 Excel 特性；上传路径遇到公式、错误单元格、嵌入图片会拒绝解析。
- 直接传入 `WorkBookData` 时调用方须确保图片/绘图已单独处理；当前业务解析仅覆盖单元格内容。
- JSON 中签名文字只代表单元格原文，不表示签章有效性或审批状态。
- warnings 需要调用方处理，不能将成功返回直接当作自动发布许可。

## 测试

```bash
mvn -pl earchives-modules/earchives-knowledge -am test -DskipTests=false -Dtest=KnowledgeParseServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

测试使用去掉示例业务数据的模板结构 fixture，覆盖内容更新、零值、多点实测、合并归属、未知模板、版式漂移、编号不一致、公式拒绝及 xlsx 读取链路。
