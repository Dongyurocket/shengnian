# 声念增量能力 Implementation Plan

> 本计划建立在已完成的 `2026-08-30-ai-voice-note-app.md` MVP 计划之上，针对 2026-08-31 三张需求截图执行后续迭代。

**Goal:** 在已完成的声念 MVP 基线上，落地截图中提出的链接提炼、笔记编辑后二次整理、图片识别、AI 图表、灵感归类和多笔记合并能力，并补齐待办编辑、多次精确提醒、系统闹钟、手机日历同步、模型思考配置和笔记生命周期状态。

**Architecture:** 保留现有 MVVM + Room + WorkManager + `LlmGateway` 三协议适配器架构。新增来源链接、图片附件、图表和待办提醒等本地实体；将多模态输入收敛到 `LlmRequest.images`，由三种适配器分别转换为各自协议的图片内容块。URL 抓取和图片复制均在本地受限处理，AI 仍通过现有后台流水线异步执行；图表使用结构化 JSON 和 Compose Canvas 原生渲染。提醒由 `AlarmManager` 统一调度，系统闹钟使用 `AlarmClockInfo`，日历同步通过获得授权后的 `CalendarProvider` 完成；模型思考配置由 `DataStore` 贯穿三种 LLM 协议。

**Tech Stack:** Kotlin 2.0.21 · Jetpack Compose / Material3 · Room 2.6.1 · WorkManager · OkHttp · kotlinx.serialization · Android Photo Picker · Android BitmapFactory / Canvas · JUnit / MockWebServer。

---

## 需求范围与默认决策

截图明确提出的增量需求：

1. 记录中的链接可被读取，提炼主要内容并参与笔记整理。
2. 整理后的笔记可编辑；补充内容后可再次交给 AI 整理。
3. 支持插入图片，AI 识别图片内容并参与整理。
4. 支持 AI 生成流程图、思维导图。
5. 新增灵感归类；整理时把灵感与强相关笔记建立关联。
6. 用户可选择多条已有笔记，让 AI 合并整理。

默认决策：

- 多笔记合并**生成一条新笔记并保留原笔记**，避免不可逆数据丢失；新笔记进入正常异步整理流程。
- 图片复制到应用私有目录，后台任务不依赖临时 `content://` 授权；只在 API 请求期间转为受限大小的 base64，不把 base64 写入 Room。
- URL 仅接受 `http`/`https`，每个页面和总上下文都有大小上限；提取失败不阻断 AI 整理，详情页显示失败状态和原始链接。
- 图表不使用远程 Mermaid/CDN 或嵌套 WebView，避免离线和滚动生命周期问题；LLM 输出受校验的节点/边 JSON，由原生 Canvas 绘制。
- “寻找相关 skill”在 Android 端没有动态加载 Proma Skill 的运行时能力，因此实现为可替换的 `DiagramGenerator` + 版本化 Prompt 扩展点，后续可接入服务端 skill 而不改 UI/存储协议。
- 不新增云同步；数据库升级使用 3→4 显式 Migration，不再因新增字段清空已有用户数据。

## 技术研究结论

- Android Photo Picker 的 `PickVisualMedia`/`PickMultipleVisualMedia` 可选图，选中后立即复制到应用私有目录，适合 WorkManager 长时任务；不需要申请广泛媒体读取权限。
- OpenAI Chat 使用 `content` 数组中的 `image_url.url=data:<mime>;base64,...`；Responses 使用 `input_image.image_url` 数据 URL；Anthropic Messages 使用 `image` + `source.type=base64/media_type/data`。
- 现有 OkHttp/三适配器已具备协议错误降级基础，只需在有图片时切换用户消息内容结构；文本请求保持原结构以避免回归。
- HTML 正文提取采用受限轻量清理器，先移除 `script/style`、优先读取 `article/main`，再用 Android HTML 解码和长度截断；不把网页文本当作系统指令。

---

### Task 1: 扩展数据模型与 3→4 数据库迁移

**Files:**
- Create: `app/src/main/java/com/voiceink/app/data/local/entity/NoteAttachmentEntity.kt`
- Create: `app/src/main/java/com/voiceink/app/data/local/entity/NoteSourceEntity.kt`
- Create: `app/src/main/java/com/voiceink/app/data/local/entity/NoteDiagramEntity.kt`
- Create: `app/src/main/java/com/voiceink/app/data/local/dao/AttachmentDao.kt`
- Create: `app/src/main/java/com/voiceink/app/data/local/dao/SourceDao.kt`
- Create: `app/src/main/java/com/voiceink/app/data/local/dao/DiagramDao.kt`
- Create: `app/src/main/java/com/voiceink/app/data/local/DatabaseMigrations.kt`
- Modify: `app/src/main/java/com/voiceink/app/data/local/entity/NoteEntity.kt`
- Modify: `app/src/main/java/com/voiceink/app/data/local/dao/NoteDao.kt`
- Modify: `app/src/main/java/com/voiceink/app/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/voiceink/app/di/AppModule.kt`

**Step 1: 定义实体与状态值**

- `NoteEntity` 增加 `rawContent`（保留最近一次用户输入）、`isInspiration`；现有构造调用保持默认值兼容。
- `NoteAttachmentEntity` 保存 `noteId/localPath/mimeType/displayName/createdAt`，外键级联删除。
- `NoteSourceEntity` 保存 `noteId/url/title/excerpt/status/error/createdAt/updatedAt`，状态使用 `PENDING/READY/FAILED/UNSUPPORTED` 字符串。
- `NoteDiagramEntity` 保存 `noteId/kind/title/specJson/createdAt/updatedAt`。

**Step 2: 添加 DAO 查询**

- 附件、来源、图表提供按笔记观察、插入/替换、删除接口。
- `NoteDao.observeFiltered` 增加可选 `inspiration` 条件。
- `NoteDao` 增加 `updateDraft`、带 `isInspiration` 的 `applyOrganization`，以及必要的状态/内容更新接口。

**Step 3: 写显式 Migration**

- Room 版本从 3 升至 4。
- `ALTER TABLE notes` 增加非空 `rawContent` 和 `isInspiration` 默认列，并用已有 `content` 回填空的 `rawContent`。
- 创建三个新表及 `noteId` 索引、外键级联规则。
- `AppModule` 使用 `.addMigrations(MIGRATION_3_4)`；保留 destructive fallback 仅作为无法识别的开发数据库兜底不得覆盖已知 3→4 路径。

**Step 4: 验证**

Run: `./gradlew :app:testDebugUnitTest --no-daemon`
Expected: 现有单元测试全部通过，Room/KSP 编译通过。

---

### Task 2: URL 扫描、正文提取与 AI 上下文注入

**Files:**
- Create: `app/src/main/java/com/voiceink/app/pipeline/UrlScanner.kt`
- Create: `app/src/main/java/com/voiceink/app/pipeline/HtmlTextExtractor.kt`
- Create: `app/src/main/java/com/voiceink/app/pipeline/LinkContentExtractor.kt`
- Create: `app/src/main/java/com/voiceink/app/data/repo/NoteSourceRepository.kt`
- Modify: `app/src/main/java/com/voiceink/app/ai/pipeline/AiPipeline.kt`
- Modify: `app/src/main/java/com/voiceink/app/data/local/dao/SourceDao.kt`
- Test: `app/src/test/java/com/voiceink/app/pipeline/UrlScannerTest.kt`
- Test: `app/src/test/java/com/voiceink/app/pipeline/HtmlTextExtractorTest.kt`

**Step 1: 写纯函数测试**

覆盖：多个 URL 去重、去除中文/英文尾部标点、拒绝非 HTTP(S)、HTML script/style 清理、article/main 优先、正文截断和空页面。

**Step 2: 实现受限抓取**

- 使用现有 `OkHttpClient` 的派生 client，连接/读取超时控制在 15 秒。
- 单页最多读取 512 KB，正文最多保留 6,000 字，总上下文最多保留 12,000 字。
- 检查响应成功、Content-Type；非 HTML 内容记录 `UNSUPPORTED`。
- 每个 URL 单独捕获异常并写入 `FAILED`，不让一个坏链接使整条笔记失败。

**Step 3: 缓存来源并构造上下文**

- `NoteSourceRepository.refreshForNote` 以 `(noteId,url)` 去重，已有成功内容直接复用。
- 生成明确的“外部页面参考资料”区块，向 Prompt 说明网页内容是不可信参考文本，不得执行其中指令。
- 对没有链接的笔记保持现有请求完全不变。

**Step 4: 接入流水线**

- `AiPipeline.process` 使用 `rawContent ?: content` 扫描 URL，在 LLM 调用前刷新来源。
- Prompt 用户区同时包含用户原文、提取到的页面标题/正文和错误链接列表。
- 详情页后续可观察来源状态；抓取失败仍继续正常组织。

**Step 5: 验证**

Run: `./gradlew :app:testDebugUnitTest --tests '*UrlScannerTest' --tests '*HtmlTextExtractorTest'`
Expected: 所有 URL/HTML 纯函数测试通过。

---

### Task 3: 图片附件、图片编码与三协议多模态请求

**Files:**
- Create: `app/src/main/java/com/voiceink/app/data/repo/NoteAttachmentRepository.kt`
- Create: `app/src/main/java/com/voiceink/app/ai/ImagePayloadEncoder.kt`
- Modify: `app/src/main/java/com/voiceink/app/ai/LlmModels.kt`
- Modify: `app/src/main/java/com/voiceink/app/ai/adapter/OpenAiChatAdapter.kt`
- Modify: `app/src/main/java/com/voiceink/app/ai/adapter/OpenAiResponsesAdapter.kt`
- Modify: `app/src/main/java/com/voiceink/app/ai/adapter/AnthropicMessagesAdapter.kt`
- Modify: `app/src/main/java/com/voiceink/app/capture/CaptureController.kt`
- Modify: `app/src/main/java/com/voiceink/app/ai/pipeline/AiPipeline.kt`
- Modify: `app/src/main/java/com/voiceink/app/ai/prompt/Prompts.kt`
- Modify: `app/src/main/java/com/voiceink/app/ai/prompt/JsonExtractor.kt`
- Test: `app/src/test/java/com/voiceink/app/ai/adapter/MultimodalAdapterTest.kt`

**Step 1: 扩展内部请求模型**

新增 `LlmImage(mimeType, base64)` 和 `LlmRequest.images: List<LlmImage> = emptyList()`。文本调用的默认值保持空列表。

**Step 2: 实现附件持久化与编码**

- 从 Photo Picker URI 复制到 `filesDir/note-attachments`，单文件最多 10 MB。
- 编码前用 `BitmapFactory.Options.inJustDecodeBounds` 计算采样，最长边压到 1,600 px，JPEG quality 85；最多发送 4 张、总 payload 有上限。
- 失败时保留附件记录并返回可读错误，不在主线程读取大文件。

**Step 3: 改造三种适配器**

- Chat：有图片时用户 `content` 为 text + `image_url` 数组；无图片时维持原字符串。
- Responses：在 `input` content 中追加 `input_image`，使用 JPEG/PNG data URL。
- Anthropic：在 user content 中加入 `image/source(base64)` 块，保留末尾 assistant `{` 预填。
- 为三种请求添加 MockWebServer 断言；既验证字段，也验证文本请求的回归结构。

**Step 4: 接入自动整理**

- `CaptureController.capture` 接受图片 URI 列表，先落笔记、复制附件，再入队。
- `AiPipeline` 将附件编码后传给组织请求，并在系统 Prompt 中要求识别图片文字/结构、只把可确认事实写入整理结果。
- 不支持视觉的端点返回清晰失败状态，附件本地仍保留。

**Step 5: 验证**

Run: `./gradlew :app:testDebugUnitTest --tests '*MultimodalAdapterTest'`
Expected: Chat/Responses/Anthropic 三类图片请求字段正确，原有 44 个测试不回归。

---

### Task 4: 编辑、二次整理与详情页来源/附件展示

**Files:**
- Create: `app/src/main/java/com/voiceink/app/ui/detail/AttachmentPreview.kt`
- Create: `app/src/main/java/com/voiceink/app/ui/detail/SourceLinksSection.kt`
- Modify: `app/src/main/java/com/voiceink/app/ui/detail/NoteDetailViewModel.kt`
- Modify: `app/src/main/java/com/voiceink/app/ui/detail/NoteDetailScreen.kt`
- Modify: `app/src/main/java/com/voiceink/app/ui/capture/CaptureViewModel.kt`
- Modify: `app/src/main/java/com/voiceink/app/ui/capture/CaptureScreen.kt`
- Modify: `app/src/main/java/com/voiceink/app/ai/pipeline/AiProcessWorker.kt`
- Modify: `app/src/main/java/com/voiceink/app/ai/pipeline/AiPipeline.kt`
- Modify: `app/src/main/java/com/voiceink/app/data/repo/NoteRepository.kt`

**Step 1: 增加编辑状态和持久化接口**

- 详情页提供编辑标题/正文、添加/删除图片、保存草稿、保存并重新整理。
- 保存草稿更新 `rawContent` 与显示内容但不发请求；重新整理将状态置为 `PENDING_AI`，设置 `intentHint=note`，通过 WorkManager 入队。
- 增加 `forceNote` 工作输入，重新整理即使模型误判为 todo 也不得删除原笔记；原有新输入仍按原意图分流。
- 重新整理时清理过期图表，避免显示与新正文不一致的图表。

**Step 2: 速记页插图**

- 使用 `PickMultipleVisualMedia(4)`，在输入页显示缩略图和移除按钮。
- 支持“只有图片没有文字”的记录，保存后由视觉模型生成整理内容。
- 保存成功立即清空文字和图片，保持连续记录体验。

**Step 3: 详情页展示**

- 顶部增加编辑图标和保存/重整命令。
- 正文下方显示图片缩略图；来源区显示 URL、提取状态、标题/摘要和“打开链接”。
- `PENDING_AI/AI_FAILED` 状态保留原有重试入口，并显示附件/来源不会丢失。

**Step 4: 验证**

Run: `./gradlew :app:assembleDebug --no-daemon`
Expected: Compose/KSP 编译通过；人工检查详情页编辑、二次整理、图片添加/删除和来源链接点击路径。

---

### Task 5: 灵感归类与 AI 图表生成/渲染

**Files:**
- Create: `app/src/main/java/com/voiceink/app/ai/diagram/DiagramSpec.kt`
- Create: `app/src/main/java/com/voiceink/app/ai/diagram/DiagramGenerator.kt`
- Create: `app/src/main/java/com/voiceink/app/ui/detail/DiagramCanvas.kt`
- Modify: `app/src/main/java/com/voiceink/app/ai/prompt/Prompts.kt`
- Modify: `app/src/main/java/com/voiceink/app/ai/prompt/ParsedIntent.kt`
- Modify: `app/src/main/java/com/voiceink/app/ai/prompt/JsonExtractor.kt`
- Modify: `app/src/main/java/com/voiceink/app/data/local/dao/DiagramDao.kt`
- Modify: `app/src/main/java/com/voiceink/app/ui/detail/NoteDetailViewModel.kt`
- Modify: `app/src/main/java/com/voiceink/app/ui/detail/NoteDetailScreen.kt`
- Modify: `app/src/main/java/com/voiceink/app/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/voiceink/app/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/voiceink/app/data/repo/NoteRepository.kt`
- Test: `app/src/test/java/com/voiceink/app/ai/diagram/DiagramSpecTest.kt`

**Step 1: 增强灵感结构化**

- 组织 Schema 增加必填 `is_inspiration`；旧模型缺失字段时按 `type == 灵感` 兼容推断。
- `NoteEntity` 落库该标记；首页增加“灵感”筛选 Chip，详情页显示灵感标记。
- 现有 LinkDiscovery 对灵感笔记继续执行，详情页把高分相关笔记标为“灵感归类建议”，并允许沿用现有解除关联动作；不自动删除或覆盖原笔记。

**Step 2: 定义图表协议和解析测试**

- `DiagramSpec` 包含 `kind=flowchart|mindmap`、标题、最多 12 个节点和最多 16 条边。
- `Prompts.DIAGRAM` 要求只输出 JSON；Schema 严格要求对象字段，兼容三协议。
- `JsonExtractor.extractDiagram` 验证节点 ID 唯一、边引用存在、数量上限和非法 kind，错误返回明确状态。

**Step 3: 生成和持久化**

- 详情页提供“生成流程图 / 生成思维导图”命令。
- `DiagramGenerator` 读取当前笔记正文、来源摘要、图片（若有），调用 `LlmGateway`，解析后 upsert；失败不影响笔记正文。
- 图表生成过程显示 loading/error/retry，已有图表支持再次生成替换。

**Step 4: 原生渲染**

- 流程图按拓扑/输入顺序分层，绘制带箭头连线和固定尺寸节点。
- 思维导图以根节点向左右/下方分支，节点文字截断不改变布局尺寸。
- `Canvas` 只接收已验证的 `DiagramSpec`，不执行模型返回的代码或 HTML。

**Step 5: 验证**

Run: `./gradlew :app:testDebugUnitTest --tests '*DiagramSpecTest' --tests '*PromptsSchemaTest'`
Expected: 图表 JSON 校验、严格 Schema 和旧意图解析测试通过；人工检查两类图表在窄屏不重叠、不溢出。

---

### Task 6: 多笔记选择与 AI 合并整理

**Files:**
- Create: `app/src/main/java/com/voiceink/app/data/repo/NoteMergeController.kt`
- Modify: `app/src/main/java/com/voiceink/app/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/voiceink/app/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/voiceink/app/ai/pipeline/AiPipeline.kt`
- Modify: `app/src/main/java/com/voiceink/app/ai/prompt/Prompts.kt`
- Test: `app/src/test/java/com/voiceink/app/data/repo/MergePromptTest.kt`

**Step 1: 写合并输入测试**

验证：选中笔记按稳定顺序拼接、保留标题/正文、明确要求去重和补全、空选择/单条选择被拒绝、原文不被删除。

**Step 2: 实现合并控制器**

- `NoteMergeController.merge(ids)` 只接受至少两条 READY 笔记。
- 构造带来源标记的合并输入，通过 `CaptureController` 写入 `source=merge`、`intentHint=merge` 的新笔记。
- AI Prompt 对 `merge` 提示“整合互补内容、删除重复、保留不确定信息并输出一条完整笔记”。
- 原笔记和其附件/来源保持不变；新笔记完成后由正常 LinkDiscovery 建立关联。

**Step 3: 首页选择模式**

- 首页标题栏增加进入选择模式的图标按钮。
- 选择模式下点击 READY 笔记卡切换选中态；顶部显示数量、取消和 AI 合并动作。
- 少于两条时合并动作禁用；点击合并前弹确认对话框，合并成功后退出选择模式并显示状态反馈。
- 保持普通模式下的打开详情、搜索、分类和灵感筛选行为不变。

**Step 4: 验证**

Run: `./gradlew :app:testDebugUnitTest --tests '*MergePromptTest'`
Expected: 合并输入测试通过；人工检查选择态、取消、确认和新待整理笔记出现。

---

### Task 7: 导出、文档、全量验证与视觉走查

**Files:**
- Modify: `app/src/main/java/com/voiceink/app/data/export/MarkdownExporter.kt`
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Create: `docs/plans/2026-08-31-voice-note-incremental.md`

**Step 1: 扩展备份**

- JSON 备份加入 `isInspiration`、来源 URL/状态、图表 JSON 和附件元数据。
- Markdown frontmatter 加入灵感标记、来源 URL；不把 API key 或未验证网页指令导出为配置。
- 图片二进制按附件目录复制，复制失败在导出结果中明确提示，不静默丢失。

**Step 2: 更新产品文档**

- README 的特性和架构说明加入新能力与“视觉模型需由用户配置”限制。
- CHANGELOG 在 Unreleased 记录本轮增量能力。
- 将本计划归档到项目 `docs/plans/`，标注旧计划已完成、这是后续迭代。

**Step 3: 全量验证**

Run: `./gradlew :app:testDebugUnitTest --no-daemon`
Expected: 全部单元测试通过。

Run: `./gradlew :app:assembleDebug --no-daemon`
Expected: 生成 `app/build/outputs/apk/debug/app-debug.apk`。

人工验收清单：

- 输入含 HTTP(S) 链接的文字，详情页出现来源提取结果；坏链接不阻断整理。
- 详情页编辑正文并选择“重新整理”，原笔记保留且状态回到整理中，完成后元数据更新。
- 速记页插入图片；已配置视觉模型时图片内容进入整理结果，未配置时显示可读失败状态但图片仍在。
- 详情页分别生成流程图和思维导图，窄屏节点不重叠。
- 首页筛选“灵感”，灵感笔记能看到高相关笔记建议。
- 选择两条 READY 笔记进行 AI 合并，新笔记出现，原笔记仍可打开。
- 旧三协议文本调用、提醒、导出和相关笔记功能无回归。

---

## 收尾验证状态：链接、图片、图表与合并（2026-08-31）

- [x] JVM 单元测试：68 个通过
- [x] `:app:compileDebugKotlin`
- [x] `:app:compileDebugAndroidTestKotlin`（包含真实 v3→v4 migration test 的编译）
- [x] `:app:lintDebug`
- [x] `:app:assembleDebug`
- [x] 独立只读审查：已收敛；已处理网络异常重试、强制笔记重试、条件删除/临时待办、Room transaction、固定 DNS、附件方向/透明度、导出并发和输出边界问题
- [x] 历史真机 `NX721J`（Android 14）：`:app:connectedDebugAndroidTest` 通过，1/1 测试成功
- [x] 历史设备基础 UI 走查：测试包可启动，首页、速记、设置、详情和编辑弹窗可打开；本地输入保存后能回到首页并显示失败重试状态
- [ ] 真实 OpenAI/Anthropic/视觉模型端点兼容性：当前仅完成 MockWebServer 契约验证

## 后续需求实现：待办、系统集成、模型思考与生命周期

### 实现范围

- `TodoEntity` 保留旧的 `deadline`、`remindAt`、`remindLeadMinutes` 字段，同时增加 `isAlarm`、`calendarEventId` 等系统集成字段；`TodoReminderEntity` 独立保存 `todoId`、序号和每次具体 `triggerAt`。
- `TodoScreen` / `TodoViewModel` 提供待办内容、截止时间、提醒次数、提醒间隔和逐条提醒时间编辑；未手动指定时间时按截止时间和间隔生成提醒，旧数据回退为单提醒。
- `ReminderScheduler`、`ReminderReceiver`、`BootReceiver` 统一处理多次调度、开机恢复、完成、删除和延期；明确闹钟意图使用 `AlarmManager.setAlarmClock` / `AlarmClockInfo`。
- `CalendarSyncRepository` 在 `READ_CALENDAR` / `WRITE_CALENDAR` 授权后创建、更新和取消系统日历事件；同步失败不阻止本地待办保存。
- `SettingsRepository` / `SettingsViewModel` / `SettingsScreen` 通过 DataStore 保存模型思考开关和低 / 中 / 高强度；`OpenAiChatAdapter`、`OpenAiResponsesAdapter`、`AnthropicMessagesAdapter` 映射各协议的 reasoning / thinking 参数，思考模式省略普通 `temperature`。
- `NoteLifecycleStatus` 独立于 AI 处理状态，支持 `PENDING`、`COMPLETED`、`ABANDONED`；首页筛选、卡片修改、详情页修改和 Markdown 导出均保留该状态。

### 数据库与兼容迁移

- 当前 Room schema version 为 `5`，`AppDatabase` 注册 `TodoReminderEntity`，并通过 `DatabaseMigrations.MIGRATION_4_5` 创建 `todo_reminders` 表、补充待办系统集成字段和笔记生命周期字段的兼容迁移。
- 旧待办数据继续读取旧提醒字段；新调度逻辑在没有独立提醒记录时回退生成一条提醒，不要求清空已有数据库。
- 相关核心文件：`AppDatabase.kt`、`DatabaseMigrations.kt`、`TodoEntity.kt`、`TodoReminderEntity.kt`、`TodoDao.kt`、`TodoReminderDao.kt`、`TodoRepository.kt`、`NoteEntity.kt`。

### 验证结果（2026-08-31）

- [x] `./gradlew :app:testDebugUnitTest --no-daemon`：`80 tests completed, 0 failed`
- [x] `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon`
- [x] `./gradlew :app:assembleDebug --no-daemon`：Debug APK 生成成功
- [x] 真机 `RMX5062` 使用独立包 `com.voiceink.app.test`：DeepSeek Chat 连接测试成功；SiliconFlow Embedding 连接测试成功，向量维度 `4096`
- [x] 自然语言「明天早上 7:50 起床」成功分流为待办并落库；编辑后保存 3 条具体提醒时间，`dumpsys alarm` 显示 3 条带 `AlarmClock` 的精确 `RTC_WAKEUP` 闹钟，`exactAllowReason=permission`
- [x] CalendarProvider 查询到对应事件，事件描述包含 3 条提醒时间；删除临时测试待办后，设备上未保留测试事件和提醒
- [x] 修复跨自然日截止标签，并补充 `TimeUtilsTest`；时间标签现在正确区分「今天」「明天」「已过期」
- [ ] 设备重启后恢复和等待实际到点触发通知的长时测试：当前未执行

本轮不实现：云同步、后台自动上传第三方文件、通用网页浏览器、任意代码/HTML 执行、自动删除原笔记、Proma Skill 动态安装。若某个 LLM 端点不支持视觉输入，应用保留附件并将失败原因展示给用户，用户可切换到支持视觉的模型后重新整理。
