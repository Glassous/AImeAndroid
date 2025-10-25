# AIme - AI 聊天助手

一个现代化的 Android AI 聊天应用，支持多种 AI 模型和云端同步功能。

## 功能特点

### 🤖 多模型支持
- 支持 OpenAI、Claude 等多种 AI 模型
- 灵活的模型分组管理
- 自定义 API 端点和密钥配置
- 轻松切换不同模型进行对话

### 💬 智能聊天体验
- 直观的对话界面，支持流畅的聊天体验
- 消息历史记录管理
- 对话标题自动生成
- 支持消息编辑和重新生成
- 错误处理和重试机制

### 🎨 个性化界面
- 支持浅色/深色/系统主题切换
- 可调节聊天字体大小
- 极简模式选项
- Material 3 设计语言
- 流畅的动画和过渡效果

### ☁️ 数据同步
- 本地数据持久化存储
- 云端同步功能（支持阿里云 OSS）
- 自动同步选项
- 数据导入/导出功能

### 🔧 高级设置
- 模型参数配置
- API 请求自定义
- 同步设置管理
- 数据备份与恢复

## 技术架构

### 核心技术栈
- **开发语言**: Kotlin
- **UI 框架**: Jetpack Compose
- **架构模式**: MVVM + Repository
- **依赖注入**: 手动依赖注入
- **异步处理**: Kotlin Coroutines + Flow

### 主要依赖库
- **Android Jetpack**:
  - Compose BOM - 现代 UI 工具包
  - Navigation Compose - 导航组件
  - ViewModel & LiveData - 状态管理
  - Room - 本地数据库
  - DataStore - 数据持久化

- **网络请求**:
  - Retrofit - HTTP 客户端
  - OkHttp - 网络拦截器
  - Gson - JSON 解析

- **其他工具**:
  - KSP - 注解处理器
  - Material Icons Extended - 图标库

### 项目结构
```
app/src/main/java/com/glassous/aime/
├── data/                    # 数据层
│   ├── dao/                # 数据访问对象
│   ├── model/              # 数据模型
│   ├── preferences/        # 用户偏好设置
│   └── repository/         # 数据仓库
├── sync/                   # 同步功能
├── ui/                     # UI 层
│   ├── components/         # 可复用组件
│   ├── navigation/         # 导航配置
│   └── screens/            # 页面
├── utils/                  # 工具类
├── viewmodel/              # 视图模型
├── Application.kt          # 应用程序类
└── MainActivity.kt         # 主活动
```

## 安装与使用

### 系统要求
- Android 7.0 (API 24) 或更高版本
- 建议至少 2GB RAM

### 安装步骤
1. 下载最新版本的 APK 文件
2. 在设备上启用"未知来源"安装
3. 安装并启动应用
4. 在设置中配置您的 AI 模型 API

### 基本使用
1. **首次使用**:
   - 打开应用后，点击设置按钮
   - 添加模型配置（API 端点、密钥等）
   - 返回主界面开始聊天

2. **聊天功能**:
   - 在输入框中输入您的问题
   - 点击发送按钮或按回车键
   - 等待 AI 回复
   - 可以编辑已发送的消息或重新生成 AI 回复

3. **管理对话**:
   - 点击左上角按钮查看对话列表
   - 长按对话可删除或重命名
   - 点击"+"按钮创建新对话

## 配置说明

### 模型配置
1. 在设置中选择"模型配置"
2. 点击"添加分组"
3. 填写以下信息:
   - 分组名称（如：OpenAI）
   - API 基础 URL
   - API 密钥
4. 在分组中添加具体模型（如：gpt-3.5-turbo）

### 云端同步
1. 在设置中找到"云端同步"
2. 配置阿里云 OSS 参数:
   - 区域 ID
   - 端点地址
   - 存储桶名称
   - 访问密钥 ID
   - 访问密钥 Secret
3. 启用自动同步选项

## 开发说明

### 构建环境
- Android Studio Hedgehog | 2023.1.1 或更高版本
- JDK 17
- Android SDK API 33
- Kotlin 2.0.21

### 构建步骤
1. 克隆项目到本地
2. 使用 Android Studio 打开项目
3. 等待 Gradle 同步完成
4. 点击运行按钮构建并安装

### 贡献指南
1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 许可证

本项目采用 Apache License 2.0 许可证。详情请参见 [LICENSE](LICENSE) 文件。

## 更新日志

### v1.0.0
- 初始版本发布
- 基础聊天功能
- 多模型支持
- 主题切换
- 云端同步

## 联系方式

如有问题或建议，请通过以下方式联系:
- 提交 Issue: [GitHub Issues](https://github.com/yourusername/aime-android/issues)
- 邮箱: your.email@example.com

## 致谢

感谢以下开源项目:
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Retrofit](https://square.github.io/retrofit/)
- [Room](https://developer.android.com/training/data-storage/room)
- [OkHttp](https://square.github.io/okhttp/)