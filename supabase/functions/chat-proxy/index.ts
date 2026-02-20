import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, x-target-base-url, x-target-api-key',
}

serve(async (req) => {
  // 处理跨域预检请求
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const targetBaseUrl = req.headers.get('x-target-base-url')
    const targetApiKey = req.headers.get('x-target-api-key')

    if (!targetBaseUrl || !targetApiKey) {
      throw new Error('Missing x-target-base-url or x-target-api-key header')
    }

    // --- 智能构建目标 URL ---
    // 1. 去除末尾的斜杠，防止拼接出双斜杠
    let targetUrl = targetBaseUrl.trim().replace(/\/+$/, '');

    // 2. 如果 URL 结尾还没有包含 /chat/completions，才进行补全
    if (!targetUrl.endsWith('/chat/completions')) {
        // 3. 如果 URL 也没有包含版本号（如 /v1 或 /v1beta），默认补齐 /v1
        if (!targetUrl.includes('/v1') && !targetUrl.includes('/v1beta')) {
            targetUrl += '/v1';
        }
        targetUrl += '/chat/completions';
    }

    console.log(`Proxying to: ${targetUrl}`);

    // 以文本形式读取原始请求体，确保 Tool Calling 等 JSON 结构原封不动地透传
    const requestBody = await req.text();

    const response = await fetch(targetUrl, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${targetApiKey}`,
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
      },
      body: requestBody,
    })

    // 创建一个新的响应体，将目标 API 的响应流透传回 Android 客户端
    const proxyResponse = new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: {
        ...corsHeaders,
        'Content-Type': response.headers.get('Content-Type') || 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive',
      },
    })

    return proxyResponse

  } catch (error) {
    // 捕获并返回任何代理层的错误
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})