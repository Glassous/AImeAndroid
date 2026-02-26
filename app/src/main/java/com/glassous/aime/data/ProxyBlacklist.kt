package com.glassous.aime.data

import android.net.Uri

object ProxyBlacklist {
    private val blockedDomains = listOf(
        // Google & YouTube
        "google.com",
        "google.co.jp",
        "google.com.hk",
        "youtube.com",
        "youtu.be",
        "googlevideo.com",
        "ytimg.com",
        
        // Social Media
        "twitter.com",
        "x.com",
        "t.co",
        "facebook.com",
        "fb.com",
        "instagram.com",
        "cdninstagram.com",
        "reddit.com",
        "redd.it",
        "redditmedia.com",
        "pinterest.com",
        "tumblr.com",
        "linkedin.com",
        "tiktok.com",
        
        // Chat & Communication
        "telegram.org",
        "t.me",
        "discord.com",
        "discord.gg",
        "whatsapp.com",
        "line.me",
        
        // Knowledge & News
        "wikipedia.org",
        "medium.com",
        "quora.com",
        "nytimes.com",
        "wsj.com",
        "bloomberg.com",
        "bbc.com",
        "bbc.co.uk",
        "yahoo.com",
        "yahoo.co.jp",
        "reuters.com",
        "cnn.com",
        
        // Search Engines
        "bing.com",
        "duckduckgo.com",
        
        // AI Services
        "openai.com",
        "chatgpt.com",
        "anthropic.com",
        "claude.ai",
        "gemini.google.com",
        "midjourney.com",
        "huggingface.co",
        "perplexity.ai",
        "poe.com",
        
        // Streaming & Video
        "twitch.tv",
        "vimeo.com",
        "netflix.com",
        "hulu.com",
        "disneyplus.com",
        "spotify.com",
        "soundcloud.com",
        
        // Others
        "github.com",
        "githubusercontent.com",
        "raw.githubusercontent.com",
        "stackoverflow.com",
        "archive.org",
        "dropbox.com",
        "slideshare.net",
        "scribd.com"
    )

    fun shouldUseProxy(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return false
            blockedDomains.any { domain -> 
                host == domain || host.endsWith(".$domain")
            }
        } catch (e: Exception) {
            false
        }
    }
}
