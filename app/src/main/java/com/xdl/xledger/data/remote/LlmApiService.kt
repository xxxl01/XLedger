package com.xdl.xledger.data.remote

import com.xdl.xledger.data.db.entity.TransactionEntity
import com.xdl.xledger.data.repository.ConfigRepository
import com.xdl.xledger.util.CompressedImportImage
import com.xdl.xledger.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * LLM 截图识别服务（OpenAI Responses API），对应旧版 service/llm.ts。
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
        "你从支付记录列表截图中提取交易。直接阅读图片内容，按列表顺序识别商户/说明、日期时间和金额。" +
            "只输出 JSON，不要解释。不要输出分类。金额始终输出正数，收支方向用 0 表示支出、1 表示收入。" +
            "不确定或缺关键字段的记录不要输出。多张图片可能包含连续记录，也可能有重复记录，重复记录只输出一次。"

    suspend fun parseTransactionsFromImages(
        images: List<CompressedImportImage>,
        transactionYear: String?,
    ): List<ParsedTransaction> {
        if (images.isEmpty()) {
            throw IllegalStateException("没有选择任何图片")
        }

        val settings = loadSettings()
        val input = buildInput(images, transactionYear)
        val body = json.encodeToString(
            LlmRequest.serializer(),
            LlmRequest(model = settings.model, input = input),
        )

        val responseText = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(settings.apiUrl)
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

        val content = extractResponseText(json.decodeFromString(LlmResponse.serializer(), responseText))
            ?: throw IllegalStateException("LLM 返回了空响应")

        return normalizePayload(extractJsonObject(content), transactionYear)
    }

    private data class LlmSettings(
        val apiKey: String,
        val apiUrl: String,
        val model: String,
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
        return LlmSettings(
            apiKey = apiKey,
            apiUrl = "${baseUrl.trimEnd('/')}/responses",
            model = model,
        )
    }

    private fun buildInput(
        images: List<CompressedImportImage>,
        transactionYear: String?,
    ): List<LlmMessage> {
        val yearInstruction = transactionYear?.let { year ->
            "所有交易的 occurred_at 年份必须使用 $year 年；截图中只有月日或时间时，也必须补全为 $year 年。"
        }.orEmpty()

        return listOf(
            LlmMessage(
                role = "system",
                content = listOf(LlmContentItem(type = "input_text", text = systemPrompt)),
            ),
            LlmMessage(
                role = "user",
                content = buildList {
                    add(
                        LlmContentItem(
                            type = "input_text",
                            text = "输出 JSON 格式：{\"transactions\":[{\"occurred_at\":\"ISO 8601 时间\"," +
                                "\"transaction_type\":0,\"amount\":\"28.50\",\"description\":\"交易摘要或 null\"}]}\n" +
                                yearInstruction +
                                "\n请从下面的支付记录截图中提取所有有效交易。",
                        ),
                    )
                    images.forEach { image ->
                        add(
                            LlmContentItem(
                                type = "input_image",
                                imageUrl = image.dataUrl,
                                detail = "auto",
                            ),
                        )
                    }
                },
            ),
        )
    }

    private fun extractResponseText(response: LlmResponse): String? {
        response.outputText?.takeIf { it.isNotEmpty() }?.let { return it }
        return response.output
            ?.flatMap { it.content.orEmpty() }
            ?.map { it.text }
            ?.firstOrNull { !it.isNullOrEmpty() }
    }

    private fun extractJsonObject(content: String): LlmTransactionPayload {
        val trimmed = content.trim()
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            .find(trimmed)
        val jsonText = fenced?.groupValues?.get(1)?.trim() ?: trimmed
        return json.decodeFromString(LlmTransactionPayload.serializer(), jsonText)
    }

    private fun normalizePayload(
        payload: LlmTransactionPayload,
        transactionYear: String?,
    ): List<ParsedTransaction> {
        val rows = payload.transactions.orEmpty()
        val result = mutableListOf<ParsedTransaction>()

        for (row in rows) {
            val occurredAt = normalizeOccurredAt(row.occurredAt, transactionYear)
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

    private fun normalizeOccurredAt(value: String?, transactionYear: String?): String? {
        if (value.isNullOrBlank()) return null
        val raw = value.trim()

        // ISO 带时区
        runCatching { ZonedDateTime.parse(raw).toInstant() }
            .getOrNull()?.let { return TimeFormat.toIsoString(it) }
        runCatching { OffsetDateTime.parse(raw).toInstant() }
            .getOrNull()?.let { return TimeFormat.toIsoString(it) }

        // 完整日期时间（本地时区）
        YEAR_FORMATS.forEach { formatter ->
            runCatching {
                LocalDateTime.parse(raw, formatter)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
            }.getOrNull()?.let { return TimeFormat.toIsoString(it) }
        }

        // 只有月日（用识别年份补全后按完整格式解析）
        val year = transactionYear?.toIntOrNull()
        if (year != null) {
            val normalized = raw.replace('/', '-')
            YEAR_FORMATS.forEach { formatter ->
                runCatching {
                    LocalDateTime.parse("$year-$normalized", formatter)
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
    }
}
