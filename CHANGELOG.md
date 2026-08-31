# Changelog

本项目遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

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
