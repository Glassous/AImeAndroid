import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, x-target-base-url, x-target-api-key',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const targetBaseUrl = req.headers.get('x-target-base-url')
    const targetApiKey = req.headers.get('x-target-api-key')

    if (!targetBaseUrl || !targetApiKey) {
      throw new Error('Missing x-target-base-url or x-target-api-key header')
    }

    // Construct target URL
    // Ensure we handle base URL correctly regardless of trailing slash or /v1 suffix
    let normalizedBase = targetBaseUrl.trim();
    if (normalizedBase.endsWith('/')) {
        normalizedBase = normalizedBase.slice(0, -1);
    }
    
    // Add /v1 if not present, then append /chat/completions
    const withV1 = normalizedBase.includes('/v1') ? normalizedBase : `${normalizedBase}/v1`;
    const targetUrl = `${withV1}/chat/completions`;

    console.log(`Proxying to: ${targetUrl}`);

    // Read the request body as text to ensure transparent forwarding
    // We do not parse it as JSON to avoid modifying it
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

    // Create a new response with the target response body and headers
    // We want to stream the response back
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
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})
