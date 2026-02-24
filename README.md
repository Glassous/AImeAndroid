# AIme - Android AI Assistant

AIme 是一个基于 Android (Kotlin + Jetpack Compose) 开发的现代化 AI 聊天助手应用。它支持多种大语言模型（如 OpenAI、豆包等），并内置了丰富的实用工具（联网搜索、天气、股票、高铁票务等），旨在提供智能、便捷的移动端 AI 体验。

## ✨ 主要功能

### 🤖 多模型支持
- **内置 AIme 模型**：开箱即用的免费 AI 模型，无需配置即可体验。
- **自定义模型配置**：支持配置任意 OpenAI 兼容接口的模型（如 GPT, DeepSeek, 豆包等）。
- **多服务商管理**：可以添加多个模型分组，分别设置 Base URL 和 API Key。
- **快速切换**：在聊天界面随时切换当前使用的模型。

### 🛠️ 智能工具箱
内置多种实用工具，AI 可根据对话上下文自动选择调用（Auto Mode）：
- **联网搜索**：获取实时互联网信息。
- **生活服务**：查询实时天气、空气质量。
- **金融数据**：查询股票行情、黄金价格。
- **票务查询**：查询高铁/动车车次及票价。
- **其他工具**：彩票开奖查询等。

### 💬 极致聊天体验
- **Markdown 渲染**：支持流式 Markdown 解析，完美显示代码块、表格、公式。
- **代码高亮**：支持多种编程语言的高亮显示与代码复制。
- **数据备份**：支持本地导入/导出完整对话记录与设置，方便数据迁移与备份。
- **极简模式**：提供沉浸式的极简聊天界面。
- **分享功能**：支持生成对话长图，或生成公开链接分享对话内容（需配置 Supabase）。

## 🛠️ 技术栈

- **语言**: Kotlin
- **UI 框架**: Jetpack Compose (Material Design 3)
- **架构**: MVVM (Model-View-ViewModel)
- **网络**: Retrofit + OkHttp
- **数据库**: Room Database
- **异步处理**: Kotlin Coroutines + Flow
- **后端服务**: Supabase (仅用于对话分享功能)

## 🚀 快速开始

### 环境要求
- Android Studio Ladybug 或更高版本
- JDK 17+
- Android SDK API 26+ (Android 8.0+)

### 构建步骤
1. 克隆项目到本地：
   ```bash
   git clone https://github.com/yourusername/AIme-Android.git
   ```
2. 使用 Android Studio 打开项目根目录。
3. 等待 Gradle 同步完成。
4. 连接 Android 设备或启动模拟器。
5. 点击 **Run** 按钮运行应用。

### ⚙️ 配置说明

#### 1. 模型配置
应用首次启动后，需要配置 AI 模型才能正常使用：
1. 进入 **设置 -> 模型配置**。
2. 点击右上角 **+** 添加模型分组。
3. 输入服务商名称、Base URL 和 API Key。
4. 在该分组下添加具体的模型名称（如 `gpt-4o`, `doubao-pro-32k` 等）。
5. 在聊天界面选择刚才配置的模型即可开始对话。

#### 2. 分享功能配置 (可选)
如果需要使用“对话链接分享”功能，需要配置 Supabase 后端：

1. **配置环境变量**：
   在项目根目录创建 `.env.local` 文件（可复制 `.env.example`），并填入以下信息：
   ```properties
   # Supabase 项目 URL
   SUPABASE_URL=https://your-project.supabase.co
   
   # Supabase Anon Key (公开密钥)
   SUPABASE_KEY=your-anon-key
   
   # 分享链接的基础 URL (你的前端展示页面地址)
   SHARE_BASE_URL=https://your-viewer-url.com/share
   ```

2. **初始化数据库**：
   需要在 Supabase 的 SQL 编辑器中执行初始化脚本，以创建存储分享数据的表和安全策略。
   
   📄 **点击查看 SQL 脚本**: [aime_shared_conversations.sql](aime_shared_conversations.sql)

   *该脚本将创建 `aime_shared_conversations` 表，并配置 RLS (Row Level Security) 策略，允许匿名用户上传对话，允许所有人读取分享的对话。*

#### 3. 云端代理配置 (可选)

部分 AI 模型服务商可能存在跨域限制或网络连接问题。AIme 提供了“云端代理”模式，利用 Supabase Edge Functions 作为中转服务，解决直连不畅的问题。

**原理**：
`App` -> `Supabase Edge Function (Cloud Proxy)` -> `AI Model Provider`

**部署步骤**：

1.  **准备 Supabase 项目**：
    确保你已经有一个 Supabase 项目（可复用分享功能的项目）。

2.  **部署 Edge Function**：
    本项目包含一个名为 `chat-proxy` 的 Edge Function。
    
    *   安装 Supabase CLI：
        ```bash
        npm install -g supabase
        ```
    *   登录 Supabase：
        ```bash
        supabase login
        ```
    *   链接项目（在项目根目录下执行）：
        ```bash
        supabase link --project-ref your-project-id
        ```
    *   部署函数：
        ```bash
        supabase functions deploy chat-proxy
        ```

3.  **App 端开启代理**：
    *   进入 **设置** 页面。
    *   找到 **云端代理模式** 选项。
    *   点击切换为 **云端代理**。
    *   此时 App 发出的聊天请求将自动通过你部署的 Supabase Edge Function 转发。

*(注意：开启云端代理后，请确保项目中的 `Supabase` 配置正确，App 会自动调用项目下的 `chat-proxy` 函数)*

#### 4. 配置阿里云 FC 代理加速 (推荐：日本东京节点)

1. 登录阿里云函数计算 (FC) 3.0 控制台。
2. **在左上角将地域切换为「日本（东京）」**（这决定了网络延迟极低）。
3. 创建一个“**Web 函数**” (Web Function)，运行环境选择 `Node.js 18` 或以上。
4. **关键配置**：
   *   **监听端口**：确保设置为 `9000` (默认)。
   *   **执行超时时间**：在高级配置中修改为 **120 秒**。
   *   **环境变量** (用于内置模型 AIme)：
       *   `TARGET_URL`: 目标服务的完整 URL (例如 `https://api.openai.com/v1/chat/completions`)
       *   `TARGET_API_KEY`: 目标服务的 API Key
       *   `TARGET_MODEL`: (可选) 强制使用的模型名称 (例如 `gpt-4o`)
5. 将本项目中 `aliyun-fc-proxy/index.js` 的代码全选复制到控制台自带的 `index.js` 中并点击“部署代码”。
   *(注意：如果您之前使用的是“事件函数”，请务必删除重建，或者将函数类型改为 Web 函数并配置监听端口为 9000)*
6. 在“触发器管理”中创建一个“HTTP 触发器”（认证方式选：不需要认证），获取公网访问地址。
7. 将获取到的地址填入 Android 项目的 `.env.local` 文件中的 `ALIYUN_FC_PROXY_URL` 字段，并在 App 设置中开启代理模式。

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建你的特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交你的修改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启一个 Pull Request

## 📄 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。
