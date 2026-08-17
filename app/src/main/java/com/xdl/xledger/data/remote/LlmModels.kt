package com.xdl.xledger.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** LLM 请求体（OpenAI Responses API 兼容）。 */
@Serializable
data class LlmRequest(
    val model: String,
    val input: List<LlmMessage>,
    val text: LlmTextConfig = LlmTextConfig(),
)

@Serializable
data class LlmTextConfig(
    val format: LlmTextFormat = LlmTextFormat(),
)

@Serializable
data class LlmTextFormat(
    val type: String = "json_object",
)

@Serializable
data class LlmMessage(
    val role: String,
    val content: List<LlmContentItem>,
)

@Serializable
data class LlmContentItem(
    val type: String,
    val text: String? = null,
    @SerialName("image_url")
    val imageUrl: String? = null,
    val detail: String? = null,
)

/** LLM 响应。 */
@Serializable
data class LlmResponse(
    @SerialName("output_text")
    val outputText: String? = null,
    val output: List<LlmResponseOutput>? = null,
)

@Serializable
data class LlmResponseOutput(
    val content: List<LlmResponseContent>? = null,
)

@Serializable
data class LlmResponseContent(
    val type: String? = null,
    val text: String? = null,
)

/** LLM 返回的交易 JSON payload。 */
@Serializable
data class LlmTransactionPayload(
    val transactions: List<LlmTransactionRow>? = null,
)

@Serializable
data class LlmTransactionRow(
    @SerialName("occurred_at")
    val occurredAt: String? = null,
    @SerialName("transaction_type")
    val transactionType: JsonElement? = null,
    val amount: JsonElement? = null,
    val description: String? = null,
)

/** 归一化后的解析结果。 */
data class ParsedTransaction(
    val occurredAt: String,
    val transactionType: Int,
    val amount: String,
    val description: String?,
)
