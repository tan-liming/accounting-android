package com.duo.ledger.ai

const val DEFAULT_AI_BASE_URL = "https://api.deepseek.com"

/**
 * 允许填官方地址、/v1 兼容地址、代理/中转地址，甚至完整的 chat/completions endpoint。
 * 纯函数，便于单测；URL 不做协议白名单，由用户自己负责填可信地址。
 */
fun buildEndpoint(baseUrl: String): String {
    val base = baseUrl.trim().trimEnd('/')
    if (base.isEmpty()) return "$DEFAULT_AI_BASE_URL/chat/completions"
    return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
}
