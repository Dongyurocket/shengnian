# 声念

> 声落成章，念起成行。

一款 Android 原生的 **AI 灵感笔记与智能待办**应用：把「记录」做成最短路径，把「整理」交给 AI。

语音转写交给系统输入法（豆包、讯飞等），App 不内置录音、不申请音频权限——专注做好「说完之后」的事：意图分流、笔记结构化、待办提取与本地提醒、语义关联双向链接。

## 特性

- **极速记录**：首页中央 FAB 直达速记页，进入即弹键盘；系统分享菜单一键收文本；桌面长按快捷方式「新建灵感 / 新建待办」
- **AI 意图分流**：一次调用区分「笔记 / 待办」。待办自动生成截止时间并设本地闹钟；笔记自动整理出标题、分类、类型、情绪、标签、摘要，并提炼其中可执行的待办
- **三协议自由接入**：OpenAI Chat Completions / OpenAI Responses / Anthropic Messages，Base URL 任意填——官方 API、DeepSeek、各类中转、本地 Ollama 均可
- **语义关联网络**：独立的 Embedding 配置（任何 OpenAI 兼容端点），三路召回（向量余弦 / 标签 Jaccard / 实体重合）+ LLM 复核，自动建立笔记双向链接并给出关联理由
- **本地提醒**：AlarmManager 精确闹钟（杀进程可达）、开机重排、通知内「完成 / 延期 10 分钟」
- **洞察页**：记录条数、待办完成率、连续记录点阵、24h 记录时段分布、灵感关键词云——全部纯本地聚合
- **隐私优先**：API Key 存 Android Keystore（AES/GCM），数据全在本地 Room；Markdown + JSON 一键导出备份

## 界面设计

视觉基准：[`docs/design/shengnian-ui.html`](docs/design/shengnian-ui.html)（设计规范 + 5 屏画板，浏览器直接打开）。

三条设计原则：**不画录音**（App 无录音功能）· **单一点缀色**（紫罗兰 `#6B5CE7`，其余交给墨与灰阶）· **留白即节奏**（衬线承担「写」，无衬线承担「用」）。

## 技术栈与架构

Kotlin 2.x · Jetpack Compose (Material3) · Room · WorkManager · Hilt · OkHttp + kotlinx.serialization · Android Keystore

```
UI (Compose) → ViewModel (StateFlow) → 领域层 (CaptureController / AiPipeline / LlmGateway / LinkDiscovery / ReminderScheduler)
                                    → 数据层 (Room × 7 表 / DataStore / Keystore)
```

- **AI 层适配器模式**：`LlmAdapter` 统一接口收敛三协议差异为 `LlmRequest / LlmResult`，JSON 输出三级兜底（schema 约束 → 助手预填 → 解析兜底）
- **离线可靠**：输入先落库（PENDING_AI），AI 流水线走 WorkManager（联网约束 + 指数退避），失败可重试、数据永不丢
- 详细设计决策见 [`docs/plans/2026-08-30-ai-voice-note-app.md`](docs/plans/2026-08-30-ai-voice-note-app.md)

## 构建

要求：JDK 17+，Android SDK（compileSdk 35）。

```bash
./gradlew :app:assembleDebug        # 调试包
./gradlew :app:testDebugUnitTest    # 单元测试（28 个：三协议契约 / JSON 兜底 / 关联算法）
./gradlew :app:assembleRelease      # 发布包（需在 local.properties 配置签名，见下）
```

发布签名（`local.properties`，不会入库）：

```properties
sdk.dir=<Android SDK 路径>
storeFile=<keystore 绝对路径>
storePassword=<...>
keyAlias=<...>
keyPassword=<...>
```

## 配置（首次使用）

设置页（首页右上角「念」进入）：

| 区块 | 说明 | 示例 |
|---|---|---|
| AI 模型 | 协议三选一 + Base URL + API Key + 模型名，「测试连接」验证 | `https://api.deepseek.com` + `deepseek-v4-flash` |
| 语义向量 | 独立开关与端点，任何 OpenAI 兼容 `/v1/embeddings` | `https://api.siliconflow.cn/v1` + `Qwen/Qwen3-Embedding-8B` |
| 通用 | 关联发现开关、打开直进速记、默认提前提醒、重建知识网络、导出备份 | — |

未配置 Embedding 时自动降级为「标签 + AI 分析」关联模式，功能不退化。

## 许可证

[MIT](LICENSE)
