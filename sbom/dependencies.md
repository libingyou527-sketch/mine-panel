# 依赖清单与审计（SBOM）

> 审计口径：**运行时**是否发起网络请求、是否含遥测/崩溃上报/广告/归因、是否读取敏感内容。构建期从 Maven 拉取依赖不视为运行时网络能力。

## 运行时依赖（app implementation）

| 依赖 | 用途 | 运行时网络 | 遥测/广告 | 敏感内容读取 | 结论 |
|---|---|---|---|---|---|
| androidx.core:core-ktx | 系统兼容与扩展 | 否 | 否 | 否 | 通过 |
| androidx.activity:activity-compose | Compose Activity 集成 | 否 | 否 | 否 | 通过 |
| androidx.compose:compose-bom / ui / material3 | 本地 UI 渲染 | 否 | 否 | 否 | 通过 |
| androidx.lifecycle:lifecycle-viewmodel-compose / runtime-compose | 视图层生命周期 | 否 | 否 | 否 | 通过 |
| androidx.room:room-runtime / room-ktx | 本地 SQLite 封装 | 否 | 否 | 否 | 通过 |
| androidx.work:work-runtime-ktx | 本地后台任务调度 | 否 | 否 | 否 | 通过 |
| androidx.datastore:datastore-preferences | 本地键值配置 | 否 | 否 | 否 | 通过 |
| net.zetetic:sqlcipher-android | 本地数据库加密 | 否 | 否 | 否 | 通过 |
| com.squareup.okio:okio-jvm（DataStore 传递） | 本地文件/字节缓冲 IO | 否 | 否 | 否 | 通过；不含 HTTP 客户端 |
| com.google.guava:listenablefuture（Core 传递） | 仅 Future 回调接口 | 否 | 否 | 否 | 通过 |

> v0.2.0 远程聊天通道**不引入任何第三方网络库**：HTTP 请求由 JDK 内置 `java.net.HttpURLConnection` 手写，请求体手写 JSON、响应经既有 `MiniJson` 解析；未引入 OkHttp / Retrofit / gRPC / Ktor 等。该通道仅在用户显式启用外部模型且配置完整时调用。

## 编译期 / 注解处理

| 依赖 | 用途 | 说明 |
|---|---|---|
| com.android.application (AGP) | 构建插件 | 构建期使用，不打入 APK |
| org.jetbrains.kotlin.android / compose | Kotlin 与 Compose 编译 | 构建期使用 |
| com.google.devtools.ksp | Room 注解处理 | 构建期使用 |

## 测试依赖

| 依赖 | 用途 |
|---|---|
| junit:junit | JVM 单元测试 |
| io.mockk:mockk | 模拟依赖 |
| androidx.room:room-testing | Room 测试 |
| androidx.test.ext:junit / espresso / compose ui-test | instrumentation 与 UI 测试 |

## 传递权限处置（阶段 0 已核验，APK 实测）

| 权限 | 来源 | 处置 | 说明 |
|---|---|---|---|
| `ACCESS_NETWORK_STATE` | work-runtime 2.10.0 传递 | **已 `tools:node="remove"` 移除** | 红线权限；本应用不读取网络状态 |
| `RECEIVE_BOOT_COMPLETED` | work-runtime 2.10.0 传递 | **已 `tools:node="remove"` 移除** | MVP 不做开机自启/开机恢复；重启后任务由下次应用启动时恢复 |
| `WAKE_LOCK` | work-runtime 2.10.0 传递 | 保留 | 后台任务本地执行期间持有 CPU 唤醒锁，不联网；无网络能力 |
| `app.minepanel.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | androidx 生成的自定义权限 | 保留 | 运行时广播隔离用途，非系统权限 |

> 阶段 0 实测 APK 权限清单：`PACKAGE_USAGE_STATS`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC`、`POST_NOTIFICATIONS`、`WAKE_LOCK`、上述自定义权限；**无 `ACCESS_NETWORK_STATE`**。
>
> v0.2.0 起新增可选权限（见下「APK 权限与组件」）：`INTERNET`（仅外部模型聊天通道）、`READ_CALENDAR`、`READ_CALL_LOG`、`ACCESS_FINE/COARSE/BACKGROUND_LOCATION`；仍**不申请** `ACCESS_NETWORK_STATE`、相机、麦克风、短信、联系人、无障碍、剪贴板读取。

## 审计规则

1. 新增依赖必须在此表登记，并标注运行时网络/遥测/广告/敏感读取结论。
2. 任何带默认网络请求、遥测、崩溃上报、广告或归因的库，移除或替换为本地实现。
3. 传递依赖在首次构建后用 `gradle dependencies` 复核；发现网络/遥测传递依赖即排除或替换。
4. 模型推理使用本地 GGUF；不引入任何云端推理 SDK。

## 阶段 6 复核记录（2026-11-22，debug APK）

### 本地导入复核（任务 #17，2026-11-22）

- 导入侧 JSON 解析为手写 tokenizer + 递归下降解析器（`data/export/MiniJson.kt`），**未引入** moshi/gson/kotlinx-serialization 等任何 JSON 库；运行时依赖表无变化。
- 导入仅经 SAF `ContentResolver` 读取用户选定的本地文件，不发起网络请求、不读剪贴板；无新增权限、无新增组件。

### 自动确认复核（任务 #28，2026-11-22）

- 自动确认裁决与降级弹窗为纯 Kotlin/Compose 既有栈改动（`DraftRepository`/`InferenceWorker`/`PanelViewModel`/`PanelScreen`/`SettingsStore` 及队列页开关区块），**未引入任何新运行时依赖**；运行时依赖表无变化。
- 未新增权限、未新增组件；自动确认只写本地 Room，降级标记存本地 DataStore；不联网、不读剪贴板、不发起文件选择外的存储访问。
- **#61 默认值翻转**：`autoConfirmEnabled` 默认由 false 改为 true，仅调整 `SettingsSnapshot`/DataStore 读取兜底/ViewModel state 初始值与对应文案、测试断言；**未引入新运行时依赖、权限、组件或 DataStore 键**，运行时依赖表无变化。

### 失效页备份提示复核（任务 #19，2026-11-22）

- 失效页仅读取既有 DataStore 键 `last_export_epoch_ms` 回显上次本地导出时间，纯 UI 文案改动，**未引入任何新运行时依赖**；运行时依赖表无变化。
- 未新增权限、未新增组件、未改数据库结构；不联网、不读剪贴板；失效状态下不提供导出入口。

### 手写片段记录复核（任务 #20，2026-11-22）

- 手写片段链路为纯 Kotlin/Compose/Room 既有栈改动（新实体 `ManualFragment`、`ManualFragmentDao`、`ManualFragmentRepository`、导出/导入 DTO 与队列页表单/列表），**未引入任何新运行时依赖**；运行时依赖表无变化。
- 未新增权限、未新增组件；片段只写本地加密 Room，导出/导入复用既有 SAF 与手写 JSON 序列化，不联网、不读剪贴板、不读相册/通讯录/聊天。
- 数据库由 version 1 升至 version 2，附 `MIGRATION_1_2` 建新表 `manual_fragments`，不改既有表结构与数据，未使用破坏性迁移；未新增 DataStore 键。

### 队列页 UI 缺陷修复复核（2026-11-22）

- 修复 LazyColumn 跨组重复 key 崩溃、表单固定顶部、子页面返回拦截，均为纯 Kotlin/Compose 既有栈改动，**未引入任何新运行时依赖**；运行时依赖表无变化。
- 未新增权限、未新增组件；Manifest 仅补 `android:enableOnBackInvokedCallback="true"`（返回回调开关，非权限），无新增声明权限；不联网、不读剪贴板。

### 长周期回顾复核（任务 #21，2026-11-22）

- 回顾窗模型、汇编仓储、双轨生成器与回顾页为纯 Kotlin/Compose/Room 既有栈改动（`core/time/ReviewPeriod`、`ReviewRepository`、`ReviewGenerator`、`ReviewViewModel`/`ReviewScreen`，复用既有 `EngineRouter`/`TemplateFallbackEngine`/`DraftRepository`），**未引入任何新运行时依赖**；运行时依赖表无变化。
- 未新增权限、未新增组件、未改数据库结构；回顾只读写本地加密 Room 与本地设置，输入仅结论性聚合数值与截断要点，不联网、不读剪贴板、不读原始事件流/包名/正文全文。

### 加密备份复核（任务 #18，2026-11-22）

- 加密容器 `core/security/BackupCipher` 仅使用 JDK 内置 `javax.crypto`（PBKDF2-HMAC-SHA256 派生、AES/GCM/NoPadding）与 `java.security.SecureRandom`，**未引入任何新运行时依赖**；备份仓储/视图模型/区块复用既有 Room、DataStore、SAF 与手写 JSON 序列化/解析，运行时依赖表无变化。
- 未新增权限、未新增组件、未改数据库结构；备份与恢复全程经 SAF 读写用户选定的本地文件，不联网、不读剪贴板；口令仅在内存暂存到本次文件选择完成，不写入 DataStore 或磁盘。备份包不含包名、联系人、通知正文与聊天内容，也不携带 SQLCipher 口令或 Keystore 条目。

### 里程碑与自定义标签复核（任务 #22，2026-11-22）

- 里程碑链路为纯 Kotlin/Compose/Room 既有栈改动（新实体 `Milestone`、`MilestoneDao`、`MilestoneRepository`、导出/导入 DTO 映射与队列页表单/卡片），复用既有手写 JSON 序列化/解析与 SAF/仓储链路，**未引入任何新运行时依赖**；运行时依赖表无变化。
- 未新增权限、未新增组件、未新增 DataStore 键；数据库由 version 2 升至 version 3，附 `MIGRATION_2_3` 建新表 `milestones` 与时间索引，不改既有表结构与数据，未使用破坏性迁移。里程碑标题/标签/说明均为本人手输短文本，存于本地加密 Room；随明文导出与加密备份一并迁移，跨设备导入不带片段内部主键；不联网、不读剪贴板、不采集任何外部内容。

### 队列 ViewModel instrumented 测试复核（任务 #25，2026-11-22）

- main 侧可测性改造为纯既有栈：`AppDatabase.inMemory` 进程内内存库构造（测试专用，生产 `get` 加密路径不变）、`SettingsAccess` 最小接口由 `SettingsStore` 实现、`DraftViewModel` 增加注入仓储/设置的测试构造；**未引入任何新运行时依赖**，运行时依赖表无变化。
- 测试依赖 `androidx.test:runner`、`androidx.test:core`、`androidx.test.ext:junit`、`espresso-core`、`compose-ui-test-junit4/manifest` 均声明为 `androidTestImplementation` / `debugImplementation`，只参与测试 APK，**不进入 release 运行时类路径**；未新增权限、未新增组件、未新增 DataStore 键、未改数据库结构。instrumented 测试使用内存库与 fake 设置，不读写真库、Keystore、DataStore，不启动 WorkManager 推理，不联网、不读剪贴板。

### 加密备份本机往返测试复核（任务 #27，2026-11-22）

- 往返测试 `BackupRoundTripInstrumentedTest` 复用既有 `ExportRepository` / `BackupCipher` / `ImportRepository` / `WipeRepository` / `ServiceLocator` 链路，**未引入任何新运行时依赖**，运行时依赖表无变化；未新增权限、组件、DataStore 键或数据库结构。
- 该用例为破坏性擦除测试，默认 `@Ignore`，不进入常规 `connectedDebugAndroidTest` 套件，仅手动移除注解后单跑；测试产物（`.mpbb` / 兜底 JSON）落在 app 外部私有目录，不写入公共存储、不联网、不读剪贴板。备份包不含 GGUF 模型文件；第二台设备跨设备回归不在本次复核范围。

### 本轮增强复核（任务 #52–#59，2026-11-22）

- **模型随包内置（#52）**：基线 GGUF 作为 `assets/models/bundled-model.gguf` 资源打包（`noCompress("gguf")`），首启后台拷贝到私有目录并做 SHA-256 基线校验；属应用资源而非 Maven 依赖，**未引入任何新运行时依赖**，运行时依赖表无变化。未新增权限/组件；拷贝与校验只读写应用私有目录，不联网、不读剪贴板。APK 体积随内置模型增大约 491 MB。
- **运行状态可见性（#53）/ 导入策略与留痕（#54）/ 跨窗趋势对比（#55）/ 锚点与关联控件（#57）/ 记忆本专区（#56）**：均为纯 Kotlin/Compose/Room/DataStore 既有栈改动，新增 ViewModel/Section/Composable 与本地查询，**未引入任何新运行时依赖**；未新增权限/组件。#54 新增 DataStore 键 `last_import_message` / `last_import_is_error` / `last_import_at_ms`，仅存本地设置；#55 趋势对比只读本地已确认记忆/片段计数；#57 锚点为本地 epochMs、关联为片段内部软引用。
- **来源标注与撤销（#58，DB 3→4）**：`ConfirmedMemory` 补 `source` 列，`MIGRATION_3_4` `ALTER TABLE ... ADD COLUMN source TEXT NOT NULL DEFAULT 'manual'`，不改既有数据、未使用破坏性迁移；撤销为本地 DAO 删除。**未引入新依赖/权限/组件**。导出/导入 DTO 暂不携带 source，跨库重建回退默认 `manual`。
- **回顾文案口径与独立导出段（#59）**：仅 `MEMORY` 写正式记忆表，标签/叙述只置草案已确认状态；导出/导入复用既有手写 JSON 序列化/解析与 SAF 链路，新增 `narratives` 可选段（旧包缺失按空列表兼容），**未引入新依赖/权限/组件/数据库表**。
- 本轮未新增任何第三方库；运行时坐标集合与依赖树结论不变（androidx / Kotlin / coroutines + guava listenablefuture + okio-jvm + sqlcipher-android），无 Firebase / 网络 / 遥测 / 广告归因库。

### v0.2.0 增强复核（2026-11-23，外部模型接入 + 插件采集落地）

- **外部（联网）聊天模型**：`model/remote/` 下 `RemoteProviders`（预置 DeepSeek/通义/Kimi/智谱/OpenRouter/OpenAI 模板与自定义）、`RemoteChatConfig`（三项齐全才视为可调用）、`RemoteChatClient`（JDK `HttpURLConnection`，30s 超时，手写 JSON 请求体，`MiniJson` 解析 `choices[0].message.content`）。**未引入 OkHttp/Retrofit/Ktor/gRPC 等任何第三方网络库**；运行时坐标集合无变化。API Key 经 `KeyStoreManager.wrapString` AES-GCM 包装后存 DataStore 密文，明文只在内存；未启用/配置不全/解密失败/网络异常/空回复时自动降级本地 GGUF，再不可用落模板兜底。面板回顾等其他推理通道仍只走本地。
- **插件真实采集（4 项接入，1 项保留占位）**：通知元数据 `NotificationMetaListener`（包名/频道/时间戳）、日历 `CalendarCollectWorker`（起止时间）、通话日志 `CallLogCollectWorker`（方向/时长/日期，号码 SHA-256 截断 12 位）、地理围栏 `GeofenceCollectWorker`（最近定位比对常去点半径，仅 enter/leave 离散事件）；写入既有 `plugin_events` 表，未新增数据库结构。各 Worker/服务先读插件开关与系统权限，关闭或未授权静默跳过。健康连接未引入 `androidx.health.connect`（compileSdk 35 / AGP 8.7.3 与 connect-client 1.1.0+ 不兼容），保留 `implemented=false` 占位与禁用开关，未声明对应健康权限。
- **依赖/权限/组件**：未新增 Maven 运行时依赖；新增 `INTERNET` 与日历/通话/定位可选权限、一个通知监听服务；采集与聚合任务仍由既有 WorkManager 调度。运行时不读取网络状态（`ACCESS_NETWORK_STATE` 仍被 `tools:node="remove"`）。

### 依赖树（`gradle :app:dependencies --configuration debugRuntimeClasspath`）

- 运行时坐标全部为 androidx / JetBrains Kotlin / kotlinx-coroutines；第三方仅 `com.google.guava:listenablefuture:1.0`、`com.squareup.okio:okio-jvm:3.4.0`、`net.zetetic:sqlcipher-android:4.6.1`。
- 未发现 Firebase / Crashlytics / Sentry / 广告归因 / analytics / okhttp / Retrofit / gRPC 等带网络或遥测的库。

### APK 权限与组件（`aapt2 dump permissions / badging / xmltree`）

- 声明权限：`PACKAGE_USAGE_STATS`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC`、`POST_NOTIFICATIONS`、`WAKE_LOCK`，以及 androidx 生成的 `app.minepanel.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`。v0.2.0 另声明 `INTERNET`（仅外部模型聊天通道）、`READ_CALENDAR`、`READ_CALL_LOG`、`ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION`、`ACCESS_BACKGROUND_LOCATION`（均为可选插件，默认关闭、逐项授权）。**仍无 `ACCESS_NETWORK_STATE`、无 `RECEIVE_BOOT_COMPLETED`、无相机/麦克风/短信/联系人/无障碍/剪贴板权限。**
- 自有组件：`MainActivity`（启动器）、`RecordingService`（`exported=false`）、`NotificationMetaListener`（通知监听服务，`exported=true` 但受 `BIND_NOTIFICATION_LISTENER_SERVICE` 系统权限保护，仅取包名/频道/时间戳）。
- WorkManager / Room 传递组件：`SystemAlarmService`、`SystemJobService`（`BIND_JOB_SERVICE`）、`SystemForegroundService`、各约束 `ConstraintProxy`、`RescheduleReceiver`、Room 失效服务等，均 `exported=false`；`DiagnosticsReceiver`、`ProfileInstallReceiver` 虽 `exported=true`，但受系统 `android.permission.DUMP` 保护，普通三方应用无法调用。
- RescheduleReceiver 虽注册 `BOOT_COMPLETED` 意图，但清单未声明 `RECEIVE_BOOT_COMPLETED`，收不到开机广播，符合"不做隐蔽自启"。

### 网络相关类/字符串扫描

- 应用源码（`app/src/main`）的网络调用仅有远程聊天通道一处：JDK 内置 `java.net.HttpURLConnection.openConnection()`，`POST` 到用户在设置中自填的 OpenAI 兼容端点；**无** `okhttp` / `Retrofit` / `Socket` / gRPC / Ktor 等第三方网络栈，无遥测/统计/远程配置硬编码域名。
- dex 内 URL 仅为 XML 命名空间、SVG schema 与 AndroidX/JetBrains 库文档/issue 链接，无业务域名或遥测端点。
- 原生库动态依赖（`llvm-readelf -d`）：`libminepanel-llama.so` 仅 NEEDED `libandroid / liblog / libm / libdl / libc`，**不链接 libcurl**；`libsqlcipher.so` 仅 NEEDED `libdl / liblog / libc / libm`。so 内 `http(s)://` 字符串为工具链与源码 issue 注释，`getaddrinfo`/`curl` 为未引用符号残留；清单无 `INTERNET` 权限，运行时网络访问被系统门禁。

## 待复核项

- [x] `gradle :app:dependencies` 全量传递依赖清单（见上）
- [x] APK 权限与组件清单（aapt dump，见上）
- [x] APK 内网络相关类/字符串扫描结果（见上）
