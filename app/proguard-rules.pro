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

# Keep all classes that are used with Gson
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod

# Explicitly kept classes for Gson usage in com.glassous.aime.data package
-keep class com.glassous.aime.data.AccountSession { *; }
-keep class com.glassous.aime.data.RpcAccountResponse { *; }
-keep class com.glassous.aime.data.RpcQuestionResponse { *; }
-keep class com.glassous.aime.data.SupabaseAuthService$RpcSyncResponse { *; }
-keep class com.glassous.aime.data.AutoToolSelectionResult { *; }
-keep class com.glassous.aime.data.SearchResult { *; }
-keep class com.glassous.aime.data.WebSearchResponse { *; }
-keep class com.glassous.aime.data.PearApiResponse { *; }
-keep class com.glassous.aime.data.PearApiSearchResult { *; }
-keep class com.glassous.aime.data.OpenAiChatMessage { *; }
-keep class com.glassous.aime.data.OpenAiContentPart { *; }
-keep class com.glassous.aime.data.OpenAiFile { *; }
-keep class com.glassous.aime.data.OpenAiVideoUrl { *; }
-keep class com.glassous.aime.data.OpenAiFileUrl { *; }
-keep class com.glassous.aime.data.OpenAiInputAudio { *; }
-keep class com.glassous.aime.data.OpenAiImageUrl { *; }
-keep class com.glassous.aime.data.ToolFunctionParameter { *; }
-keep class com.glassous.aime.data.ToolFunctionParameters { *; }
-keep class com.glassous.aime.data.ToolFunction { *; }
-keep class com.glassous.aime.data.Tool { *; }
-keep class com.glassous.aime.data.ChatCompletionsRequest { *; }
-keep class com.glassous.aime.data.ImageConfig { *; }
-keep class com.glassous.aime.data.ChatCompletionsChunkChoiceDelta { *; }
-keep class com.glassous.aime.data.ToolCall { *; }
-keep class com.glassous.aime.data.ToolCallFunction { *; }
-keep class com.glassous.aime.data.FunctionCall { *; }
-keep class com.glassous.aime.data.ChatCompletionsChunkChoice { *; }
-keep class com.glassous.aime.data.ChatCompletionsChunk { *; }
-keep class com.glassous.aime.data.OpenAiError { *; }
-keep class com.glassous.aime.data.OpenAiErrorResponse { *; }
-keep class com.glassous.aime.data.TicketSeatInfo { *; }
-keep class com.glassous.aime.data.HighSpeedTrainItem { *; }
-keep class com.glassous.aime.data.HighSpeedTicketPayload { *; }
-keep class com.glassous.aime.data.LotteryApiResponse { *; }
-keep class com.glassous.aime.data.LotteryItem { *; }
-keep class com.glassous.aime.data.LotteryResult { *; }
-keep class com.glassous.aime.data.StockDailyItem { *; }
-keep class com.glassous.aime.data.StockApiResponse { *; }
-keep class com.glassous.aime.data.StockQueryResult { *; }
-keep class com.glassous.aime.data.WeatherDaily { *; }
-keep class com.glassous.aime.data.WeatherQueryResult { *; }
-keep class com.glassous.aime.data.WeatherService$GeocodingResult { *; }
-keep class com.glassous.aime.data.WeatherService$GeocodeItem { *; }
-keep class com.glassous.aime.data.WeatherService$ForecastResp { *; }
-keep class com.glassous.aime.data.WeatherService$ForecastDaily { *; }
-keep class com.glassous.aime.data.WeatherService$AirQualityResp { *; }
-keep class com.glassous.aime.data.WeatherService$AirQualityHourly { *; }
-keep class com.glassous.aime.data.TavilySearchResult { *; }

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
