package com.xdl.xledger.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---- Chat Completions 请求体（DeepSeek / OpenAI 兼容）----

@Serializable
data class ChatCompletionsRequest(
    val model: String,
    val messages: List<ChatMessage>,
    /** 千问等混合思考模型：false 直接回答（JSON 输出建议关闭），true 先思考再回答。 */
    @SerialName("enable_thinking")
    val enableThinking: Boolean? = null,
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: List<ChatContentItem>,
)

@Serializable
data class ChatContentItem(
    val type: String,
    val text: String? = null,
    @SerialName("image_url")
    val imageUrl: ChatImageUrl? = null,
)

@Serializable
data class ChatImageUrl(
    val url: String,
)

// ---- Chat Completions 响应 ----

@Serializable
data class LlmResponse(
    val choices: List<LlmResponseChoice>? = null,
)

@Serializable
data class LlmResponseChoice(
    val message: LlmResponseMessage? = null,
)

@Serializable
data class LlmResponseMessage(
    /** 可能是字符串，也可能是 OpenAI 新版数组形式 [{type:"text",...}]，用 JsonElement 兼容。 */
    val content: JsonElement? = null,
    /** DeepSeek 推理模型（R1 类）的思考过程。 */
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
    /** OpenAI 风格推理字段名。 */
    val reasoning: String? = null,
    /** 部分平台（如部分国产模型网关）的思考字段名。 */
    val thinking: String? = null,
)

// ---- 交易 JSON payload ----

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

// ---- 归一化结果 ----

/** 归一化后的解析结果。 */
data class ParsedTransaction(
    val occurredAt: String,
    val transactionType: Int,
    val amount: String,
    val description: String?,
)

/** 带调试信息的解析结果：保留模型原始返回与思考过程，便于排查识别问题。 */
data class LlmParseDebugResult(
    val transactions: List<ParsedTransaction>,
    val apiUrl: String,
    val model: String,
    val responseContent: String,
    /** 模型的思考过程（reasoning_content），无则为 null。 */
    val reasoningContent: String? = null,
    /** 请求成功但返回内容无法解析时非空。 */
    val parseError: String? = null,
)
