package com.xdl.xledger.data.remote

import com.xdl.xledger.data.db.entity.TransactionEntity
import com.xdl.xledger.data.repository.ConfigRepository
import com.xdl.xledger.util.CompressedImportImage
import com.xdl.xledger.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * LLM 截图识别服务，对应旧版 service/llm.ts。
 *
 * 只支持 Chat Completions 协议（/chat/completions，DeepSeek / OpenAI 兼容格式）。
 * 响应按 `choices[0].message` 解析：content 为最终答案，reasoning_content 为思考过程（推理模型）。
 */
class LlmApiService(
    private val configRepository: ConfigRepository,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build(),
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val systemPrompt =
        "你从支付记录列表截图中提取交易。逐条阅读图片内容，图片上有几条记录就原样输出几条，严格按列表顺序识别商户/说明、日期时间和金额。" +
            "只输出 JSON，不要解释。金额始终输出正数，收支方向用 0 表示支出、1 表示收入。" +
            "禁止自行判断重复或合并记录：即使多条记录的项目名、金额完全一样，也要逐条全部输出，是否重复由系统后续处理，你绝不能省略、合并或去重。" +
            "只有完全无法识别出日期、金额等关键字段的记录才可以不输出。不要输出任何推理过程、分析或解释，直接输出 JSON。"

    suspend fun parseTransactionsFromImages(
        images: List<CompressedImportImage>,
        today: String?,
    ): List<ParsedTransaction> =
        parseTransactionsFromImagesWithDebug(images, today).transactions

    /**
     * 识别并保留模型原始返回与思考过程（调试用）。
     *
     * [today] 为 "今天是哪天"（yyyy-MM-dd）：模型原样抄写截图上的日期文字，相对日期（昨天/前天）与缺失的年份按它在本地推算。
     *
     * 请求级错误（未配置 Key、网络失败、HTTP 错误）仍抛出异常；
     * 请求成功但返回内容无法解析时不抛，而是通过 [LlmParseDebugResult.parseError] 返回原因。
     */
    suspend fun parseTransactionsFromImagesWithDebug(
        images: List<CompressedImportImage>,
        today: String?,
    ): LlmParseDebugResult {
        if (images.isEmpty()) {
            throw IllegalStateException("没有选择任何图片")
        }

        val settings = loadSettings()
        val body = buildRequest(settings, images)

        val responseText = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${settings.baseUrl}/chat/completions")
                .header("Authorization", "Bearer ${settings.apiKey}")
                .header("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("LLM request failed: ${response.code} $text")
                }
                text
            }
        }

        val response = json.decodeFromString(LlmResponse.serializer(), responseText)
        val message = response.choices?.firstOrNull()?.message

        // 最终内容：优先标准 envelope 的 message.content；
        // 无标准 envelope 时兜底：直接把整个响应体当作交易 JSON（某些服务直接返回裸 JSON）。
        val content = extractMessageContent(message)
            ?: runCatching {
                extractJsonObject(responseText)
                responseText
            }.getOrNull()
            ?: throw IllegalStateException("LLM 返回了空响应")

        val reasoningContent = message?.reasoningContent?.takeIf { it.isNotBlank() }
            ?: message?.reasoning?.takeIf { it.isNotBlank() }
            ?: message?.thinking?.takeIf { it.isNotBlank() }

        return try {
            val payload = extractJsonObject(content)
            LlmParseDebugResult(
                transactions = normalizePayload(payload, today),
                apiUrl = "${settings.baseUrl}/chat/completions",
                model = settings.model,
                responseContent = content,
                reasoningContent = reasoningContent,
            )
        } catch (error: Exception) {
            LlmParseDebugResult(
                transactions = emptyList(),
                apiUrl = "${settings.baseUrl}/chat/completions",
                model = settings.model,
                responseContent = content,
                reasoningContent = reasoningContent,
                parseError = error.message ?: "返回内容解析失败",
            )
        }
    }

    // ---- 请求 ----

    private data class LlmSettings(
        val apiKey: String,
        val baseUrl: String,
        val model: String,
        val enableThinking: Boolean,
    )

    private suspend fun loadSettings(): LlmSettings {
        val apiKey = configRepository.getValue(LlmConfigKeys.API_KEY)
        if (apiKey.isNullOrBlank()) {
            throw IllegalStateException("未配置 LLM API Key，请先在 设置 → LLM 配置 中填写。")
        }
        val baseUrl = configRepository.getValue(LlmConfigKeys.BASE_URL)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_LLM_BASE_URL
        val model = configRepository.getValue(LlmConfigKeys.MODEL)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_LLM_MODEL
        // 识别类任务输出 JSON，默认关闭思考（更快、更省 token，且与 JSON 输出兼容）
        val enableThinking = configRepository.getValue(LlmConfigKeys.ENABLE_THINKING) == "true"
        return LlmSettings(
            apiKey = apiKey,
            baseUrl = baseUrl.trimEnd('/'),
            model = model,
            enableThinking = enableThinking,
        )
    }

    private fun buildRequest(
        settings: LlmSettings,
        images: List<CompressedImportImage>,
    ): String {
        // 日期不让模型算：原样抄下截图上的日期文字，相对日期与缺失的年份由 [OccurredAtParser] 按今天推算
        val userText =
            "输出 JSON 格式：{\"transactions\":[{\"occurred_at\":\"截图上的日期时间原文\"," +
                "\"transaction_type\":0,\"amount\":\"28.50\",\"description\":\"交易摘要或 null\"}]}\n" +
                "occurred_at 必须原样抄写截图上该条记录的日期时间文字，不要换算、不要补全年份、不要改成其他格式，" +
                "也不要自行推算“今天”“昨天”“前天”对应的日期。例如截图写“11月3日 12:30”就输出“11月3日 12:30”，" +
                "写“昨天 20:54”就输出“昨天 20:54”，写“2026-11-03 12:30:00”就输出“2026-11-03 12:30:00”。" +
                "如果截图里该条记录没有写时间，只输出日期原文。\n" +
                "请从下面的支付记录截图中逐条提取所有交易：图片上有几条就输出几条，一条都不要省略。"

        return json.encodeToString(
            ChatCompletionsRequest.serializer(),
            ChatCompletionsRequest(
                model = settings.model,
                enableThinking = settings.enableThinking,
                messages = listOf(
                    ChatMessage(
                        role = "system",
                        content = listOf(ChatContentItem(type = "text", text = systemPrompt)),
                    ),
                    ChatMessage(
                        role = "user",
                        content = listOf(
                            ChatContentItem(type = "text", text = userText),
                        ) + images.map { image ->
                            ChatContentItem(
                                type = "image_url",
                                imageUrl = ChatImageUrl(url = image.dataUrl),
                            )
                        },
                    ),
                ),
            ),
        )
    }

    // ---- 响应解析 ----

    /**
     * 提取 message.content，兼容字符串与数组两种形态：
     * - `"content": "{\"transactions\":...}"`（字符串）
     * - `"content": [{"type":"text","text":"..."}, ...]`（OpenAI 新版数组）
     */
    private fun extractMessageContent(message: LlmResponseMessage?): String? {
        val element = message?.content ?: return null
        return when (element) {
            is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }
            is JsonArray -> element.mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                if ((obj["type"] as? JsonPrimitive)?.content == "text") {
                    (obj["text"] as? JsonPrimitive)?.content
                } else {
                    null
                }
            }.joinToString("\n").takeIf { it.isNotBlank() }
            else -> null
        }
    }

    /**
     * 从 LLM 返回文本中提取交易 JSON。
     *
     * 容错顺序：
     * 1. ```json fence 包裹的块
     * 2. 整段文本直接解析
     * 3. 扫描提取第一个平衡的 JSON 对象（容忍叙述性前缀/后缀）
     */
    private fun extractJsonObject(content: String): LlmTransactionPayload {
        val trimmed = content.trim()
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            .find(trimmed)
            ?.groupValues?.get(1)?.trim()

        val candidates = buildList {
            fenced?.let { add(it) }
            add(trimmed)
            extractJsonObjectText(trimmed)?.let { add(it) }
        }

        for (candidate in candidates) {
            runCatching {
                return json.decodeFromString(LlmTransactionPayload.serializer(), candidate)
            }
        }

        throw IllegalArgumentException("LLM 返回内容不是有效 JSON: ${trimmed.take(200)}")
    }

    /** 扫描第一个平衡的 JSON 对象（忽略字符串内的花括号）。 */
    private fun extractJsonObjectText(text: String): String? {
        var start = -1
        var depth = 0
        var inString = false
        var escaped = false

        for ((index, char) in text.withIndex()) {
            when {
                char == '"' && !escaped -> inString = !inString
                inString && char == '\\' && !escaped -> escaped = true
                inString -> escaped = false
                !inString && char == '{' -> {
                    if (depth == 0) start = index
                    depth++
                }
                !inString && char == '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        return text.substring(start, index + 1)
                    }
                }
            }
        }
        return null
    }

    private fun normalizePayload(
        payload: LlmTransactionPayload,
        today: String?,
    ): List<ParsedTransaction> {
        val rows = payload.transactions.orEmpty()
        val result = mutableListOf<ParsedTransaction>()

        for (row in rows) {
            val occurredAt = OccurredAtParser.parse(
                row.occurredAt,
                today?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            )
            val transactionType = normalizeTransactionType(row.transactionType)
            val amount = normalizeAmount(row.amount)
            if (occurredAt == null || transactionType == null || amount == null) {
                continue
            }
            result.add(
                ParsedTransaction(
                    occurredAt = occurredAt,
                    transactionType = transactionType,
                    amount = amount,
                    description = row.description?.trim()?.takeIf { it.isNotEmpty() },
                ),
            )
        }
        return result
    }

    private fun normalizeAmount(value: JsonElement?): String? {
        val raw = (value as? JsonPrimitive)?.content ?: return null
        val cleaned = raw.replace(Regex("[^\\d.-]"), "")
        val amount = abs(cleaned.toDoubleOrNull() ?: return null)
        if (!amount.isFinite() || amount <= 0) return null
        return "%.2f".format(amount)
    }

    private fun normalizeTransactionType(value: JsonElement?): Int? {
        val raw = (value as? JsonPrimitive)?.content ?: return null
        return when {
            raw == "0" || raw.equals("expense", ignoreCase = true) || raw == "支出" ->
                TransactionEntity.TYPE_EXPENSE
            raw == "1" || raw.equals("income", ignoreCase = true) || raw == "收入" ->
                TransactionEntity.TYPE_INCOME
            else -> raw.toIntOrNull()?.takeIf { it == 0 || it == 1 }
        }
    }
}
