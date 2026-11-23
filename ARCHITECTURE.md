# 架构说明（ARCHITECTURE）

## 分层

```
ui (Compose)            面板 / 属性详情 / 记忆草案队列 / 设置 / 首次引导
  ↑
presentation            ViewModel + UI State；只暴露粗类别与聚合结果
  ↑
domain (UseCase)         规则引擎、草案裁决、徽章判定、基线期判断
  ↑
data                    Repository → DAO / DataStore / 本地推理封装
  ↑
collector / aggregate   UsageEvents 采集；WorkManager 日聚合与周滑动窗口；长周期回顾双轨生成
  ↑
local storage           Room + SQLCipher（Keystore）；DataStore 配置
```

- 单向依赖：ui → domain → data → collector/storage；下层不反向持有 UI。
- 手写轻量 DI（`core/ServiceLocator`），不引入 Hilt 等额外容器。

## 核心数据流

1. **采集**：`UsageStatsManager` / `UsageEvents` 取前后台切换、时长、时间戳；解锁/亮屏事件在系统 API 稳定获取时纳入，否则列为可选。事件在 mapper 层映射为粗类别后写入 `RawUsageEvent`。可选插件（默认关闭、逐项授权）经独立 Worker / 监听服务写入 `PluginEvent`：通知元数据（包名/频道/时间戳）、日历（起止时间）、通话日志（方向/时长/日期，号码 SHA-256 截断摘要）、地理围栏（常去点 enter/leave 离散事件）；采集端先查开关与系统权限，关闭或未授权静默跳过。健康连接本版保留占位，未引入 SDK。
2. **聚合**：WorkManager 每日任务生成 `DailyAggregate`；周滑动窗口供规则引擎读取。前 7 天为基线期，面板显示“数据积累中”，不下结论。
3. **规则引擎**：纯 Kotlin、可单测；产出 `AttributeScore`（0–100、置信度、依据摘要、样本天数）。所有数值只在此层产生。
4. **模型层**：`InferenceEngine` 接口；默认 `TemplateFallbackEngine`；GGUF 引擎为可选实现。输入为聚合 JSON 片段；输出经 schema 校验后生成 `DraftMemory` / 面板叙述 / 状态标签。长周期回顾由 `ReviewGenerator` 复用同一引擎路由与「模型优先、模板兜底」双轨范式，按自然月/季/年窗汇编精简输入（窗标签、计数、截断要点，不含正文全文与原始事件），产出仍交草案裁决链路。接口另设 `chat(prompt)` 自由文本通道供本地聊天使用：GGUF 透传拼好的 ChatML 取原始回复（不经 JSON schema 解析），模板引擎返回中性兜底语；聊天历史由 `ChatPromptBuilder` 按 token 估算预算截断，预留输出、系统提示、记忆背景与安全余量，限制最近消息条数，只在完整轮次边界丢弃早期对话。已采纳的聊天要点作为背景附在 system 段；「整理记忆」另走提炼提示词回放最近对话，模型输出 `memories` JSON，经同一人读校验过滤后入候选草案，本人采纳后才写入已采纳要点表并参与后续注入。聊天另接可选远程引擎：`model/remote/` 下 `RemoteChatConfig`（Base URL/Key/模型名三项齐全才可调用）与 `RemoteChatClient`（JDK `HttpURLConnection`，OpenAI 兼容 `/chat/completions`，手写 JSON、`MiniJson` 解析）；`ChatViewModel` 远程优先，调用失败/空回复回退本地 GGUF，再空落模板兜底。API Key 经 Keystore AES-GCM 包装存 DataStore 密文；面板回顾等其他通道始终只走本地模型。
5. **草案裁决**：`DraftMemory` 状态机 `PENDING → CONFIRMED / MODIFIED / REJECTED`。入库按类型分流：仅 `MEMORY` 长期记忆在 CONFIRMED/MODIFIED 后写入 `ConfirmedMemory`；`STATUS_TAG` 与 `NARRATIVE` 只在草案表置已确认状态，不沉淀进正式记忆表。默认走人工队列；队列页可切换 `autoConfirmEnabled`（默认 true），`InferenceWorker` 仅在 `ModelStatus.SUCCESS` + 开关开启 + 输出校验通过 + 非重复时，由 `DraftRepository.autoSettle` 裁决；查重或单条异常时该条回落 PENDING，不写半成品。正式记忆带 `source` 来源列（`manual` 人工 / `auto_model` 自动），可按 id 撤销（仅删正式记忆行，不回退草案）。另有本人主动记录的手写片段：不经草案状态机，由 `ManualFragmentRepository` 做非空/长度校验、同正文去重与类别归一后直写独立表 `manual_fragments`（`mood?`/`category?`/`anchorEpochMs?` 可选）。
6. **面板**：读取 `AttributeScore`、`ConfirmedMemory`、`Badge`、状态标签渲染；不读取原始事件流。
7. **降级提示**：`FALLBACK_TEMPLATE`/`TIMEOUT`/`ERROR` 不触发自动确认，草案回落 PENDING；Worker 置 `fallbackNoticePending` 标记，`PanelViewModel.refresh()` 读取后在主界面弹窗「本次为兜底回顾」，用户关闭（前往队列或知道了）时清除标记，不反复弹。
8. **本地导出**：`ExportRepository` 只读已确认记忆、状态标签、回顾文案（`narratives`）、规则分值、徽章、手写片段与里程碑（原始事件按勾选纳入），经手写序列化器生成带 `schemaVersion` 的明文 JSON，由 SAF 写入用户选定的本地文件；不联网、不写剪贴板，待确认/已丢弃草案与推理日志不导出。聊天另有独立导出通道：`ChatExportRepository` 把**当前会话**消息与全局已采纳要点组 `ChatExportBundle`（携带会话标题快照），经 `ChatExporter` 手写序列化为独立 JSON（候选不导出、不含分值/包名/联系人），不并入综合导出包与加密备份。聊天独立导入通道：`ChatImportRepository` 经 SAF 读 JSON → `ChatImportParser` 校验 `schemaVersion` 与字段类型 → 新建会话承载消息、要点按正文去重还原为全局已采纳；非法 role 落 USER，缺时间戳落 0。聊天页二次确认后「清空当前」只删当前会话消息，全局要点（候选 + 已采纳）保留。
9. **本地导入**：`ImportRepository` 经 SAF `OpenDocument` 读取用户选定的本地 JSON → 手写 `MiniJson` 递归下降解析 → `ImportParser` 校验根节点、`schemaVersion`（当前为 1）与各字段类型 → 各节按指纹查重后入库；策略 `SKIP_DUPLICATES`（默认）/`OVERWRITE`/`APPEND_ALL` 可在队列页切换，最近一次导入结果（消息/是否错误/时间）持久化到 DataStore 长期回显。标签回写为 CONFIRMED 状态标签草案；回顾文案回写为 CONFIRMED 叙述草案；片段按正文查重；待确认/已丢弃草案与推理日志不导入。导入与导出共用同一套 DTO 与映射层。
10. **加密备份**：`BackupRepository` 复用导出组包与 JSON 序列化生成明文载荷，再由 `BackupCipher` 用用户口令经 PBKDF2-HMAC-SHA256（独立随机盐、150k 迭代）派生 AES-256 密钥，AES-GCM 加密为自描述二进制容器后经 SAF 写本地文件；恢复时读文件 → 口令解密与 GCM 校验 → 复用 `ImportParser` 解析与 `ImportRepository.persist` 查重入库。备份不携带 SQLCipher 口令与 Keystore 条目，新设备自行生成库密钥包装；口令遗忘不可恢复、无云端找回。`BackupCipher` 仅用 JDK 密码学 API，可纯 JVM 覆盖往返、错误口令与篡改路径。

## 六维属性与依据

| 属性 | 主要输入（本地元数据） | 依据要素 |
|---|---|---|
| 专注 FOCUS | 连续前台块时长、中断次数、碎片切换频率 | 最长连续块、中断计数、切换密度 |
| 规律 REGULARITY | 睡眠窗口方差、首次解锁时间方差、解锁次数稳定性 | 窗口离散度、首日样本 |
| 活力 VITALITY | 活动时段、久坐块、步数（Health Connect 可选） | 时段分布、久坐时长 |
| 社交 SOCIAL | 社交类别使用节律、通话时长/频率（可选） | 节律分布；不记联系人 |
| 探索 EXPLORATION | 外出事件、日历外出事件（可选） | 事件计数与类别 |
| 数字节制 MODERATION | 深夜使用占比、解锁频率、单次时长分布 | 深夜占比、解锁密度、长尾分布 |

每维附带：分值、置信度（样本天数/覆盖不足时下降）、人话依据摘要。

## 模型输出 schema（校验拒绝数值字段与诊断结论）

```json
{
  "draftMemories": [{ "kind": "memory", "text": "草案文案" }],
  "panelNarrative": "面板叙述草案",
  "statusTags": ["标签一", "标签二"]
}
```

- 校验失败、超时、内存不足、引擎不可用：降级为规则模板文案，`ModelRunLog.status` 记为 `FALLBACK_TEMPLATE / TIMEOUT / ERROR`；此类产出不触发自动确认、回落 PENDING，并置降级待提示标记，由主界面弹窗提示一次。
- 模型不得回写 `AttributeScore`，不得输出人格类型或诊断性表述。
- 字段正文须为自然中文：回吐输入数据字典（`{}`、输入键名、snake_case 键值结构）、回填 schema 字段名或 Prompt 示例占位词的输出判不合格；tags/memories 逐条过滤污染条目，叙述不合格则整段回退模板兜底，原始数据不得落库。

## 数据表

见 [`docs/PLAN.md`](docs/PLAN.md) §4：`RawUsageEvent`、`DailyAggregate`、`AttributeScore`、`DraftMemory`、`ConfirmedMemory`（含 `source` 来源列）、`Badge`、`ModelRunLog`、`PluginEvent`、`ManualFragment`（手写片段，Room 1→2 迁移建表）、`Milestone`（里程碑，Room 2→3 迁移建表）、`ChatMessage`（聊天消息，Room 4→5 迁移建表，独立于草案与正式记忆；Room 6→7 补 `sessionId` 归属列）、`ChatMemoryDraft`（聊天记忆候选，Room 5→6 迁移建表）、`ChatMemory`（已采纳聊天要点，Room 5→6 迁移建表，全局共享不归属会话）、`ChatSession`（聊天会话容器，Room 6→7 迁移建表，只组织消息；删除会话级联删其消息，不删全局要点）。数据库当前 version 7：`MIGRATION_1_2`（片段表）、`MIGRATION_2_3`（里程碑表）、`MIGRATION_3_4`（正式记忆补 `source` 列，旧行默认 `manual`）、`MIGRATION_4_5`（聊天消息表）、`MIGRATION_5_6`（聊天记忆候选表与已采纳要点表）、`MIGRATION_6_7`（聊天会话表、消息补 `sessionId` 列并把既有消息归入默认会话）。当前选中会话 id 存 DataStore（可空，null 清除标记），启动时校验会话仍存在，否则回退默认会话。

## 可测试边界

- 纯 JVM 单测（`app/src/test`）：规则引擎、类别映射、时间工具、JSON 输出解析、模板降级措辞、Prompt/ChatML 构造、快照哈希、模型文件与内存门槛校验、草案状态机、插件目录、设置快照默认值、导出 JSON 序列化与实体映射、导入 JSON 解析与 schema 校验、三策略查重计数与入库映射、自动确认裁决（开关关闭留 PENDING、开启后非重复条目入库、重复标签/记忆回落 PENDING、查重异常该条回落不崩溃）、自动确认与降级待提示标记的快照默认值、失效页备份提示文案（有/无备份两分支与时间格式）、手写片段仓储（新增入库、空正文拒绝、重复跳过、类别/心情归一、限长）、片段导出映射/序列化可空字段/解析与导入查重计数、长周期自然窗边界与标签（月/季/年、历史偏移、半开区间）、回顾窗汇编（空窗/类别汇总/要点去重截断/窗标签）、回顾双轨生成（SUCCESS 透传、校验异常与 OOM 回退记 ERROR、模板引擎标 FALLBACK、输入携带窗标签/计数/要点）、加密备份容器往返与校验（含中文/空/二进制载荷、错误口令、密文篡改、魔数不符、截断、随机盐 IV、空口令拒绝）、备份仓储解密解析五节、错误口令与非备份二进制拒绝、里程碑仓储（归一存储、空标题拒绝、同标题去重、空白标签/说明落 null、限长截断）与导入里程碑查重跳过/入库映射；本轮新增：来源标注（人工/自动）写入与撤销按 id 删除、标签/叙述确认不写正式记忆表、仅记忆写入正式记忆、回顾文案导出映射与导入重建、已确认标签/叙述只读查询、自动确认仅记忆条目带来源入库。聊天会话仓储（已有会话直接取用、空库建默认会话、空白标题落默认、标题限长截断、空白重命名不生效、删除会话级联消息）、聊天导入解析（消息/要点/会话标题解析、非法 role 落 USER、缺时间戳落 0、空数组、缺 schemaVersion/版本不符/缺 content/坏 JSON 拒绝）、聊天导入入库（新建会话写消息、空正文跳过、要点按正文去重、标题缺失落默认「导入会话」）。远程客户端：请求体模型/消息/转义、响应取 `choices[0].message.content`、缺内容抛错、配置完整性与端点拼接；围栏常去点 JSON 往返解析与默认半径；插件目录实现状态与快照远程/围栏默认值断言。
- Instrumented 测试（`app/src/androidTest`，跑在已连接设备）：队列 ViewModel 用进程内非加密内存库贯通 DAO → 仓储 → ViewModel state，共 9 例——里程碑新增/同标题去重/删除、片段新增/同正文去重/删除、草案确认入正式记忆/丢弃不写正式记忆/修改后以编辑文本入库、自动确认开关默认开启且切换回写设置。测试经 `AppDatabase.inMemory` 建内存库、`SettingsAccess` 注入 fake 设置，不触碰真库、Keystore、DataStore 与 WorkManager；新增测试依赖仅 `androidx.test:runner/core`、ext-junit、espresso 与 compose-test，均为 androidTest/debug 配置，不进入 release 运行时。另有一条破坏性本机往返测试 `BackupRoundTripInstrumentedTest`（备份 → 物理擦除 → 凭口令本机恢复，六类业务数据计数与关键内容还原核对），直接操作真库/Keystore/DataStore 并擦除本机数据，因此默认 `@Ignore`，不进入常规全量上机套件，仅手动复核时移除注解单跑；第二台设备跨设备换机回归不在覆盖内。
- 加密包装（Keystore + SQLCipher）依赖 Android 运行时，不在纯 JVM 覆盖；由真机强杀冷启动验证解密与开库。失效时旧库无法抢救打开，失效页仅从 DataStore 回显上次明文导出时间与可恢复性说明，不提供导出按钮，二次确认后物理擦除重来。
- 清单红线由 `tools/check_manifest.ps1` 校验源码与合并后清单。
- 队列 ViewModel 交互层 instrumented 测试已补齐（见上条，9 例真机通过）；整屏 Compose 渲染断言（表单展开/卡片点按/区块可见性）尚未补齐，列为后续项。当前整屏交互以真机复核为准。自动确认真机链路已复核（SUCCESS 产出自动入库、未进待确认队列）；降级弹窗因真机本次为 SUCCESS 未触发，其标记读写随 instrumented 测试一并复核。手写片段队列页已真机复测：滚动到底不再崩溃（跨表 LazyColumn key 已加 `frag-`/`draft-`/`mem-` 命名空间前缀，避免自增主键撞键）、「记一笔」表单固定顶部展开即见、返回键回到主面板（`BackHandler` 拦截，表单展开时先收表单）；片段仓储增删已由队列 ViewModel instrumented 测试覆盖；正文录入保存的整屏点按与导出 JSON 片段节贯通仍留整屏渲染测试与人工上机复核。Room 1→2 迁移已由真机冷启动验证开库解密成功、既有数据保留。长周期回顾页已真机复测：月/季/年切换与上一窗偏移标签正确，生成一次走模型 SUCCESS，回顾文案/状态标签/记忆要点进入待确认队列，无 FATAL/重复 key/signal 9/OOM，返回面板正常；回顾页整屏 instrumented UI 测试随后续 Compose 渲染测试扩展。加密备份的「备份 → 擦除 → 凭口令本机恢复」往返已由 #27 破坏性 instrumented 测试在真机跑通（六类业务数据计数与关键内容还原核对通过）；第二台设备换机回归本期挂起，待后续真机齐备后补；SAF 文件选择与口令录入以人工上机为准。里程碑（#22）Room 2→3 迁移已由真机覆盖安装后冷启动验证开库解密成功、既有等级与数据保留，队列页导出/备份/插件区块渲染无崩溃；里程碑仓储增删已由队列 ViewModel instrumented 测试覆盖；表单新建/删除与标签卡片的整屏点按贯通随后续 Compose 渲染测试；时间锚点文本框与里程碑关联片段选择控件已随 #57 落地，记忆本独立浏览专区已随 #56 上线（只读分区列示记忆/标签/叙述/片段/里程碑）。
