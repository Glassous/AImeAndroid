# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }

# Retrofit
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Data Models (Gson)
-keep class com.glassous.aime.data.model.** { *; }

# Explicitly kept classes for Gson usage

# SupabaseAuthService
-keep class com.glassous.aime.data.AccountSession { *; }
-keep class com.glassous.aime.data.RpcAccountResponse { *; }
-keep class com.glassous.aime.data.RpcQuestionResponse { *; }
-keep class com.glassous.aime.data.SupabaseAuthService$RpcSyncResponse { *; }

# AutoToolSelector
-keep class com.glassous.aime.data.AutoToolSelectionResult { *; }

# WebSearchService
-keep class com.glassous.aime.data.SearchResult { *; }
-keep class com.glassous.aime.data.WebSearchResponse { *; }
-keep class com.glassous.aime.data.PearApiResponse { *; }
-keep class com.glassous.aime.data.PearApiSearchResult { *; }

# OpenAiService & DoubaoArkService
-keep class com.glassous.aime.data.OpenAiChatMessage { *; }
-keep class com.glassous.aime.data.ToolFunctionParameter { *; }
-keep class com.glassous.aime.data.ToolFunctionParameters { *; }
-keep class com.glassous.aime.data.ToolFunction { *; }
-keep class com.glassous.aime.data.Tool { *; }
-keep class com.glassous.aime.data.ChatCompletionsRequest { *; }
-keep class com.glassous.aime.data.ChatCompletionsChunkChoiceDelta { *; }
-keep class com.glassous.aime.data.ToolCall { *; }
-keep class com.glassous.aime.data.ToolCallFunction { *; }
-keep class com.glassous.aime.data.FunctionCall { *; }
-keep class com.glassous.aime.data.ChatCompletionsChunkChoice { *; }
-keep class com.glassous.aime.data.ChatCompletionsChunk { *; }
-keep class com.glassous.aime.data.OpenAiError { *; }
-keep class com.glassous.aime.data.OpenAiErrorResponse { *; }

# HighSpeedTicketService
-keep class com.glassous.aime.data.TicketSeatInfo { *; }
-keep class com.glassous.aime.data.HighSpeedTrainItem { *; }
-keep class com.glassous.aime.data.HighSpeedTicketPayload { *; }

# BaiduTikuService
-keep class com.glassous.aime.data.BaiduTikuApiResponse { *; }
-keep class com.glassous.aime.data.BaiduTikuData { *; }
-keep class com.glassous.aime.data.BaiduTikuResult { *; }

# LotteryService
-keep class com.glassous.aime.data.LotteryApiResponse { *; }
-keep class com.glassous.aime.data.LotteryItem { *; }
-keep class com.glassous.aime.data.LotteryResult { *; }

# StockService
-keep class com.glassous.aime.data.StockDailyItem { *; }
-keep class com.glassous.aime.data.StockApiResponse { *; }
-keep class com.glassous.aime.data.StockQueryResult { *; }

# GoldPriceService
-keep class com.glassous.aime.data.BankGoldBarPrice { *; }
-keep class com.glassous.aime.data.GoldRecyclePrice { *; }
-keep class com.glassous.aime.data.PreciousMetalPrice { *; }
-keep class com.glassous.aime.data.GoldPricePayload { *; }
-keep class com.glassous.aime.data.GoldPriceApiResponse { *; }
-keep class com.glassous.aime.data.GoldPriceResult { *; }

# WeatherService
-keep class com.glassous.aime.data.WeatherDaily { *; }
-keep class com.glassous.aime.data.WeatherQueryResult { *; }
# Inner classes in WeatherService are private, but we should keep them if used by Gson
-keep class com.glassous.aime.data.WeatherService$GeocodingResult { *; }
-keep class com.glassous.aime.data.WeatherService$GeocodeItem { *; }
-keep class com.glassous.aime.data.WeatherService$ForecastResp { *; }
-keep class com.glassous.aime.data.WeatherService$ForecastDaily { *; }
-keep class com.glassous.aime.data.WeatherService$AirQualityResp { *; }
-keep class com.glassous.aime.data.WeatherService$AirQualityHourly { *; }

# General rule for any class used with Gson that might have been missed
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Jsoup
-keep class org.jsoup.** { *; }

# Keep Kotlinx Serialization (if used)
-keep class kotlinx.serialization.** { *; }

# Keep Room
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase

# Keep OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Keep Okio
-keep class okio.** { *; }
-dontwarn okio.**

# Markwon & CommonMark
-keep class io.noties.markwon.** { *; }
-keep class org.commonmark.** { *; }
-dontwarn io.noties.markwon.**
-dontwarn org.commonmark.**
