export default {
    async fetch(request, env) {
        // 1. 处理 CORS 预检请求 (OPTIONS)
        if (request.method === "OPTIONS") {
            return new Response(null, {
                headers: {
                    "Access-Control-Allow-Origin": "*",
                    "Access-Control-Allow-Methods": "GET, HEAD, POST, PUT, DELETE, OPTIONS",
                    "Access-Control-Allow-Headers": "*",
                    "Access-Control-Max-Age": "86400",
                }
            });
        }

        const url = new URL(request.url);
        let targetUrl = url.searchParams.get("url") || request.headers.get("x-target-url");

        if (!targetUrl) {
            return new Response("Missing 'url' parameter or 'x-target-url' header", { status: 400 });
        }

        try {
            const target = new URL(targetUrl);

            // 2. 修复 Bug：在 Worker 中必须通过 new Headers() 深拷贝才能修改 Header
            const requestHeaders = new Headers(request.headers);
            
            // 删除可能导致目标服务器拒绝请求的 Header
            requestHeaders.delete("host");
            requestHeaders.delete("cf-connecting-ip");
            requestHeaders.delete("x-forwarded-for");
            requestHeaders.delete("x-real-ip");
            
            // 伪装 Referer 和 Origin 防止防盗链拦截
            requestHeaders.set("Referer", target.origin);
            requestHeaders.set("Origin", target.origin);

            if (!requestHeaders.has("user-agent")) {
                requestHeaders.set("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            }

            // 3. 修复 Bug：GET 和 HEAD 请求严禁携带 body，否则 Fetch 会报错崩溃
            const isGetOrHead = request.method === 'GET' || request.method === 'HEAD';
            const newRequest = new Request(target, {
                method: request.method,
                headers: requestHeaders,
                body: isGetOrHead ? null : request.body,
                redirect: "follow"
            });

            // 4. 发起代理请求
            const response = await fetch(newRequest);

            // 5. 组装返回响应
            const newResponse = new Response(response.body, response);

            // 处理 CORS 和防止强制下载
            newResponse.headers.set("Access-Control-Allow-Origin", "*");
            newResponse.headers.delete("Content-Disposition");
            // 移除可能导致 WebView 安全策略阻挡的 Header
            newResponse.headers.delete("X-Frame-Options");
            newResponse.headers.delete("Content-Security-Policy");

            // 修复 Content-Type
            const contentType = newResponse.headers.get("content-type") || "";
            if (!contentType) {
                if (targetUrl.endsWith(".css")) newResponse.headers.set("content-type", "text/css");
                else if (targetUrl.endsWith(".js")) newResponse.headers.set("content-type", "application/javascript");
                else newResponse.headers.set("content-type", "text/html");
            }

            // 6. 🚀 杀手级优化：为 HTML 动态注入 <base> 标签
            // 解决 WebView 中相对路径（如 <img src="/logo.png">）加载失败的问题
            if (contentType.includes("text/html") || newResponse.headers.get("content-type")?.includes("text/html")) {
                return new HTMLRewriter().on("head", {
                    element(element) {
                        element.prepend(`<base href="${target.href}">`, { html: true });
                    }
                }).transform(newResponse);
            }

            return newResponse;

        } catch (e) {
            return new Response(`Error fetching url: ${e.message}`, { status: 500 });
        }
    }
};