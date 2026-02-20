const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, x-target-base-url, x-target-api-key',
};

export default {
  async fetch(request, env, ctx) {
    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: corsHeaders });
    }
    try {
      const targetBaseUrl = request.headers.get('x-target-base-url');
      const targetApiKey = request.headers.get('x-target-api-key');

      if (!targetBaseUrl || !targetApiKey) {
        return new Response('Missing x-target-base-url or x-target-api-key header', { status: 400 });
      }

      let targetUrl = targetBaseUrl.trim().replace(/\/+$/, '');
      if (!targetUrl.endsWith('/chat/completions')) {
          if (!targetUrl.includes('/v1') && !targetUrl.includes('/v1beta')) {
              targetUrl += '/v1';
          }
          targetUrl += '/chat/completions';
      }

      const requestBody = request.body;
      const response = await fetch(targetUrl, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${targetApiKey}`,
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream',
        },
        body: requestBody,
      });

      return new Response(response.body, {
        status: response.status,
        statusText: response.statusText,
        headers: {
          ...corsHeaders,
          'Content-Type': response.headers.get('Content-Type') || 'text/event-stream',
          'Cache-Control': 'no-cache',
          'Connection': 'keep-alive',
        },
      });
    } catch (error) {
      return new Response(JSON.stringify({ error: error.message }), {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }
  },
};
