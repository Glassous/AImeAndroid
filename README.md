这是一个根据您提供的完整项目文件重写的 `README.md`。

这个版本更新了功能列表，以包含代码库中支持的特定功能（如联网搜索、Markdown/LaTeX 渲染、高级 UI 自定义），并修正了技术栈和构建要求（例如 `minSdk = 33`），使其与您的 `build.gradle.kts` 和其他配置文件保持一致。

-----

# AIme - 现代 AI 聊天助手

一个基于 Jetpack Compose 构建的现代化、高度可定制的 Android AI 聊天应用。支持多种 AI 模型、工具调用和云端同步功能。

## ✨ 功能特点

### 🤖 强大的聊天体验

  * **多模型支持:** 灵活配置和切换兼容 OpenAI API 的多种 AI 模型（如 OpenAI, OpenRouter, 阿里云通义千问, DeepSeek 等）。
  * **工具调用 (Function Calling):** 支持模型调用工具。已内置 **联网搜索** 功能，可获取实时信息。
  * **流式响应:** 支持打字机效果的流式文本响应。
  * **Markdown 渲染:** 完整支持 Markdown，包括代码块、表格和 LaTeX 数学公式。
  * **会话管理:** 支持新建、删除、重命名对话。
  * **消息操作:** 支持编辑用户消息并重新生成、重新生成 AI 回复、复制、查看详情。

### 🎨 高度可定制的 UI

  * **Material 3 设计:** 现代 M3 风格，支持动态取色 (Dynamic Color)。
  * **沉浸式界面:** 支持边缘到边缘 (Edge-to-Edge) 显示。
  * **主题切换:** 支持浅色、深色及跟随系统主题。
  * **极简模式:** 可逐个隐藏 UI 元素（如菜单、工具图标、欢迎语、输入框边框等）。
  * **显示设置:**
      * **全屏显示:** 支持聊天页面全屏及极简模式下全局全屏。
      * **字体大小:** 自由调节聊天字体大小。
      * **UI 透明度:** 调节聊天页面顶部和底部栏的透明度。
      * **AI 气泡:** 可选是否为 AI 的回复显示消息气泡。

### ☁️ 数据同步与备份

  * **云端同步:** 支持配置阿里云 OSS，实现自动或手动将所有数据（模型、会话）上传到云端或从云端恢复。
  * **本地备份:** 支持将所有数据导出为本地 JSON 文件，或从本地文件导入。
  * **兼容导入:** 支持从旧版 `AImeBackup.json` 格式文件导入数据。

## 🛠️ 技术架构

### 核心技术栈

  * **开发语言:** Kotlin
  * **UI 框架:** Jetpack Compose
  * **架构模式:** MVVM + Repository
  * **异步处理:** Kotlin Coroutines + Flow
  * **依赖注入:** 手动依赖注入 (通过 `Application` 类)

### 主要依赖库

  * **UI:** Jetpack Compose, Navigation Compose, Material 3
  * **Architecture:** ViewModel, LiveData (隐式), Coroutines, Flow
  * **Data:** Room (数据库), DataStore (用户偏好)
  * **Network:** OkHttp, Retrofit, Gson (用于 `OpenAiService` 和 `WebSearchService`)
  * **Serialization:** Kotlinx Serialization (用于 DataStore 配置)
  * **Cloud:** 阿里云 OSS SDK
  * **Markdown:** Markwon (Core, LaTeX, Tables)
  * **Web Scraping:** Jsoup (用于联网搜索)
  * **Annotation Processing:** KSP (用于 Room)

### 📂 项目结构

```
app/src/main/java/com/glassous/aime/
├── data/                    # 数据层
│   ├── dao/                 # Room 数据访问对象
│   ├── model/               # 数据模型 (Room 实体, 备份, 工具)
│   ├── preferences/         # DataStore 用户偏好
│   ├── repository/          # 数据仓库
│   ├── OpenAiService.kt     # OpenAI 流式 API 服务
│   ├── WebSearchService.kt  # 联网搜索服务
│   └── ChatRepository.kt    # 核心聊天仓库
├── ui/                      # UI 层
│   ├── components/          # 可复用 Compose 组件
│   ├── navigation/          # 导航配置 (NavHost)
│   ├── screens/             # 页面 (Chat, Settings, ModelConfig)
│   ├── settings/            # 特定设置页面 (OSS Config)
│   └── theme/               # 主题和字体
├── viewmodel/               # ChatViewModel
├── ui/viewmodel/            # 其他 ViewModels (Sync, Model, Theme)
├── Application.kt           # 应用程序类 (用于依赖注入)
└── MainActivity.kt          # 主活动 (Compose 入口, 沉浸式处理)
```

## 🚀 安装与使用

### 系统要求

  * **Android 13.0 (API 33) 或更高版本**

### 基本使用

1.  **首次使用:**
      * 打开应用后，应用会尝试预设几个模型分组 (如 OpenRouter, 阿里云, DeepSeek)。
      * 点击顶部的模型名称按钮（或进入“设置” -\> “模型配置”）。
      * 选择一个模型分组，填入您自己的 API 密钥。
      * 返回主界面，选择该模型即可开始聊天。
2.  **聊天功能:**
      * 在输入框中输入您的问题。
      * 点击模型按钮，可以切换模型或选择“工具调用” (如联网搜索)。
      * 长按消息可进行复制、重新生成或编辑。
3.  **管理对话:**
      * 点击左上角菜单按钮打开对话列表。
      * 支持新建、删除、重命名对话。

## ⚙️ 配置说明

### 模型配置

1.  在设置中选择"模型配置"。
2.  点击"添加分组"，填入分组名称、API Base URL 和 API 密钥。
3.  在分组中添加具体模型，填入“显示名称”（如 `GPT-4o`）和“模型名称”（如 `gpt-4o`）。

### 云端同步

1.  在设置中找到"云端同步"。
2.  点击“配置阿里云 OSS”，填入您的 Region, Endpoint, Bucket, AK/SK。
3.  保存后，可启用“自动同步”，或手动点击“上传到云端”/“从云端导入”。

## 🔧 开发与构建

### 构建环境

  * **Android Studio:** (推荐) Koala | 2024.1.1 或更高版本
  * **JDK:** 11 或更高版本
  * **Kotlin:** 2.0.21
  * **Compile SDK:** 36

### 构建步骤

1.  克隆项目到本地。
2.  使用 Android Studio 打开项目。
3.  等待 Gradle 同步完成。
4.  点击运行按钮构建并安装。

## 📄 许可证

本项目采用 Apache License 2.0 许可证。详情请参见 [LICENSE](https://www.google.com/search?q=LICENSE) 文件。