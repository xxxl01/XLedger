package com.xdl.xledger.data.remote

/** LLM 配置键名与默认值（与旧版一致）。 */
object LlmConfigKeys {
    const val BASE_URL = "llm_base_url"
    const val API_KEY = "llm_api_key"
    const val MODEL = "llm_model"
}

const val DEFAULT_LLM_BASE_URL = "https://api.openai.com/v1"
const val DEFAULT_LLM_MODEL = "gpt-4.1-mini"
