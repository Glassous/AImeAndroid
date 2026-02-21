# Aliyun FC AI Proxy

阿里云函数计算 (FC) 专用 AI 代理服务，用于解决部分 AI 模型 API 无法直连或需要隐藏 API Key 的场景。

## 功能特性

1.  **透传代理**：支持将请求透传至 OpenAI、Moonshot (Kimi)、DeepSeek 等兼容 OpenAI 接口的服务商。
2.  **流式响应**：完整支持 SSE (Server-Sent Events) 流式响应。
3.  **内置模型支持**：可通过环境变量配置内置模型，客户端无需通过 Header 传递敏感信息。
4.  **安全**：支持通过 `x-target-api-key` 验证内置模型请求。

## 部署配置

### 1. 常规透传模式

客户端需在 Header 中携带以下参数：

*   `x-target-url`: 目标 API 的完整 URL (例如 `https://api.openai.com/v1/chat/completions`)
*   `x-target-api-key`: 目标服务的 API Key

### 2. 内置模型模式 (环境变量配置)

当客户端传递 `x-target-api-key: sk-builtin-aime` 时，代理将使用环境变量中的配置。

需要在函数计算中配置以下环境变量：

| 环境变量名 | 描述 | 示例 |
| :--- | :--- | :--- |
| `TARGET_URL` | 目标服务的完整 URL | `https://api.moonshot.cn/v1/chat/completions` |
| `TARGET_API_KEY` | 目标服务的 API Key | `sk-xxxxxxxxxxxxxxxx` |
| `TARGET_MODEL` | (可选) 强制使用的模型名称 | `moonshot-v1-8k` |

**注意**：
*   配置 `TARGET_MODEL` 后，无论客户端请求体中传递什么 `model`，都会被替换为此处配置的值。
*   此模式下，代理会缓冲请求体以进行 JSON 解析和替换，可能会稍微增加延迟，但对流式响应无影响。

## 部署说明

本代码适用于阿里云函数计算 (FC) 的 Web 函数或 Custom Runtime (Node.js)。
默认监听端口：9000。
