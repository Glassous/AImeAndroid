const https = require('https');
const http = require('http');

/**
 * 阿里云函数计算 (FC) 专用 AI 代理
 * 
 * 模式：Web 函数 (Web Function) / Custom Runtime
 * 监听端口：9000 (默认)
 * 
 * 作用：透传请求至目标 AI 服务商 (OpenAI / Moonshot / etc.)
 * 特性：
 * 1. 支持 SSE (Server-Sent Events) 流式透传
 * 2. 自动处理 HTTPS/HTTP 协议
 * 3. 强制禁用 Gzip 压缩以避免双重编码问题
 * 4. 详细日志输出，方便排查
 */

const SERVER_PORT = 9000;

const server = http.createServer((req, res) => {
  // 1. 设置基础响应头 (CORS)
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'authorization, x-client-info, apikey, content-type, x-target-url, x-target-api-key, accept');

  // 2. 处理 OPTIONS 预检请求
  if (req.method === 'OPTIONS') {
    res.statusCode = 200;
    res.end();
    return;
  }

  const requestId = Math.random().toString(36).substring(7);
  console.log(`[${requestId}] Received request: ${req.method} ${req.url}`);

  try {
    // 3. 获取目标配置
    const targetUrlStr = req.headers['x-target-url'];
    const targetApiKey = req.headers['x-target-api-key'];

    if (!targetUrlStr || !targetApiKey) {
      console.warn(`[${requestId}] Missing headers: x-target-url or x-target-api-key`);
      res.statusCode = 400;
      res.end(JSON.stringify({ 
        error: { 
          message: 'Missing x-target-url or x-target-api-key header', 
          type: 'invalid_request_error', 
          code: 'missing_headers' 
        } 
      }));
      return;
    }

    const targetUrl = new URL(targetUrlStr);
    const requestModule = targetUrl.protocol === 'https:' ? https : http;

    console.log(`[${requestId}] Proxying to: ${targetUrl.origin}${targetUrl.pathname}`);

    // 4. 准备透传 Headers
    const headers = {};
    // 排除列表
    const skipHeaders = ['host', 'connection', 'accept-encoding', 'x-target-url', 'x-target-api-key', 'authorization'];
    
    for (const key in req.headers) {
      if (!skipHeaders.includes(key.toLowerCase())) {
        headers[key] = req.headers[key];
      }
    }

    // 强制覆盖关键 Headers
    headers['Authorization'] = `Bearer ${targetApiKey}`;
    headers['Host'] = targetUrl.hostname;
    
    // 5. 发起代理请求
    const options = {
      hostname: targetUrl.hostname,
      port: targetUrl.port || (targetUrl.protocol === 'https:' ? 443 : 80),
      path: targetUrl.pathname + targetUrl.search,
      method: req.method,
      headers: headers,
      // 禁用自动 Agent 保持连接，避免 FC 环境下连接池问题
      agent: false
    };

    const proxyReq = requestModule.request(options, (proxyRes) => {
      console.log(`[${requestId}] Target response: ${proxyRes.statusCode}`);
      
      res.statusCode = proxyRes.statusCode;
      
      // 透传响应头
      for (const key in proxyRes.headers) {
        if (key.toLowerCase() !== 'content-encoding') {
             res.setHeader(key, proxyRes.headers[key]);
        }
      }

      // 管道透传响应体
      proxyRes.pipe(res);
      
      proxyRes.on('end', () => {
        console.log(`[${requestId}] Response stream ended`);
      });
    });

    proxyReq.on('error', (e) => {
      console.error(`[${requestId}] Proxy Request Error:`, e);
      if (!res.headersSent) {
        res.statusCode = 502;
        res.end(JSON.stringify({ 
          error: { 
            message: `Proxy Error: ${e.message}`, 
            type: 'proxy_error', 
            code: 'proxy_failed' 
          } 
        }));
      }
    });

    // 6. 管道透传请求体
    req.pipe(proxyReq);

    req.on('error', (e) => {
      console.error(`[${requestId}] Request Stream Error:`, e);
      proxyReq.destroy();
      if (!res.headersSent) {
        res.statusCode = 400;
        res.end(JSON.stringify({ 
          error: { 
            message: `Request Error: ${e.message}`, 
            type: 'invalid_request_error', 
            code: 'request_failed' 
          } 
        }));
      }
    });

  } catch (error) {
    console.error(`[${requestId}] Handler Error:`, error);
    if (!res.headersSent) {
      res.statusCode = 500;
      res.end(JSON.stringify({ 
        error: { 
          message: `Internal Server Error: ${error.message}`, 
          type: 'server_error', 
          code: 'internal_error' 
        } 
      }));
    }
  }
});

// 启动服务器
server.listen(SERVER_PORT, '0.0.0.0', () => {
  console.log(`FC Proxy Server started on port ${SERVER_PORT}`);
});
