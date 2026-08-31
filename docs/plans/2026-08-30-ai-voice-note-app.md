# 声念 · AI 灵感笔记与智能待办（Android App）实现计划

> 执行时可配合 executing-plans Skill 按任务逐条实施。

**目标（Goal）：** 交付一款 Android 原生应用「声念」（slogan：声落成章，念起成行）：极速文本输入（语音转写交给系统输入法，如豆包输入法，App 不内置 ASR）→ 用户自选协议（OpenAI Chat Completions / OpenAI Responses / Anthropic Messages）的 LLM 完成意图分流、笔记结构化（标题/多维分类/标签/摘要）、待办提取与本地提醒、笔记语义关联双向链接。

**设计基准（Design Baseline）：** 全部 UI 实现以「声念」设计稿为视觉基准——本地镜像 `docs/design/shengnian-ui.html`（设计规范区 + 5 屏画板，浏览器直接打开）；源文件在 Open Design 项目 `ai-ui-c6c9`（预览 http://127.0.0.1:7456/api/projects/ai-ui-c6c9/raw/index.html），迭代后需重新导出覆盖本地镜像。冲突仲裁原则：**视觉呈现以设计稿为准（§11），功能流程以本计划为准**。

**架构（Architecture）：** 单 Module Kotlin 工程，MVVM + 单向数据流；Room 本地持久化（笔记/待办/标签/关联/向量）；输入层为纯文本采集（语音转写由系统输入法完成，App 零音频权限）；AI 层采用适配器模式（`LlmAdapter` 接口 + 三个协议适配器）将协议差异收敛为统一内部模型 `LlmRequest/LlmResult`；处理流水线 `AiPipeline`（意图分流 → 笔记整理 → 关联发现）通过 WorkManager 保证离线重试。

**技术栈（Tech Stack）：** Kotlin 2.x、minSdk 24、Jetpack Compose（Material3）、Room、OkHttp + kotlinx.serialization、WorkManager、Hilt（依赖注入）、Android Keystore（API Key 加密）、MockWebServer + JUnit（协议适配层单测）。

---

## 0. 需求审阅结论（风险点与决策）

| # | 需求点 | 审阅结论与决策 |
|---|--------|----------------|
| 1 | 输入方式 | **App 不内置语音识别**：语音转写交给系统输入法（豆包输入法、讯飞输入法等），App 只接收纯文本。砍掉最重的第三方 SDK 集成、RECORD_AUDIO 权限与前台服务合规负担，聚焦 AI 整理核心价值；也避免与输入法重复造轮子。 |
| 2 | 三协议支持 | Responses API 与 Anthropic 在"强制 JSON 输出"上的能力不同（见 §7.6 对比表），统一用"JSON Schema/response_format + 助手预填 + 解析兜底"三级策略。 |
| 3 | Embedding 语义检索 | Embedding 为**独立配置**：设置页单独填写 Base URL / API Key / 模型名（可指向 OpenAI 官方、硅基流动、Jina、本地 Ollama 等任何 OpenAI 兼容 `/v1/embeddings` 服务），与聊天用 LLM 协议完全解耦。未配置或调用失败 → 走降级链路（标签 Jaccard + LLM 两两复核），见 §9。向量本地暴力余弦检索，万级笔记性能足够，暂不引入 sqlite-vec（YAGNI，留接口）。 |
| 4 | 零摩擦入口 | 没有录音按钮后，用三招补入口速度：① 首页底部中央 FAB 一键进速记页并自动弹键盘（遵循设计稿屏 01/02 结构；另提供"打开 App 直接进速记"可选设置项，照顾极速记录偏好）；② 系统分享菜单接收文本（ACTION_SEND）直接建笔记；③ 桌面快捷方式/小部件一键直达输入框。 |
| 5 | 精确闹钟 | API 31+ 需 `SCHEDULE_EXACT_ALARM` 权限或降级 `setWindow`；API 33+ 需 `POST_NOTIFICATIONS` 运行时权限。 |
| 6 | 离线/失败 | AI 处理失败不能丢输入成果：文本先落库为"待整理"笔记，AI 流水线异步执行、失败重试（WorkManager 指数退避）。 |
| 7 | 云同步 | MVP 不做；导出为 Markdown+JSON（含 frontmatter）到用户目录，作为备份与后续 WebDAV 同步的过渡。 |

---

## 1. 总体架构

```
┌─────────────────────────────── UI 层 (Compose) ───────────────────────────────┐
│  HomeScreen(笔记流) CaptureScreen(速记) NoteDetailScreen TodoScreen InsightsScreen SettingsScreen│
└──────────────┬─────────────────────────────────────────────────┬─────────────┘
               │ StateFlow                                       │ StateFlow
┌──────────────▼───────────┐                      ┌──────────────▼─────────────┐
│    CaptureViewModel      │                      │  Note/Todo/Settings VM     │
└──────────────┬───────────┘                      └──────────────┬─────────────┘
               │                                                │
┌──────────────▼──────────────────────  领域层  ────────────────▼─────────────┐
│  CaptureController：文本落库（PENDING_AI）→ 入队 AI 流水线 (§6)               │
│  AiPipeline: IntentRouter → NoteOrganizer → LinkDiscovery (§8, §9)          │
│  LlmGateway ──► LlmAdapter × 3 (§7)                                         │
│  ReminderScheduler (AlarmManager, §10)                                      │
└──────────────┬─────────────────────────────────────────────────────────────┘
┌──────────────▼──────────────────────  数据层  ──────────────────────────────┐
│  Room: notes / todos / tags / note_tags / note_links / embeddings (§4)      │
│  ApiKeyStore (Keystore AES/GCM, §5)                                         │
└─────────────────────────────────────────────────────────────────────────────┘
```

关键数据流（一次输入的完整旅程）：

1. 用户点首页 FAB 进入速记页 → 键盘自动弹出（语音输入由用户输入法完成，如豆包输入法语音转文字）→ 输入或说出内容 → 点"保存"。
2. 文本**立即**以 `status=PENDING_AI` 存入 `notes` 表（保证不丢），输入框清空可继续下一条。
3. `AiPipeline.enqueue(noteId)` 入 WorkManager（联网约束）：
   - 步骤 A `IntentRouter`：一次 LLM 调用完成"意图分类 + 结构化抽取"（笔记/待办一个 Prompt 搞定，省一次往返）；
   - 若为待办 → 写入 `todos` 表 → `ReminderScheduler` 设闹钟；
   - 若为笔记 → 写回标题/分类/类型/情绪/标签/摘要 → 步骤 B `LinkDiscovery` 计算候选 → LLM 复核 → 建立双向 `note_links`。
4. 全程 UI 通过 Flow 观察状态：`EDITING → AI_PROCESSING → DONE / FAILED(可重试)`。

## 2. 工程代码结构

```
app/
├── build.gradle.kts
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/voiceink/app/
│   │   ├── VoiceInkApp.kt                    # @HiltAndroidApp, WorkManager 初始化
│   │   ├── MainActivity.kt                   # 单 Activity + Navigation Compose
│   │   ├── di/AppModule.kt                   # Room/OkHttp/Repository 绑定
│   │   ├── core/
│   │   │   ├── Result.kt                     # sealed Result<out T>
│   │   │   ├── Jsons.kt                      # 统一 Json { ignoreUnknownKeys = true }
│   │   │   └── TimeUtils.kt
│   │   ├── data/
│   │   │   ├── local/
│   │   │   │   ├── AppDatabase.kt
│   │   │   │   ├── Converters.kt             # List<String>、Long 时间戳、FloatArray
│   │   │   │   ├── entity/
│   │   │   │   │   ├── NoteEntity.kt
│   │   │   │   │   ├── TodoEntity.kt
│   │   │   │   │   ├── TagEntity.kt
│   │   │   │   │   ├── NoteTagCrossRef.kt
│   │   │   │   │   ├── NoteLinkEntity.kt
│   │   │   │   │   ├── NoteEmbeddingEntity.kt
│   │   │   │   │   └── CategoryEntity.kt     # 用户自定义分类体系
│   │   │   │   └── dao/
│   │   │   │       ├── NoteDao.kt
│   │   │   │       ├── TodoDao.kt
│   │   │   │       ├── TagDao.kt
│   │   │   │       └── LinkDao.kt
│   │   │   ├── repo/
│   │   │   │   ├── NoteRepository.kt
│   │   │   │   ├── TodoRepository.kt
│   │   │   │   └── SettingsRepository.kt     # DataStore(协议/model/baseUrl) + ApiKeyStore
│   │   │   └── export/MarkdownExporter.kt    # 备份导出
│   │   ├── capture/
│   │   │   ├── CaptureController.kt          # 保存文本→落库→入队 AI 流水线 (§6)
│   │   │   └── ShareIngestActivity.kt        # 接收系统分享文本 (ACTION_SEND)
│   │   ├── ai/
│   │   │   ├── LlmModels.kt                  # LlmRequest/LlmResult/LlmProtocol 等
│   │   │   ├── LlmGateway.kt                 # 读配置→选 adapter→统一重试/超时
│   │   │   ├── adapter/
│   │   │   │   ├── LlmAdapter.kt             # 接口 (§7.1)
│   │   │   │   ├── OpenAiChatAdapter.kt      # §7.3
│   │   │   │   ├── OpenAiResponsesAdapter.kt # §7.4
│   │   │   │   ├── AnthropicMessagesAdapter.kt # §7.5
│   │   │   │   └── LlmAdapterFactory.kt
│   │   │   ├── prompt/Prompts.kt             # 全部 System Prompt 与 JSON Schema (§8.2)
│   │   │   ├── pipeline/
│   │   │   │   ├── AiPipeline.kt             # 编排 (§8.1)
│   │   │   │   ├── AiProcessWorker.kt        # WorkManager 入口
│   │   │   │   ├── NoteOrganizer.kt          # 写库
│   │   │   │   └── LinkDiscovery.kt          # §9
│   │   │   └── embedding/EmbeddingClient.kt  # 仅 OpenAI 兼容协议可用
│   │   ├── reminder/
│   │   │   ├── ReminderScheduler.kt          # AlarmManager 封装 (§10)
│   │   │   ├── ReminderReceiver.kt           # BroadcastReceiver → 通知
│   │   │   ├── BootReceiver.kt               # 开机重排
│   │   │   └── NotificationHelper.kt
│   │   └── ui/
│   │       ├── theme/ (Color.kt, Type.kt, VoiceInkTheme.kt)  # 设计 token 见 §11.2
│   │       ├── nav/AppNavHost.kt             # 3 Tab（首页/待办/洞察）+ 中央 FAB 直达速记页
│   │       ├── home/ (HomeScreen.kt, HomeViewModel.kt, FilterBar.kt)  # 首页笔记流，按设计稿屏 01
│   │       ├── capture/ (CaptureScreen.kt, CaptureViewModel.kt)       # 速记页，按设计稿屏 02
│   │       ├── detail/ (NoteDetailScreen.kt, NoteDetailViewModel.kt, RelatedNotesSection.kt)  # 屏 03
│   │       ├── todo/ (TodoScreen.kt, TodoViewModel.kt)                # 屏 04
│   │       ├── insights/ (InsightsScreen.kt, InsightsViewModel.kt)    # 屏 05，阶段 6 实现
│   │       └── settings/ (SettingsScreen.kt, SettingsViewModel.kt, ProtocolConfigSection.kt)  # 首页右上角「念」头像进入
│   └── res/ (strings.xml, drawable/, xml/backup_rules.xml)
└── src/test/java/com/voiceink/app/
    ├── ai/adapter/ (三个 adapter 的 MockWebServer 契约测试, §7.7)
    ├── ai/prompt/JsonExtractorTest.kt        # JSON 兜底解析测试
    └── pipeline/LinkDiscoveryTest.kt         # 相似度/阈值测试 (fake dao)
```

**架构决策说明：**
- **单 Module**：MVP 阶段多 Module 只会拖慢迭代；用包边界 + 单向依赖约束代替。
- **Compose**：Material3 + Navigation Compose；按设计稿为三 Tab（首页/待办/洞察），中央 FAB 悬浮于 `NavigationBar` 之上直达速记页，设置页由首页右上角入口进入。视觉 token（色板/字体/圆角）见 §11.2。
- **不用 Retrofit，统一 OkHttp + kotlinx.serialization**：三种协议端点/头/体差异大，Retrofit 注解反而成为约束；适配器直接构建 `Request`，JSON 用 `@Serializable`，可控且易测。
- **Hilt**：适配器/仓库/调度器均接口化注入，测试时替换 fake。

## 3. Gradle 与 Manifest 基线

`app/build.gradle.kts`（依赖部分）：

```kotlin
android {
    namespace = "com.voiceink.app"
    compileSdk = 35
    defaultConfig { applicationId = "com.voiceink.app"; minSdk = 24; targetSdk = 35 }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
}
dependencies {
    // Jetpack
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")

    // 网络 & JSON
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // DI & 安全
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06") // 备选，见 §5

    // 测试
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.11")
}
```

`settings.gradle.kts` 只需默认仓库：

```kotlin
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
```

`AndroidManifest.xml` 关键声明（无音频/前台服务权限，权限面积极小）：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<application ...>
    <!-- 系统分享菜单入口：分享文本到 App 直接建笔记 -->
    <activity android:name=".capture.ShareIngestActivity" android:exported="true"
              android:theme="@style/Theme.Transparent">
        <intent-filter>
            <action android:name="android.intent.action.SEND" />
            <category android:name="android.intent.category.DEFAULT" />
            <data android:mimeType="text/plain" />
        </intent-filter>
    </activity>
    <receiver android:name=".reminder.ReminderReceiver" android:exported="false" />
    <receiver android:name=".reminder.BootReceiver" android:exported="true">
        <intent-filter><action android:name="android.intent.action.BOOT_COMPLETED" /></intent-filter>
    </receiver>
</application>
```

## 4. 数据层（Room Schema 与 DAO）

### 4.1 实体定义

```kotlin
// entity/NoteEntity.kt
@Entity(
    tableName = "notes",
    indices = [Index("category"), Index("createdAt"), Index("status")]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",                 // AI 生成，未处理前为空
    val content: String,                    // 用户输入原文（先落库，AI 整理后可能被润色替换）
    val category: String? = null,           // 主题分类：科技/生活/工作...
    val type: String? = null,               // 灵感/总结/摘录/待研究/日记
    val mood: String? = null,               // 积极/中立/消极
    val summary: String? = null,
    val status: NoteStatus = NoteStatus.PENDING_AI, // PENDING_AI / READY / AI_FAILED
    val source: String = "app",             // app 内输入 / share（系统分享）/ shortcut
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
enum class NoteStatus { PENDING_AI, READY, AI_FAILED }

// entity/TodoEntity.kt
@Entity(tableName = "todos", indices = [Index("deadline"), Index("done")])
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val priority: Int = 1,                  // 0低 1中 2高，由 AI 建议、用户可改
    val deadline: Long? = null,
    val remindAt: Long? = null,             // = deadline - leadMinutes（或用户单独设定）
    val remindLeadMinutes: Int = 5,         // 默认提前 5 分钟，用户可自由设 N
    val done: Boolean = false,
    val sourceNoteId: Long? = null,         // 溯源：来自哪条输入
    val createdAt: Long = System.currentTimeMillis()
)

// entity/TagEntity.kt + NoteTagCrossRef.kt
@Entity(tableName = "tags")
data class TagEntity(@PrimaryKey val name: String, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "note_tags", primaryKeys = ["noteId", "tag"],
    foreignKeys = [ForeignKey(NoteEntity::class, ["id"], ["noteId"], onDelete = ForeignKey.CASCADE)])
data class NoteTagCrossRef(val noteId: Long, val tag: String)

// entity/NoteLinkEntity.kt —— 双向链接：建立时写入两行 (a→b, b→a)，删除同理
@Entity(tableName = "note_links", primaryKeys = ["fromId", "toId"],
    indices = [Index("toId")],
    foreignKeys = [
        ForeignKey(NoteEntity::class, ["id"], ["fromId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(NoteEntity::class, ["id"], ["toId"], onDelete = ForeignKey.CASCADE)
    ])
data class NoteLinkEntity(
    val fromId: Long,
    val toId: Long,
    val score: Float,                       // 综合相似度 0..1
    val reason: String? = null,             // LLM 给出的关联理由，详情页展示
    val autoCreated: Boolean = true,
    val confirmed: Boolean = false,         // 用户确认过 → 权重提升
    val createdAt: Long = System.currentTimeMillis()
)

// entity/NoteEmbeddingEntity.kt —— 向量以 FloatArray 序列化为 BLOB（个人量级暴力检索足够）
@Entity(tableName = "note_embeddings")
data class NoteEmbeddingEntity(
    @PrimaryKey val noteId: Long,
    val vector: ByteArray,                  // FloatArray.toByteArray()
    val model: String,                      // 记录模型名，换模型后需重建
    val updatedAt: Long = System.currentTimeMillis()
)

// entity/CategoryEntity.kt —— 自定义分类体系（AI 学习用户习惯的载体）
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val kind: String,                       // "theme" | "type"
    val usageCount: Int = 0,                // 历史使用频次，注入 Prompt 供 AI 参考
    val userCreated: Boolean = false
)
```

### 4.2 关键 DAO

```kotlin
@Dao
interface NoteDao {
    @Insert suspend fun insert(note: NoteEntity): Long
    @Update suspend fun update(note: NoteEntity)
    @Query("SELECT * FROM notes WHERE id = :id") suspend fun byId(id: Long): NoteEntity?
    @Query("SELECT * FROM notes WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: NoteStatus): Flow<List<NoteEntity>>

    // 列表筛选：分类/标签/时间/关键词，全部可选
    @Query("""
        SELECT DISTINCT n.* FROM notes n
        LEFT JOIN note_tags nt ON nt.noteId = n.id
        WHERE (:category IS NULL OR n.category = :category)
          AND (:tag IS NULL OR nt.tag = :tag)
          AND (:keyword IS NULL OR n.title LIKE '%'||:keyword||'%' OR n.content LIKE '%'||:keyword||'%')
        ORDER BY n.createdAt DESC
    """)
    fun observeFiltered(category: String?, tag: String?, keyword: String?): Flow<List<NoteEntity>>

    @Query("SELECT id, title, summary FROM notes WHERE status = 'READY' AND id != :excludeId")
    suspend fun allDigests(excludeId: Long): List<NoteDigest> // 供关联发现的 LLM 复核
}
data class NoteDigest(val id: Long, val title: String, val summary: String?)

@Dao
interface LinkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(links: List<NoteLinkEntity>)
    @Query("DELETE FROM note_links WHERE (fromId = :a AND toId = :b) OR (fromId = :b AND toId = :a)")
    suspend fun deleteBidirectional(a: Long, b: Long)
    @Query("""
        SELECT n.*, l.score, l.reason FROM note_links l JOIN notes n ON n.id = l.toId
        WHERE l.fromId = :noteId ORDER BY l.score DESC
    """)
    fun observeRelated(noteId: Long): Flow<List<RelatedNote>>
}
data class RelatedNote(@Embedded val note: NoteEntity, val score: Float, val reason: String?)

@Dao
interface EmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(e: NoteEmbeddingEntity)
    @Query("SELECT * FROM note_embeddings") suspend fun all(): List<NoteEmbeddingEntity>
    @Query("SELECT * FROM note_embeddings WHERE noteId = :id") suspend fun byId(id: Long): NoteEmbeddingEntity?
}
```

> 说明：`RelatedNote` 的 `@Embedded` 字段映射需用 POJO + 别名处理，实现时改为显式列投影即可，此处表达意图。

## 5. API Key 安全存储（Keystore）

方案：AES/GCM 密钥由 Android Keystore 持有（不出 TEE/SE），密文落 SharedPreferences。不直接依赖已弃用维护的 EncryptedSharedPreferences，自己写 30 行封装更可控：

```kotlin
// data/repo/ApiKeyStore.kt
class ApiKeyStore(context: Context) {
    private val alias = "voiceink_api_key"
    private val prefs = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build()
        )
        return gen.generateKey()
    }

    fun save(apiKey: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ct = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("api_key_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("api_key_ct", Base64.encodeToString(ct, Base64.NO_WRAP))
            .apply()
    }

    fun load(): String? {
        val iv = prefs.getString("api_key_iv", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        val ct = prefs.getString("api_key_ct", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return runCatching { String(cipher.doFinal(ct), Charsets.UTF_8) }.getOrNull()
    }
}
```

协议类型 / BaseUrl / Model 名等**非密**配置用 DataStore Preferences；**两类** API Key（聊天 LLM、Embedding）均进 Keystore（`ApiKeyStore` 以 key alias 区分：`voiceink_llm_key` / `voiceink_embed_key`）。

## 6. 快速文本输入设计（零摩擦入口）

### 6.1 设计原则

语音转写已由系统输入法承担（用户用豆包输入法等说完即得文字），App 要做的是把"想法 → 落库"的链路缩到最短：**首页点 FAB → 速记页键盘已弹出 → 输入/说话 → 保存 → 立刻可以输入下一条**。保存不等待 AI，AI 全部异步。默认首页为笔记流（设计稿屏 01）；另有"打开 App 直接进速记页"设置项，供极速记录偏好者开启。

### 6.2 CaptureController

```kotlin
// capture/CaptureController.kt
class CaptureController @Inject constructor(
    private val notes: NoteRepository,
    private val pipeline: AiPipeline
) {
    /** 保存一条输入：先落库再异步 AI，全程不阻塞 UI */
    suspend fun capture(text: String, source: String = "app"): Long {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty())
        val noteId = notes.insertRaw(trimmed, source)   // status=PENDING_AI
        pipeline.enqueue(noteId)                        // WorkManager 联网后执行
        return noteId
    }
}

// capture/ShareIngestActivity.kt —— 透明 Activity，接住系统分享直接落库
class ShareIngestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        lifecycleScope.launch {
            if (!text.isNullOrBlank()) {
                appGraph.captureController.capture(text, source = "share")
                Toast.makeText(this@ShareIngestActivity, "已保存，AI 整理中…", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}
```

### 6.3 入口速度优化

- **速记页**：`CaptureScreen` 进入即 `focusRequester.requestFocus()` + `keyboardController.show()`；大输入框 + 底部"保存并继续"按钮；支持多行、Ctrl+Enter 快捷保存。视觉按设计稿屏 02：衬线体编辑区 + 常驻 AI 提示条「保存后自动整理，并提炼待办」。设计稿中"键盘麦克风键呼吸光晕"是引导示意——Android 不绘制系统键盘，落地为首启 Coach Mark + 输入框 placeholder「说点什么，或写点什么…」。
- **桌面快捷方式**（`ShortcutManager`）：长按图标弹出"新建灵感""新建待办"，直达输入页并预设意图提示（"新建待办"时 AI 优先判 todo）。
- **分享接收**：任何 App 选中文本 → 分享 → 本 App → 静默落库（见上 `ShareIngestActivity`）。
- **剪贴板快捷**（可选）：输入页检测剪贴板非空且未入库时，顶部显示"粘贴最近复制内容？"Chip，一键入库。

## 7. AI 协议适配层（核心设计）

### 7.1 统一内部模型与适配器接口

上层业务只面向 `LlmRequest`/`LlmResult` 编程，完全不知道底层是哪一家协议：

```kotlin
// ai/LlmModels.kt
enum class LlmProtocol { OPENAI_CHAT, OPENAI_RESPONSES, ANTHROPIC_MESSAGES }

data class LlmEndpoint(
    val baseUrl: String,          // 用户填写，如 https://api.openai.com 或 http://192.168.1.5:11434
    val apiKey: String,
    val model: String,            // 用户自由填写，如 gpt-4o-mini / deepseek-chat / claude-sonnet-4-5 / qwen2.5
    val protocol: LlmProtocol
)

/** Embedding 独立配置：与聊天 LLM 解耦，可指向任何 OpenAI 兼容 /v1/embeddings 服务 */
data class EmbeddingEndpoint(
    val enabled: Boolean = false,
    val baseUrl: String,          // 如 https://api.siliconflow.cn 或 http://192.168.1.5:11434
    val apiKey: String,           // 同样经 Keystore 加密存储（§5）
    val model: String             // 如 text-embedding-3-small / bge-m3 / nomic-embed-text
)

/** 内部标准请求：一次“系统指令 + 用户输入 → JSON 文本”的调用 */
data class LlmRequest(
    val system: String,
    val user: String,
    val jsonSchemaName: String,   // 供 Responses json_schema / 兜底解析使用
    val maxTokens: Int = 2048,
    val temperature: Double = 0.3 // 结构化任务低温度
)

/** 内部标准结果：只承诺 text 是“尽量合法 JSON 的字符串”，解析兜底在 §8.3 */
data class LlmResult(val text: String, val stopReason: StopReason, val usageTokens: Int?)
enum class StopReason { COMPLETE, MAX_TOKENS, OTHER }

// ai/adapter/LlmAdapter.kt
interface LlmAdapter {
    val protocol: LlmProtocol
    /** 根据端点配置构建 HTTP 请求体/头，并把响应解析为 LlmResult */
    suspend fun complete(endpoint: LlmEndpoint, request: LlmRequest): LlmResult
}
```

### 7.2 公共底座：OkHttp、重试与错误模型

```kotlin
// ai/adapter/AbstractLlmAdapter.kt
abstract class AbstractLlmAdapter(
    protected val client: OkHttpClient,   // 单例：connectTimeout 15s / readTimeout 60s
    protected val json: Json              // Json { ignoreUnknownKeys = true; explicitNulls = false }
) : LlmAdapter {

    protected suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: JsonObject
    ): JsonObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw LlmException(resp.code, parseErrorMessage(text), retriable(resp.code))
            runCatching { json.parseToJsonElement(text).jsonObject }
                .getOrElse { throw LlmException(-1, "响应不是合法 JSON: ${text.take(200)}", retriable = true) }
        }
    }

    private fun retriable(code: Int) = code == 429 || code >= 500
    protected open fun parseErrorMessage(body: String): String = body.take(300)
}

class LlmException(val httpCode: Int, message: String, val retriable: Boolean) : Exception(message)
```

**重试策略**集中在 `LlmGateway`：仅对 `retriable=true` 的错误指数退避（1s/2s/4s，最多 3 次）；401/403 直接提示用户检查 Key；WorkManager 层再做任务级重试（网络断开场景）。

```kotlin
// ai/LlmGateway.kt
class LlmGateway @Inject constructor(
    private val factory: LlmAdapterFactory,
    private val settings: SettingsRepository
) {
    suspend fun complete(request: LlmRequest): LlmResult {
        val endpoint = settings.currentEndpoint()  // DataStore 非密配置 + ApiKeyStore 取 Key
        val adapter = factory.create(endpoint.protocol)
        var delayMs = 1000L
        repeat(3) { attempt ->
            try { return adapter.complete(endpoint, request) }
            catch (e: LlmException) {
                if (!e.retriable || attempt == 2) throw e
                delay(delayMs); delayMs *= 2
            }
        }
        error("unreachable")
    }
}
```

### 7.3 适配器一：OpenAI Chat Completions

**协议要点**：`POST {baseUrl}/v1/chat/completions`；`Authorization: Bearer <key>`；`messages` 数组携带 system/user；`response_format: {"type":"json_object"}` 强制 JSON（DeepSeek/通义千问兼容模式同样支持；本地 Ollama 的 `/v1/chat/completions` 兼容端点也支持 `format: "json"`，可作为后续小适配）。

```kotlin
class OpenAiChatAdapter @Inject constructor(client: OkHttpClient, json: Json) :
    AbstractLlmAdapter(client, json) {

    override val protocol = LlmProtocol.OPENAI_CHAT

    override suspend fun complete(endpoint: LlmEndpoint, request: LlmRequest): LlmResult {
        val url = endpoint.baseUrl.trimEnd('/') + "/v1/chat/completions"
        val body = buildJsonObject {
            put("model", endpoint.model)
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", request.system) }
                addJsonObject { put("role", "user"); put("content", request.user) }
            }
            putJsonObject("response_format") { put("type", "json_object") }
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)   // 新模型字段为 max_completion_tokens，见下方兼容说明
        }
        val resp = post(url, mapOf(
            "Authorization" to "Bearer ${endpoint.apiKey}",
            "Content-Type" to "application/json"
        ), body)
        return parse(resp)
    }

    private fun parse(resp: JsonObject): LlmResult {
        // 标准结构：{ choices: [ { message: { content: "..." }, finish_reason: "stop" } ], usage: {...} }
        val choice = resp["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw LlmException(-1, "响应缺少 choices: ${resp.toString().take(300)}", false)
        val content = choice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: throw LlmException(-1, "choices[0].message.content 缺失", false)
        val finish = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
        return LlmResult(
            text = content,
            stopReason = if (finish == "length") StopReason.MAX_TOKENS else StopReason.COMPLETE,
            usageTokens = resp["usage"]?.jsonObject?.get("total_tokens")?.jsonPrimitive?.intOrNull
        )
    }

    override fun parseErrorMessage(body: String): String =
        runCatching {
            Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")
                ?.jsonPrimitive?.content
        }.getOrNull() ?: super.parseErrorMessage(body)
}
```

**兼容性说明（实现时注意）**：
- GPT-5 系新模型要求 `max_completion_tokens` 且不支持自定义 `temperature`。策略：默认发 `max_tokens`；若 400 且错误信息含 `max_completion_tokens`，自动换字段重试一次（在 gateway 做一次字段降级即可，用户无感）。
- 部分第三方代理不支持 `response_format` → 同样捕获 400 后降级：去掉该字段重试，最终由 §8.3 的 JSON 兜底解析保证健壮。

### 7.4 适配器二：OpenAI Responses API

**协议要点**：`POST {baseUrl}/v1/responses`；`instructions` 承载 system；`input` 为结构化数组；**响应不是 choices，而是 `output` 项数组**（`reasoning` / `message` / `tool_call`…），需从中取 `type=="message"` 项的 `content` 里 `type=="output_text"` 的 `text`；结构化输出用 `text.format`（`json_schema` + `strict`）。

```kotlin
class OpenAiResponsesAdapter @Inject constructor(client: OkHttpClient, json: Json) :
    AbstractLlmAdapter(client, json) {

    override val protocol = LlmProtocol.OPENAI_RESPONSES

    override suspend fun complete(endpoint: LlmEndpoint, request: LlmRequest): LlmResult {
        val url = endpoint.baseUrl.trimEnd('/') + "/v1/responses"
        val body = buildJsonObject {
            put("model", endpoint.model)
            put("instructions", request.system)
            putJsonArray("input") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject { put("type", "input_text"); put("text", request.user) }
                    }
                }
            }
            putJsonObject("text") {
                putJsonObject("format") {
                    put("type", "json_schema")
                    put("name", request.jsonSchemaName)
                    put("strict", true)
                    put("schema", Prompts.schemaFor(request.jsonSchemaName))
                }
            }
            put("temperature", request.temperature)
            put("max_output_tokens", request.maxTokens)
        }
        val resp = post(url, mapOf(
            "Authorization" to "Bearer ${endpoint.apiKey}",
            "Content-Type" to "application/json"
        ), body)
        return parse(resp)
    }

    private fun parse(resp: JsonObject): LlmResult {
        // 1. 状态检查：completed / incomplete(含 incomplete_details.reason="max_output_tokens")
        val status = resp["status"]?.jsonPrimitive?.contentOrNull
        // 2. 便捷字段 output_text 存在时直接用（官方 SDK 的行为，服务端也可能直返）
        resp["output_text"]?.jsonPrimitive?.contentOrNull?.let {
            return LlmResult(it, stopReason(status, resp), usage(resp))
        }
        // 3. 标准路径：遍历 output 数组
        val text = resp["output"]?.jsonArray
            ?.filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "message" }
            ?.flatMap { it.jsonObject["content"]?.jsonArray ?: JsonArray(emptyList()) }
            ?.filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "output_text" }
            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
            ?.takeIf { it.isNotBlank() }
            ?: throw LlmException(-1, "Responses 输出中未找到 output_text: ${resp.toString().take(300)}", false)
        return LlmResult(text, stopReason(status, resp), usage(resp))
    }

    private fun stopReason(status: String?, resp: JsonObject) = when {
        status == "incomplete" &&
            resp["incomplete_details"]?.jsonObject?.get("reason")?.jsonPrimitive?.contentOrNull == "max_output_tokens" ->
            StopReason.MAX_TOKENS
        status == "completed" -> StopReason.COMPLETE
        else -> StopReason.OTHER
    }
    private fun usage(resp: JsonObject) =
        resp["usage"]?.jsonObject?.get("total_tokens")?.jsonPrimitive?.intOrNull
}
```

**兼容性说明**：`json_schema strict` 不被某些中转支持时，捕获 400 → 降级为 `format: {"type": "json_object"}` 重试；推理模型（o 系/GPT-5 reasoning）会额外出现 `type=="reasoning"` 的 output 项，解析逻辑已天然跳过。

### 7.5 适配器三：Anthropic Messages API

**协议要点**（与前两者差异最大，重点核对）：
- `POST {baseUrl}/v1/messages`；认证头是 **`x-api-key`**（不是 Bearer），另必须带 **`anthropic-version: 2023-06-01`**。
- 请求体 **`system` 是顶层字段**，`messages` 只允许 user/assistant 交替；`content` 用数组块 `[{"type":"text","text":...}]`。
- **`max_tokens` 必填**。
- 没有 `response_format`；强制 JSON 的可靠做法是 **assistant 预填（prefill）**：在 messages 末尾追加 `{"role":"assistant","content":[{"type":"text","text":"{"}]}`，模型会从 `{` 之后续写，解析时把 `{` 拼回去。
- 响应结构：`{"content":[{"type":"text","text":"..."}], "stop_reason":"end_turn"|"max_tokens", "usage":{...}}`。

```kotlin
class AnthropicMessagesAdapter @Inject constructor(client: OkHttpClient, json: Json) :
    AbstractLlmAdapter(client, json) {

    override val protocol = LlmProtocol.ANTHROPIC_MESSAGES

    override suspend fun complete(endpoint: LlmEndpoint, request: LlmRequest): LlmResult {
        val url = endpoint.baseUrl.trimEnd('/') + "/v1/messages"
        val body = buildJsonObject {
            put("model", endpoint.model)
            put("max_tokens", request.maxTokens)              // 必填
            put("system", request.system)                     // 顶层 system，不进 messages
            put("temperature", request.temperature)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject { put("type", "text"); put("text", request.user) }
                    }
                }
                // JSON 预填：强制模型从 '{' 后续写，等价于 json_mode
                addJsonObject {
                    put("role", "assistant")
                    putJsonArray("content") {
                        addJsonObject { put("type", "text"); put("text", "{") }
                    }
                }
            }
        }
        val resp = post(url, mapOf(
            "x-api-key" to endpoint.apiKey,
            "anthropic-version" to "2023-06-01",
            "Content-Type" to "application/json"
        ), body)
        return parse(resp)
    }

    private fun parse(resp: JsonObject): LlmResult {
        val partial = resp["content"]?.jsonArray
            ?.filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "text" }
            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
            ?: throw LlmException(-1, "Anthropic 响应缺少 content: ${resp.toString().take(300)}", false)
        val stop = resp["stop_reason"]?.jsonPrimitive?.contentOrNull
        val usage = resp["usage"]?.jsonObject?.let {
            (it["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0) +
                (it["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0)
        }
        return LlmResult(
            text = "{" + partial.trimStart(),   // 拼回 prefill 的 '{'
            stopReason = if (stop == "max_tokens") StopReason.MAX_TOKENS else StopReason.COMPLETE,
            usageTokens = usage
        )
    }

    override fun parseErrorMessage(body: String): String =
        runCatching {
            Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")
                ?.jsonPrimitive?.content
        }.getOrNull() ?: super.parseErrorMessage(body)
}
```

> 注意：Anthropic 官方 API 默认不开 CORS/移动端直连讨论与本机 App 无关（原生 OkHttp 无跨域限制），但**官方 API Key 直接放客户端有泄露风险**——需求已要求本地加密存储（§5），商用版本建议自建中转。

### 7.6 三协议差异速查表

| 维度 | Chat Completions | Responses | Anthropic Messages |
|---|---|---|---|
| 端点 | `/v1/chat/completions` | `/v1/responses` | `/v1/messages` |
| 认证 | `Authorization: Bearer` | 同左 | `x-api-key` + `anthropic-version` |
| system 位置 | `messages[0].role=system` | 顶层 `instructions` | 顶层 `system` |
| 强制 JSON | `response_format` | `text.format=json_schema` | assistant 预填 `"{"` |
| 取结果 | `choices[0].message.content` | `output[*].content[output_text].text` | `content[*].text`（拼回 `{`） |
| 截断信号 | `finish_reason=length` | `status=incomplete`+reason | `stop_reason=max_tokens` |
| max_tokens 必填 | 否 | 否（`max_output_tokens`） | **是** |
| Embedding | 与聊天协议解耦，独立配置任意 OpenAI 兼容端点（见 §9.4） | 同左 | 同左 |

### 7.7 契约测试（MockWebServer）

每个适配器至少 3 个测试：正常响应解析、HTTP 错误映射、截断信号识别。示例：

```kotlin
class OpenAiChatAdapterTest {
    private val server = MockWebServer()
    private val adapter = OpenAiChatAdapter(OkHttpClient(), Json { ignoreUnknownKeys = true })

    @Test fun `解析标准响应`() = runTest {
        server.enqueue(MockResponse().setBody("""
          {"choices":[{"message":{"content":"{\"title\":\"测试\"}"},"finish_reason":"stop"}],
           "usage":{"total_tokens":42}}"""))
        server.start()
        val result = adapter.complete(
            LlmEndpoint(server.url("/").toString().trimEnd('/'), "k", "m", LlmProtocol.OPENAI_CHAT),
            LlmRequest(system = "s", user = "u", jsonSchemaName = "note"))
        assertEquals("{\"title\":\"测试\"}", result.text)
        assertEquals(StopReason.COMPLETE, result.stopReason)
        // 同时断言请求构建正确：路径、认证头、response_format
        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer k", recorded.getHeader("Authorization"))
        assertTrue(recorded.body.readUtf8().contains("\"response_format\""))
        server.shutdown()
    }

    @Test fun `429 映射为可重试错误`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{\"error\":{\"message\":\"rate limited\"}}"))
        server.start()
        val e = assertFailsWith<LlmException> {
            adapter.complete(LlmEndpoint(server.url("/").toString().trimEnd('/'), "k", "m",
                LlmProtocol.OPENAI_CHAT), LlmRequest("s", "u", "note"))
        }
        assertTrue(e.retriable); server.shutdown()
    }
}
```

Anthropic 适配器额外测试：断言请求头含 `x-api-key` 与 `anthropic-version`、请求体 messages 末位是 assistant 预填；响应解析后文本以 `{` 开头。

## 8. AI 处理流水线与 Prompt 设计

### 8.1 流水线编排

```kotlin
// ai/pipeline/AiProcessWorker.kt —— WorkManager 保证进程被杀后仍执行，网络断开自动重试
@HiltWorker
class AiProcessWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted params: WorkerParameters,
    private val pipeline: AiPipeline
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val noteId = inputData.getLong("noteId", -1L)
        return when (pipeline.process(noteId)) {
            is AiPipeline.Outcome.Done -> Result.success()
            is AiPipeline.Outcome.Retryable -> if (runAttemptCount < 5) Result.retry() else Result.failure()
            is AiPipeline.Outcome.Fatal -> Result.failure()   // 如 401：重试无意义，等用户改配置
        }
    }
}

// 入队：唯一工作名防重复，网络约束
fun enqueue(noteId: Long) {
    val req = OneTimeWorkRequestBuilder<AiProcessWorker>()
        .setInputData(workDataOf("noteId" to noteId))
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()
    WorkManager.getInstance(context)
        .enqueueUniqueWork("ai_process_$noteId", ExistingWorkPolicy.KEEP, req)
}

// ai/pipeline/AiPipeline.kt
class AiPipeline @Inject constructor(
    private val gateway: LlmGateway,
    private val notes: NoteRepository,
    private val todos: TodoRepository,
    private val linkDiscovery: LinkDiscovery,
    private val reminder: ReminderScheduler
) {
    sealed interface Outcome { object Done : Outcome; object Retryable : Outcome; object Fatal : Outcome }

    suspend fun process(noteId: Long): Outcome {
        val note = notes.byId(noteId) ?: return Outcome.Fatal
        // 步骤 1：一次调用完成“意图分类 + 结构化抽取”（笔记/待办两种 schema 合一）
        val result = try {
            gateway.complete(LlmRequest(
                system = Prompts.INTENT_AND_ORGANIZE,
                user = buildString {
                    append("当前时间："); append(TimeUtils.nowString()); append('\n')
                    append("用户常用分类（按使用频次）："); append(notes.topCategories()); append('\n')
                    append("用户输入原文：\n"); append(note.content)
                },
                jsonSchemaName = "intent"
            ))
        } catch (e: LlmException) {
            notes.markFailed(noteId)
            return if (e.retriable) Outcome.Retryable else Outcome.Fatal
        }
        if (result.stopReason == StopReason.MAX_TOKENS) notes.markFailed(noteId) // 提示用户换长输出模型

        return when (val parsed = JsonExtractor.extractIntent(result.text)) {
            is ParsedIntent.Todo -> {
                val todoId = todos.insertFrom(parsed, sourceNoteId = noteId)
                todos.byId(todoId)?.remindAt?.let { reminder.schedule(todoId, it) }
                notes.delete(noteId)          // 原始输入迁移进 todo.content，笔记不重复留存
                Outcome.Done
            }
            is ParsedIntent.Note -> {
                notes.applyOrganization(noteId, parsed)   // 标题/分类/类型/情绪/标签/摘要 → status=READY
                linkDiscovery.discoverFor(noteId)          // 步骤 2：关联发现（§9）
                Outcome.Done
            }
            ParsedIntent.Unparseable -> { notes.markFailed(noteId); Outcome.Retryable }
        }
    }
}
```

### 8.2 System Prompt（内置，支持后续用户自定义覆写）

```kotlin
object Prompts {
    val INTENT_AND_ORGANIZE = """
你是一个个人笔记整理助手。用户会提供一段输入文本（可能来自语音输入法，可能有口语、重复、识别错字）。
请先纠正明显错字，再判断意图并只输出一个 JSON 对象，不要输出任何其他文字。

意图 A：灵感/想法/随笔/记录 → 输出：
{
  "intent": "note",
  "title": "≤15字的精准标题",
  "content": "整理后的正文（保留原意，去除口头禅，分段清晰）",
  "category": "主题分类，从用户常用分类中选，都不合适才新建",
  "type": "灵感|总结|摘录|待研究|日记 之一",
  "mood": "积极|中立|消极",
  "tags": ["3-5个精准关键词"],
  "summary": "一句话摘要"
}
意图 B：待办/计划/提醒 → 输出：
{
  "intent": "todo",
  "content": "任务内容（动宾结构，可执行）",
  "priority": 0或1或2,
  "deadline": "yyyy-MM-dd HH:mm，无明确时间则省略该字段",
  "remind_lead_minutes": 提前提醒分钟数，用户未指定则省略
}
时间词（明天/下周三/下班前）一律以用户提供的“当前时间”为基准换算成绝对时间。
""".trimIndent()

    val LINK_JUDGE = """…（输入新笔记摘要 + 候选笔记列表，输出 {"related":[{"id":17,"reason":"…"}]}）…"""

    fun schemaFor(name: String): JsonObject = when (name) {
        "intent" -> INTENT_JSON_SCHEMA   // 供 Responses json_schema strict 使用
        "link" -> LINK_JSON_SCHEMA
        else -> JsonObject(emptyMap())
    }
}
```

**设计要点**：
- 意图分类与结构化抽取**合并为一次调用**：速记场景对延迟敏感，且输入短小，一次调用足够稳定。
- `topCategories()` 把用户历史分类（`categories.usage_count` 排序）注入 Prompt，即需求中"AI 学习用户分类习惯"的实现方式——不微调模型，用上下文注入，零成本且即时生效。
- 时间换算显式传入当前时间，避免模型幻觉日期。

### 8.3 JSON 兜底解析（健壮性关键）

即使三方协议都声明了 JSON 约束，中转代理/小模型仍可能输出杂质。统一走三层兜底：

```kotlin
object JsonExtractor {
    fun extractIntent(raw: String): ParsedIntent {
        val json = firstJsonObject(raw) ?: return ParsedIntent.Unparseable
        return runCatching { decode(json) }.getOrElse { ParsedIntent.Unparseable }
    }

    /** 从任意文本中抠出第一个完整 JSON 对象（处理 ```json 包裹、前后废话、Anthropic 预填拼接） */
    fun firstJsonObject(raw: String): JsonObject? {
        var s = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        if (!s.startsWith("{")) s = "{" + s.substringAfter('{', "")   // 预填场景
        val start = s.indexOf('{'); if (start < 0) return null
        var depth = 0; var inStr = false; var esc = false
        for (i in start until s.length) {
            val c = s[i]
            if (esc) { esc = false; continue }
            when (c) {
                '\\' -> if (inStr) esc = true
                '"' -> inStr = !inStr
                '{' -> if (!inStr) depth++
                '}' -> if (!inStr && --depth == 0)
                    return runCatching { Json.parseToJsonElement(s.substring(start, i + 1)).jsonObject }.getOrNull()
            }
        }
        return null
    }
}
```

配套单测覆盖：纯 JSON、markdown 包裹、前后带废话、预填缺 `{`、字符串内含 `}` 五种场景。

## 9. 智能关联发现（算法流程）

### 9.1 总流程（每条新笔记）

```
新笔记 READY
   │
   ▼
① Embedding 计算并入库（若当前协议支持）        ← 失败不阻塞，走降级
   │
   ▼
② 候选召回（三路并集，去重，上限 20 条）
     a) 向量余弦 Top-K（K=10，阈值 ≥0.72）
     b) 标签 Jaccard：|A∩B|/|A∪B| ≥ 0.34 的历史笔记
     c) 实体重合：从新笔记抽出的人名/项目名（复用 tags 中的专名）在旧笔记正文出现
   │
   ▼
③ LLM 复核：新笔记摘要 + 候选 (id,title,summary) 列表 → 返回 [{id, reason}]
     （仅当候选 >0 时才调用，控制成本；单次 Prompt 控制在 2k token 内）
   │
   ▼
④ 落库：LLM 确认的关联，或向量分 ≥0.90 的高置信关联
     → note_links 写入双向两行 (a→b, b→a)，score = 0.6*cos + 0.4*(llm确认?1:0)
```

### 9.2 核心实现

```kotlin
// ai/pipeline/LinkDiscovery.kt
class LinkDiscovery @Inject constructor(
    private val notes: NoteDao, private val links: LinkDao,
    private val embeddings: EmbeddingDao, private val embeddingClient: EmbeddingClient,
    private val gateway: LlmGateway
) {
    suspend fun discoverFor(noteId: Long) {
        val note = notes.byId(noteId) ?: return
        // ①
        val newVec = embeddingClient.embedOrNull(note.title + "\n" + note.summary.orEmpty())
            ?.also { embeddings.upsert(NoteEmbeddingEntity(noteId, it.toBytes(), embeddingClient.model)) }

        // ②
        val candidates = mutableMapOf<Long, Float>() // id → 召回分
        if (newVec != null) {
            embeddings.all().asSequence()
                .filter { it.noteId != noteId }
                .map { it.noteId to cosine(newVec, it.vector.toFloatArray()) }
                .filter { it.second >= 0.72f }
                .sortedByDescending { it.second }.take(10)
                .forEach { (id, s) -> candidates.merge(id, s, ::maxOf) }
        }
        tagsOf(noteId).forEach { tag ->
            links.notesSharingTag(noteId, tag).forEach { (id, jaccard) ->
                if (jaccard >= 0.34f) candidates.merge(id, jaccard * 0.9f, ::maxOf)
            }
        }
        if (candidates.isEmpty()) return

        // ③
        val digests = notes.allDigests(excludeId = noteId).filter { it.id in candidates.keys }
        val judged = runCatching {
            gateway.complete(LlmRequest(
                system = Prompts.LINK_JUDGE,
                user = buildLinkPrompt(note, digests),
                jsonSchemaName = "link", maxTokens = 512
            ))
        }.getOrNull()?.let { JsonExtractor.extractLinks(it.text) } ?: emptyList()

        // ④
        val now = System.currentTimeMillis()
        val rows = mutableListOf<NoteLinkEntity>()
        for ((id, reason) in judged) {
            val score = 0.6f * (candidates[id] ?: 0f) + 0.4f
            if (score < 0.55f) continue
            rows += NoteLinkEntity(noteId, id, score, reason, createdAt = now)
            rows += NoteLinkEntity(id, noteId, score, reason, createdAt = now)  // 双向
        }
        // 高置信直通：向量分极高即使 LLM 未返回也建链
        candidates.filter { it.value >= 0.90f && it.key !in judged.map { j -> j.id } }
            .forEach { (id, s) ->
                rows += NoteLinkEntity(noteId, id, s, "语义高度相似", createdAt = now)
                rows += NoteLinkEntity(id, noteId, s, "语义高度相似", createdAt = now)
            }
        if (rows.isNotEmpty()) links.upsertAll(rows)
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in 0 until minOf(a.size, b.size)) { dot += a[i]*b[i]; na += a[i]*a[i]; nb += b[i]*b[i] }
        return if (na == 0f || nb == 0f) 0f else dot / (sqrt(na.toDouble()) * sqrt(nb.toDouble())).toFloat()
    }
}
```

### 9.3 阈值与调参说明

| 阈值 | 含义 | 调参方向 |
|---|---|---|
| 0.72 | 向量候选召回线 | 召回太少降到 0.65；噪音多升到 0.78 |
| 0.34 | 标签 Jaccard 召回线（约 2 个共现标签） | 笔记标签稀疏时降到 0.2 |
| 0.90 | 向量高置信直通线 | 误关联多就升到 0.93，并关掉直通全走 LLM |
| 0.55 | 综合分落库线 | 详情页"相关笔记"只展示 ≥0.55，可在 UI 再过滤 |

定时全量增量分析（需求 3.1 的可选项）：`PeriodicWorkRequest` 每 24h 一次，只对 `createdAt > lastFullScan` 的笔记跑 `discoverFor`；全量重建留一个设置页入口（"重建知识网络"，进度条 + 可取消）。

### 9.4 Embedding 配置与降级链路

`EmbeddingClient` 不再依赖聊天协议，只读独立的 `EmbeddingEndpoint` 配置：

```kotlin
// ai/embedding/EmbeddingClient.kt
class EmbeddingClient @Inject constructor(
    private val client: OkHttpClient,
    private val settings: SettingsRepository
) {
    /** 未配置或调用失败均返回 null，由 LinkDiscovery 走降级召回，绝不影响主流程 */
    suspend fun embedOrNull(text: String): FloatArray? {
        val ep = settings.embeddingEndpoint()
        if (!ep.enabled || ep.baseUrl.isBlank() || ep.model.isBlank()) return null
        val body = """{"model":"${ep.model}","input":${Json.encodeToString(text)}}"""
        val req = Request.Builder()
            .url(ep.baseUrl.trimEnd('/') + "/v1/embeddings")
            .header("Authorization", "Bearer ${ep.apiKey}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val root = Json.parseToJsonElement(resp.body!!.string()).jsonObject
                root["data"]!!.jsonArray.first().jsonObject["embedding"]!!.jsonArray
                    .map { it.jsonPrimitive.float }.toFloatArray()
            }
        }.getOrNull()
    }
    val model: String get() = runBlocking { settings.embeddingEndpoint().model } // 仅用于标记向量来源
}
```

- 配置来源举例：OpenAI 官方（`text-embedding-3-small`）、硅基流动（`BAAI/bge-m3`）、Jina（`jina-embeddings-v3`）、本地 Ollama（`nomic-embed-text`，端点为 `http://<host>:11434/v1`）——均为 OpenAI 兼容格式，一套代码通吃。
- **维度漂移处理**：`note_embeddings.model` 记录来源模型；设置里更换模型后，设置页提示"需重建向量索引"，一键清空 `note_embeddings` 并后台批量重算（不同模型/维度不可混算余弦）。
- 未配置时候选召回仅靠 (b)(c) 两路 + LLM 复核，功能不退化、只是召回率下降；设置页提示文案："未配置 Embedding 服务，关联发现使用标签+AI 分析模式"。

## 10. 待办提醒（AlarmManager）

```kotlin
// reminder/ReminderScheduler.kt
class ReminderScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val am = context.getSystemService(AlarmManager::class.java)

    fun schedule(todoId: Long, triggerAt: Long) {
        if (triggerAt <= System.currentTimeMillis()) return
        val pi = pendingIntent(todoId)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            // 引导用户授权；未授权则降级窗口闹钟
            am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60_000L, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }
    fun cancel(todoId: Long) = am.cancel(pendingIntent(todoId))

    private fun pendingIntent(todoId: Long) = PendingIntent.getBroadcast(
        context, todoId.toInt(),
        Intent(context, ReminderReceiver::class.java).putExtra("todoId", todoId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

// reminder/ReminderReceiver.kt —— 到期发通知；BootReceiver 开机后对所有未完成的待办重排
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra("todoId", -1L); if (todoId < 0) return
        goAsync {
            val todo = appGraph.todoRepository.byId(todoId) ?: return@goAsync
            if (!todo.done) NotificationHelper.showTodoReminder(context, todo)  // 含"完成""延期 10 分钟"两个 action
        }
    }
}
```

注意点：
- API 33+ 首次进入待办页时请求 `POST_NOTIFICATIONS`；API 31+ 引导开启精确闹钟权限（`Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`）。
- `remindAt = deadline - remindLeadMinutes` 在保存/修改待办时重算并 `schedule`；完成/删除时 `cancel`。
- 通知 action "完成" 直接改库并取消闹钟；"延期" 弹出时间选择或 +10min 快速操作。

## 11. UI/UX 设计规范与实现要点

> **设计基准**：UI 实现以「声念」设计稿为唯一视觉基准。本地镜像 `docs/design/shengnian-ui.html`（设计规范区 + 5 屏画板，浏览器直接打开）；Open Design 项目 `ai-ui-c6c9` 可持续迭代，迭代后重新导出覆盖本地镜像。**视觉呈现以设计稿为准，功能流程以本计划为准。**

### 11.1 品牌

- 名称：**声念**；slogan：**声落成章，念起成行**。应用显示名用「声念」；包名维持 `com.voiceink.app` 不变（避免改名成本）。
- 产品气质：简洁、优雅、现代、有呼吸感；**不出现任何录音/声波元素**（App 无录音功能，设计稿原则一即「不画录音」）。

### 11.2 设计 Token（`ui/theme/` 的直接输入）

| Token | 值 | 用途 |
|---|---|---|
| 纸白 `#FAFAF7` | 主背景（Scaffold background） |
| 米灰 `#F5F4F0` | 次级面 / 输入框填充 |
| 卡面 `#FFFFFF` | 卡片 |
| 墨 `#1A1A1A` | 主文字 |
| 灰 `#8C8A84` | 辅助文字 |
| 分隔 `#EAE8E2` | 1px 极淡分隔线 |
| 紫罗兰 `#6B5CE7` | **唯一点缀色，每屏 ≤2 处**：FAB、听写引导、关键状态（AI 摘要标识、进度条、峰值高亮） |

- **字体**：标题 / 编辑器正文 = 衬线（Noto Serif SC，回退系统 Noto Serif CJK，600）；界面 / 正文 = 无衬线（Inter / 系统默认）；数据与时间 = Inter 300 + `tabular-nums`。
- **字号阶梯**：26 / 23 / 18.5 / 16 / 13.5 / 12.5 / 10.5 sp，行高 1.45–1.92。
- **圆角**：chip 7 / 小件 10 / 输入 14 / 卡片 18 / 大容器 22 dp。
- **投影**：卡片 `y1 + y6 blur18` 低透明度；FAB 带紫光晕。Android 用 `tonalElevation` + 极淡 shadow 近似。

三条设计原则（并入代码评审 checklist）：**不画录音**；**单一点缀色**（其余交给墨与灰阶）；**留白即节奏**（衬线承担「写」，无衬线承担「用」，卡片之间留够呼吸）。

### 11.3 屏幕映射（设计稿 → App 路由）

| 设计稿 | App 屏幕 | 说明 |
|---|---|---|
| 屏 01 首页 · 灵感笔记流 | `HomeScreen`（首页 Tab） | 品牌区 + 搜索 + 分类 Chip + 按「今天/昨天」分组的笔记卡片流；底部中央 FAB「记录灵感」悬浮于 Tab 栏之上；右上角「念」头像 → 设置页 |
| 屏 02 快速记录页 | `CaptureScreen` | FAB 进入，进入即弹键盘；衬线编辑区 + AI 提示条；键盘麦克风光晕为首启 Coach Mark 示意（见 §6.3） |
| 屏 03 笔记详情页 | `NoteDetailScreen` | AI 摘要卡（紫罗兰淡底）、带小标题的整理正文、标签 Chip、「AI 提炼的待办」列表（一键加入/已加入态） |
| 屏 04 智能待办页 | `TodoScreen`（待办 Tab） | 顶部日期 + 完成进度条；「今天/接下来」分组卡片；优先级标（高=紫罗兰淡底，中=米灰）；来源笔记回溯链接；圆形勾选完成态 |
| 屏 05 洞察页 | `InsightsScreen`（洞察 Tab） | 记录条数/提炼待办/完成率三 tile、连续记录点阵、24h 时段分布柱状（峰值紫罗兰）、灵感关键词云；阶段 6 实现，数据全部来自 Room 聚合 |
| —（设计稿未覆盖） | `SettingsScreen` | 风格沿用 token 即可：米灰分组卡 + 墨字，点缀色仅用于「测试连接」等关键动作 |

导航结构：**3 Tab（首页 / 待办 / 洞察）+ 中央 FAB 直达速记页**，替代早期「四 Tab」方案；设置页从首页右上角进入。

### 11.4 各屏功能实现要点（视觉细节一律回看设计稿）

- **首页（笔记流）**：条目显示标题/摘要/标签/时间与「N 条待办」关联计数；`PENDING_AI` 状态的笔记以输入原文前 40 字为临时标题并带「整理中」角标——保证无网络时也完全可用；保存后顶部状态条显示 "AI 整理中…"，完成后短震动（`VibratorManager`）+ 变 "已保存"。
- **速记页**：进入即弹键盘；保存后立即清空、可连续输入；AI 提示条常驻编辑区下方。
- **笔记详情**：标题、分类/类型/情绪 Chip、AI 摘要卡、正文、标签行、「AI 提炼的待办」（一键加入）、「相关笔记」横向卡片（显示 score 对应的相关性强度与 reason，可手动 `deleteBidirectional` 解除）。
- **待办页**：勾选完成（圆形 checkbox + 删除线）、左滑删除、编辑截止时间/提前量；来源链接可点击跳回笔记详情。
- **洞察页**：纯本地聚合（Room SQL + Canvas 绘制）；时段分布按 1h 桶聚合 `notes.createdAt`；关键词云取近 30 天标签频次 Top10。
- **设置页**：三个配置区块——① 聊天 LLM：协议类型单选（三选一）→ BaseUrl / ApiKey / Model 表单 → "测试连接"按钮；② Embedding（可折叠，默认关闭）：开关 + BaseUrl / ApiKey / 模型名 + "测试连接" + 换模型后的"重建向量索引"入口；③ 通用：关联发现开关、"打开直接进速记"开关、默认提前提醒分钟数、数据导出。

## 12. MVP 任务分解（对应需求六阶段）

> 每个阶段交付可运行的 App；每步完成后 commit。阶段内任务按 2–5 分钟粒度在执行会话中继续拆分（TDD：适配器/解析器/算法均先写测试）。

### 阶段 1：快速输入 + 落库
- [ ] 1.1 建工程、依赖、Manifest、Compose 三 Tab + 中央 FAB 骨架（导航结构按 §11.3）
- [ ] 1.2 按 §11.2 建立 `VoiceInkTheme`（色板/字体/圆角/投影 token 落 `Color.kt/Type.kt`）
- [ ] 1.3 Room 初始化 + `NoteEntity/NoteDao.insert` + 首页笔记流（仅原文展示，视觉按设计稿屏 01）
- [ ] 1.4 `ApiKeyStore` + 设置页骨架（LLM BaseUrl/Key/Model 录入）
- [ ] 1.5 `CaptureScreen` + `CaptureController`：FAB 进入即弹键盘 → 保存 → 落库 → 列表可见（视觉按设计稿屏 02）
- [ ] 1.6 `ShareIngestActivity`：系统分享文本直接建笔记 + 桌面快捷方式
- **验收**：冷启动到首页可交互 ≤1s，FAB 到键盘弹出 ≤300ms；输入保存后 100ms 内列表出现原文笔记（不等 AI）；从微信分享一段文字可静默入库；对照设计稿屏 01/02 完成首次视觉走查（色板、圆角、字体、FAB 位置）。

### 阶段 2：意图分流 + 待办提取（Chat Completions 优先）
- [ ] 2.1 `LlmModels`/`AbstractLlmAdapter`/`LlmGateway` + MockWebServer 测试
- [ ] 2.2 `OpenAiChatAdapter`（含 400 降级重试）+ 契约测试
- [ ] 2.3 `Prompts.INTENT_AND_ORGANIZE` + `JsonExtractor` + 五种脏输入单测
- [ ] 2.4 `AiPipeline` + `AiProcessWorker`（唯一任务名、指数退避）
- [ ] 2.5 `TodoEntity/TodoDao` + 待办分支写库
- **验收**：输入"明天下午三点前把周报发给王总"→ 待办出现，deadline 为次日 15:00；输入"我突然想到一个点子…"→ 笔记被整理出标题和标签。

### 阶段 3：待办列表 + 提醒
- [ ] 3.1 待办列表 UI（今天/接下来分组、圆形勾选完成、左滑删除、编辑截止时间/提前量、来源笔记回溯链接——视觉按设计稿屏 04）
- [ ] 3.2 `ReminderScheduler`/`ReminderReceiver`/`NotificationHelper` + 权限引导
- [ ] 3.3 `BootReceiver` 重排 + 完成/删除联动取消
- **验收**：设 2 分钟后提醒，杀进程仍准时响；重启手机后提醒不丢。

### 阶段 4：笔记整理完备 + 筛选
- [ ] 4.1 笔记详情页（AI 摘要卡 + 整理正文 + 提炼待办一键加入——视觉按设计稿屏 03）+ 首页筛选（分类 Chip/标签/关键词/时间）
- [ ] 4.2 分类体系表 + `topCategories()` 注入 Prompt（用户改分类后 `usageCount` 更新）
- [ ] 4.3 `AI_FAILED` 笔记的"重试整理"入口
- **验收**：连续输入 10 条不同主题内容，分类/标签/情绪合理，筛选可用。

### 阶段 5：关联发现
- [ ] 4.1 `EmbeddingEndpoint` 独立配置（URL/Key/模型名，Key 入 Keystore）+ `EmbeddingClient` + `NoteEmbeddingEntity` + cosine 单测 + "测试连接"
- [ ] 4.2 三路候选召回 + `LINK_JUDGE` 复核 + 双向写库
- [ ] 4.3 详情页"相关笔记"区 + 手动解除关联
- [ ] 4.4 每日增量扫描 Worker + 设置页"重建知识网络"
- **验收**：先输入"想做一个灵感笔记 App"，隔天再输入"灵感笔记的关联功能可以这样做"，详情页互相关联且理由可读。

### 阶段 6：多协议 + 收尾
- [ ] 5.1 `OpenAiResponsesAdapter` + 契约测试（output_text 路径、incomplete 状态）
- [ ] 5.2 `AnthropicMessagesAdapter` + 契约测试（预填拼接、stop_reason）
- [ ] 5.3 设置页：聊天协议切换 + Embedding 独立配置区块 + 双路"测试连接" + 未配置 Embedding 时的降级提示
- [ ] 5.4 Markdown/JSON 导出备份
- [ ] 5.5 洞察页（设计稿屏 05）：记录条数/完成率/连续记录/时段分布/关键词云，纯 Room 聚合 + Canvas 绘制（可裁剪，工作量超预算时移入下个迭代）
- **验收**：三套真实端点（OpenAI 官方、DeepSeek、Claude 官方）各完成一次完整"输入→整理→关联"链路；全 App 对照设计稿完成终末视觉走查（含 3 Tab + FAB 导航、单一点缀色约束）。

## 13. 集成前必须人工核对的事项（防止凭记忆写错）

1. 用户自填的中转服务对 `response_format` / `json_schema` / `max_tokens` 字段的兼容性 → 三个适配器均有降级重试，但需在联调时确认降级路径触发正常。
2. `max_completion_tokens` vs `max_tokens`：按目标模型代际确认默认字段。
3. 用户自填 Embedding 服务的返回结构是否与 OpenAI `data[0].embedding` 一致（个别服务有差异）→ `EmbeddingClient` 联调时抓包确认。
4. 目标设备厂商（小米/华为等）对 AlarmManager 精确闹钟与自启动的限制 → 设置页提供"收不到提醒？"引导页（自启动/电池白名单）。



