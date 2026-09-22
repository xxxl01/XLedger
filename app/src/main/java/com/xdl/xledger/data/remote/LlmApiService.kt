package com.xdl.xledger.data.remote

import com.xdl.xledger.data.db.entity.CategoryEntity
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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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
            "category 必须从用户给定的可选分类中选一个最可能的分类名输出，绝不允许输出 null，即使不确定也要给出最可能的猜测：" +
            "有对应的二级分类（缩进的子分类）就直接输出二级分类名，没有合适的二级分类就输出一级分类名。" +
            "支出记录只能选支出分类，收入记录只能选收入分类。" +
            "禁止自行判断重复或合并记录：即使多条记录的项目名、金额完全一样，也要逐条全部输出，是否重复由系统后续处理，你绝不能省略、合并或去重。" +
            "只有完全无法识别出日期、金额等关键字段的记录才可以不输出。不要输出任何推理过程、分析或解释，直接输出 JSON。"

    suspend fun parseTransactionsFromImages(
        images: List<CompressedImportImage>,
        today: String?,
        categories: List<CategoryEntity>,
    ): List<ParsedTransaction> =
        parseTransactionsFromImagesWithDebug(images, today, categories).transactions

    /**
     * 识别并保留模型原始返回与思考过程（调试用）。
     *
     * [today] 为 "今天是哪天"（yyyy-MM-dd），用于补全截图中的相对日期（今天/昨天）与月日。
     *
     * 请求级错误（未配置 Key、网络失败、HTTP 错误）仍抛出异常；
     * 请求成功但返回内容无法解析时不抛，而是通过 [LlmParseDebugResult.parseError] 返回原因。
     */
    suspend fun parseTransactionsFromImagesWithDebug(
        images: List<CompressedImportImage>,
        today: String?,
        categories: List<CategoryEntity>,
    ): LlmParseDebugResult {
        if (images.isEmpty()) {
            throw IllegalStateException("没有选择任何图片")
        }

        val settings = loadSettings()
        val body = buildRequest(settings, images, today, categories)

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
        today: String?,
        categories: List<CategoryEntity>,
    ): String {
        // 截图里的日期常为“今天”“昨天”或只有月日（如 07-15），必须告知今天日期让模型推算补全
        val todayInstruction = today?.let { date ->
            "今天是 $date。截图中出现的“今天”“昨天”“前天”等相对日期，以及只有月日（如 07-15）的日期，" +
                "都必须按今天推算并补全为具体的年月日；occurred_at 输出完整的 ISO 8601 日期时间。"
        }.orEmpty()
        val userText =
            "输出 JSON 格式：{\"transactions\":[{\"occurred_at\":\"ISO 8601 时间\"," +
                "\"transaction_type\":0,\"amount\":\"28.50\",\"description\":\"交易摘要或 null\"," +
                "\"category\":\"分类名（必填，必须从可选分类中选一个，不允许 null）\"}]}\n" +
                todayInstruction +
                buildCategoryCandidateText(categories) +
                "\n请从下面的支付记录截图中逐条提取所有交易：图片上有几条就输出几条，一条都不要省略。"

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

    /**
     * 生成可选分类说明，让模型只从用户已有分类里选 category。
     * 按收支类型分组，一级分类独占一行、二级分类用 "- " 缩进；输出时也保持该层级，便于本地映射。
     */
    private fun buildCategoryCandidateText(categories: List<CategoryEntity>): String {
        if (categories.isEmpty()) {
            return "\n用户还没有创建任何分类，所有记录的 category 只能输出 null。"
        }

        fun formatGroup(typeName: String, list: List<CategoryEntity>): String {
            if (list.isEmpty()) return ""
            val lines = mutableListOf("[${typeName}分类]")
            list.filter { it.parentId == null }.forEach { parent ->
                lines += parent.categoryName
                list.filter { it.parentId == parent.categoryId }.forEach { child ->
                    lines += "  - ${child.categoryName}"
                }
            }
            return lines.joinToString("\n")
        }

        val groupText = listOf(
            formatGroup("支出", categories.filter { it.transactionType == TransactionEntity.TYPE_EXPENSE }),
            formatGroup("收入", categories.filter { it.transactionType == TransactionEntity.TYPE_INCOME }),
        ).filter { it.isNotBlank() }.joinToString("\n\n")

        return "\n可选分类（category 是必填字段，必须从下列分类中选一个最可能的分类名输出，绝不允许输出 null：优先选择带“- ”缩进的二级分类名，一步到位选到最具体的子分类；没有合适的二级分类就选一级分类名。收入记录只能选收入分类、支出记录只能选支出分类）：\n$groupText"
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
            val occurredAt = normalizeOccurredAt(row.occurredAt, today)
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
                    categoryName = row.category?.trim()?.takeIf { it.isNotEmpty() },
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

    private fun normalizeOccurredAt(value: String?, today: String?): String? {
        if (value.isNullOrBlank()) return null
        val raw = value.trim()

        // ISO 带时区
        runCatching { ZonedDateTime.parse(raw).toInstant() }
            .getOrNull()?.let { return TimeFormat.toIsoString(it) }
        runCatching { OffsetDateTime.parse(raw).toInstant() }
            .getOrNull()?.let { return TimeFormat.toIsoString(it) }

        // ISO 本地日期时间（无时区，如 2026-08-18T20:54:00 / 2026-08-18T20:54:00.000），
        // 按本地时区解释，与 JS new Date(value) 行为一致
        runCatching { LocalDateTime.parse(raw).atZone(ZoneId.systemDefault()).toInstant() }
            .getOrNull()?.let { return TimeFormat.toIsoString(it) }

        // 完整日期时间（本地时区）
        YEAR_FORMATS.forEach { formatter ->
            runCatching {
                LocalDateTime.parse(raw, formatter)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
            }.getOrNull()?.let { return TimeFormat.toIsoString(it) }
        }

        // 相对日期（今天 / 昨天 / 前天，可带时间）：按“今天是哪天”推算
        val todayDate = today?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (todayDate != null) {
            val relative = RELATIVE_DAY_REGEX.find(raw)
            if (relative != null) {
                val dayOffset = when (relative.groupValues[1]) {
                    "今天" -> 0L
                    "昨天" -> 1L
                    else -> 2L
                }
                val time = relative.groupValues[2].ifEmpty { "00:00" }
                runCatching {
                    LocalDateTime.of(todayDate.minusDays(dayOffset), LocalTime.parse(time))
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                }.getOrNull()?.let { return TimeFormat.toIsoString(it) }
            }
        }

        // 只有月日（如 07-15 12:30 / 7月15日 12:30）：用今天的年份补全
        val year = todayDate?.year
        if (year != null) {
            val normalized = raw.replace('/', '-')
            YEAR_FORMATS.forEach { formatter ->
                runCatching {
                    LocalDateTime.parse("$year-$normalized", formatter)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                }.getOrNull()?.let { return TimeFormat.toIsoString(it) }
            }
            // 中文月日格式：7月15日 / 7月15日 12:30
            val monthDay = MONTH_DAY_REGEX.find(raw)
            if (monthDay != null) {
                val month = monthDay.groupValues[1].toInt()
                val day = monthDay.groupValues[2].toInt()
                val time = monthDay.groupValues[3].trim().ifEmpty { "00:00" }
                runCatching {
                    LocalDateTime.of(LocalDate.of(year, month, day), LocalTime.parse(time))
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                }.getOrNull()?.let { return TimeFormat.toIsoString(it) }
            }
        }

        return null
    }

    companion object {
        private val YEAR_FORMATS = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        )

        /** 中文月日（如 7月15日 12:30），年份用“今天是哪天”补全。 */
        private val MONTH_DAY_REGEX = Regex("(\\d{1,2})月(\\d{1,2})日\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?)?")

        /** 相对日期（今天/昨天/前天）后跟可选时间，如 “今天 12:30”。 */
        private val RELATIVE_DAY_REGEX = Regex("(今天|昨天|前天)\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?)?")
    }
}
