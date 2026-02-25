# AI ChatBox - 原生Android应用

强大的AI对话助手，支持多种大模型API，真正的原生Android APK。

## 功能特点

- 🚀 **多模型支持**: OpenAI、Anthropic Claude、Google Gemini、本地模型
- 💾 **本地存储**: 聊天记录持久化保存
- ⚡ **流式输出**: 实时显示AI回复
- 🎨 **Material Design 3**: 现代化UI设计
- 📱 **原生APK**: 纯原生Android应用

## 编译APK

### 方法一：使用GitHub Actions（推荐）

1. Fork 或上传此项目到你的 GitHub 仓库
2. 进入仓库的 **Actions** 页面
3. 点击 **Build APK** workflow
4. 点击 **Run workflow** 按钮
5. 等待编译完成后，在 **Artifacts** 中下载 APK

### 方法二：本地编译

需要：
- JDK 17+
- Android SDK (platform 34, build-tools 34.0.0)

```bash
# 编译Debug APK
./gradlew assembleDebug

# 编译Release APK
./gradlew assembleRelease
```

APK输出位置：
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release-unsigned.apk`

## 使用说明

1. 安装APK后打开应用
2. 点击侧边菜单 → 设置
3. 输入你的API端点和API Key
4. 开始对话！

## 支持的API

| 提供商 | 端点 | 模型示例 |
|--------|------|----------|
| OpenAI | api.openai.com | gpt-4, gpt-4o |
| Anthropic | api.anthropic.com | claude-3-opus |
| Google AI | generativelanguage.googleapis.com | gemini-pro |
| 本地模型 | localhost:11434 | llama3, mistral |

## 技术栈

- Kotlin
- Material Design 3
- OkHttp + SSE
- Coroutines
- AndroidX

## License

MIT
