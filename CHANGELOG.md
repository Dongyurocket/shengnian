# Changelog

本项目遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [Unreleased]

后续变更记录将在此处追加。

## [0.3.0] - 2026-08-31

### 新增
- 链接内容提炼：自动扫描笔记中的 HTTP(S) 链接，受限提取页面标题与正文并作为参考资料参与 AI 整理；失败状态与原始链接保留在详情页
- 笔记编辑后二次整理：支持编辑标题/正文、增删图片、仅保存或重新进入异步 AI 流程，重新整理时固定保留为笔记
- 图片识别：速记页和详情页使用 Android Photo Picker 选择图片，复制到应用私有目录后按三种 LLM 协议发送视觉内容
- AI 流程图与思维导图：使用结构化 JSON 校验和 Compose Canvas 原生渲染，不执行 Mermaid、HTML 或模型代码
- 灵感归类与多笔记合并：AI 标记灵感并支持首页筛选；多选 READY 笔记生成新的合并笔记，原笔记及其附件/来源保留
- 导出备份扩展：Markdown + JSON 包含灵感、来源、图表、附件元数据，并复制附件文件；失败附件在结果中明确提示

### 工程
- Room 从 v3 通过显式 Migration 升至 v4，新增附件、来源和图表表；补充 URL/HTML、三协议多模态、图表结构和合并输入测试
- 工程与安全边界：网页抓取改为手动校验、最多 3 次重定向，并拒绝本机/私网/保留地址；固定已校验 DNS 地址、禁用代理/Cookie；异步 AI 写回增加版本快照校验，避免旧请求覆盖用户新编辑
- 单元测试增至 68 个，并新增真实 SQLite v3→v4 Room migration instrumentation test；补充 URL 主机策略、属性顺序解析、重定向边界、三协议多模态、图表结构、合并输入和结构化字段边界测试


## [0.2.1] - 2026-08-31

### 文案与设计
- 重写 README、移动端设计稿与实现计划的产品叙事，突出灵感采集、AI 整理、智能待办、语义关联、洞察和本地优先体验
- 补充声念的核心优势、使用价值和功能说明，统一 Android 端的设计基准与交互表述

### DeepSeek 联调
- Responses `json_schema strict`：所有对象属性完整加入 `required`，并将 `additionalProperties` 设为 `false`，同时补齐关联项的嵌套 Schema，修复 DeepSeek HTTP 400
- DeepSeek V4 三协议请求关闭默认思考模式：Chat / Anthropic 使用 `thinking.type = "disabled"`，Responses 使用 `reasoning.effort = "none"`，为结构化 JSON 保留输出额度
- Anthropic 预填解析兼容完整 JSON 与空 `content`，设置页连接测试按协议响应状态提示“连接成功”，不再把非严格业务文本显示成连接异常
- strict Schema 的空字段和提醒占位值在兜底解析层规范化，不生成空分类，也不覆盖默认提醒设置

### 工程
- 单元测试增至 44 个，补充 DeepSeek strict Schema、三协议思考模式请求、Anthropic 预填边界和连接测试提示回归

## [0.2.0] - 2026-08-31

### 新增
- 设置页「检查更新」：查询 GitHub 最新 Release，展示版本号与更新日志
- 发现 APK 附件时可用 DownloadManager 下载，下载完成后交给系统安装器；无附件时回退到发布页

### 修复
- Responses JSON Schema：修复 `tags` / `todos` 数组字段生成了非法空数组 schema，DeepSeek 等端点不再因 `Invalid json schema` 返回 HTTP 400
- Responses 400 降级：兼容返回 `json schema`（空格）错误的中转端点，自动重试 `json_object`
- 测试连接：推理模型测试额度由 64 提高到 512 token，并区分空响应、非严格 JSON 与真实连接失败
- Android 24 通知兼容：通知渠道仅在 API 26+ 创建，统一使用 `NotificationCompat`

### 工程
- 单元测试增至 35 个，覆盖 JSON Schema 数组结构、GitHub Release 解析与更新版本比较

## [0.1.1] - 2026-08-31

### 新增
- 应用图标：钢笔尖 + 紫罗兰星芒（设计稿品牌色系），Adaptive Icon 前景/背景分层 + 各密度 legacy 位图

### 修复
- Base URL 规范化：用户填写自带 `/v1` 后缀的地址（如硅基流动）时不再拼出 `/v1/v1/…`（真实联调发现）
- release lint：备份规则补 include 前置、移除 WorkManager 默认初始化器（配合 Hilt 按需初始化）

## [0.1.0] - 2026-08-31

首个公开版本（MVP）。

### 新增
- 极速记录：首页中央 FAB 直达速记页（进入即弹键盘）、保存即连续输入、系统分享菜单接收文本、桌面快捷方式「新建灵感 / 新建待办」
- AI 意图分流：一次 LLM 调用区分笔记 / 待办；笔记自动产出标题、分类、类型、情绪、标签、摘要并提炼待办；待办自动换算相对时间为绝对截止时间
- 三协议接入：OpenAI Chat Completions（含 `max_completion_tokens` / `response_format` 400 自动降级）、OpenAI Responses（`json_schema strict` 降级 `json_object`）、Anthropic Messages（assistant 预填强制 JSON）
- 语义关联：Embedding 独立配置（任意 OpenAI 兼容端点），三路召回 + LLM 复核建立双向链接，详情页相关笔记区可跳转 / 解除；每日增量扫描 + 一键重建知识网络
- 本地提醒：AlarmManager 精确闹钟（未授权自动降级窗口闹钟）、通知内完成 / 延期 10 分钟、开机自动重排
- 首页：今天 / 昨天分组笔记流、分类 Chip 过滤、关键词搜索（标题 / 正文 / 标签）、待办计数角标、整理失败一键重试
- 待办页：今天 / 接下来 / 无时间分组、圆形勾选、左滑删除、截止时间与提前量编辑、来源笔记回溯
- 洞察页：记录条数 / 待办完成率 / 连续记录点阵 / 24h 时段分布 / 灵感关键词云，纯本地聚合
- 安全与备份：API Key 存 Android Keystore（AES/GCM），Markdown + JSON 导出备份

### 工程
- 28 个单元测试：三协议适配器 MockWebServer 契约测试、JSON 兜底解析五场景、关联算法阈值
- 构建环境：AGP 8.6.1 / Kotlin 2.0.21 / Gradle 8.9 / JDK 17 / compileSdk 35 / minSdk 24
