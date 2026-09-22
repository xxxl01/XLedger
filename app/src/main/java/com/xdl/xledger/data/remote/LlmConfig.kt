package com.xdl.xledger.data.remote

/** LLM 配置键名与默认值。 */
object LlmConfigKeys {
    const val BASE_URL = "llm_base_url"
    const val API_KEY = "llm_api_key"
    const val MODEL = "llm_model"

    /** 千问等混合思考模型：true 开启思考，false 直接回答（默认关，识别类任务更快更省 token）。 */
    const val ENABLE_THINKING = "llm_enable_thinking"
}

const val DEFAULT_LLM_BASE_URL = "https://api.openai.com/v1"
const val DEFAULT_LLM_MODEL = "gpt-4.1-mini"
