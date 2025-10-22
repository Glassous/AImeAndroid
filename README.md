# AIme Android

AIme 是一个基于 Jetpack Compose 的 Android 聊天应用，支持自定义模型分组、模型管理与主题切换，提供便捷的对话体验。

## 功能特性
- 对话列表与多会话管理（抽屉导航）
- 底部输入区：模型选择、消息输入与发送
- 模型配置：分组、模型增删改与参数管理
- 主题设置：跟随系统 / 浅色 / 深色
- 基于 Material 3 的现代化 UI

## 主要界面
- `ChatScreen`：聊天主界面
- `SettingsScreen`：设置页（主题设置与模型配置入口）
- `ModelConfigScreen`：模型分组与模型管理

## 构建与运行
- 开发环境：Android Studio（或命令行 Gradle）
- 依赖版本：Compose BOM `2024.09.00`、Material3、Navigation Compose 等
- 命令行构建：
  - Windows：`./gradlew.bat assembleDebug`
  - macOS/Linux：`./gradlew assembleDebug`

## 项目结构
- `app/src/main/java/com/glassous/aime/ui/components/ChatInput.kt`：底部输入区组件
- `app/src/main/java/com/glassous/aime/ui/screens/ChatScreen.kt`：聊天页面
- `app/src/main/java/com/glassous/aime/ui/screens/SettingsScreen.kt`：设置页面
- `app/src/main/java/com/glassous/aime/ui/screens/ModelConfigScreen.kt`：模型配置页面

## 最近更新
- 底部输入区：模型切换与发送按钮圆角与输入框一致（统一为 24dp）
- 设置页面：模型配置模块移动到主题设置模块下方

## 许可证
本项目采用 GNU GPL v3（或更高版本）许可证，详情参见 `LICENSE` 文件。

如需商业使用或二次分发，请遵循 GPL 许可证的相关条款。