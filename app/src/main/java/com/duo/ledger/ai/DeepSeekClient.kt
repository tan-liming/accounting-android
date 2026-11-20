package com.duo.ledger.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.time.LocalDate

private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 20_000

/** 只在用户主动点「识别」时调用；请求体不含任何历史流水、金额或结余。 */
class DeepSeekClient {
    suspend fun recognize(
        apiKey: String,
        model: String,
        baseUrl: String,
        today: LocalDate,
        text: String,
        memberNames: List<String>,
        expenseCategories: List<String>,
        incomeCategories: List<String>
    ): Result<AiRawDraft> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put(
                    "content",
                    buildSystemPrompt(today, memberNames, expenseCategories, incomeCategories)
                ))
                put(JSONObject().put("role", "user").put("content", text))
            })
            // 官方 JSON Output：必须同时设置 response_format 且 prompt 里出现 json 与示例
            put("response_format", JSONObject().put("type", "json_object"))
            put("max_tokens", 300)
            put("temperature", 0.0)
            put("stream", false)
        }

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(buildEndpoint(baseUrl)).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException(errorMessage(code)))
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val content = JSONObject(response)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content", "")
            // 官方坑：非法 JSON 会被替换成空格，content 可能全是空白。
            if (content.isBlank()) {
                return@withContext Result.failure(IllegalStateException("模型未返回有效内容，请重试"))
            }
            val raw = try {
                parseRaw(content)
            } catch (e: JSONException) {
                null
            }
            if (raw == null) {
                Result.failure(IllegalStateException("模型返回格式异常，请重试"))
            } else {
                Result.success(raw)
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(IllegalStateException("网络超时，请重试"))
        } catch (e: IOException) {
            Result.failure(IllegalStateException("网络不可用，请检查连接"))
        } catch (e: JSONException) {
            Result.failure(IllegalStateException("接口响应解析失败"))
        } finally {
            connection?.disconnect()
        }
    }

    private fun errorMessage(code: Int): String = when (code) {
        401 -> "API Key 无效，请到设置里检查"
        402 -> "账户余额不足，请先充值"
        429 -> "请求过于频繁，稍后再试"
        else -> "接口返回错误码 $code"
    }

    private fun parseRaw(content: String): AiRawDraft {
        val o = JSONObject(content)
        return AiRawDraft(
            type = o.optString("type", ""),
            amount = o.optString("amount", ""),
            purpose = o.optString("purpose", ""),
            member = o.optString("member", ""),
            category = o.optString("category", ""),
            counterparty = o.optString("counterparty", ""),
            date = o.optString("date", "")
        )
    }

    private fun buildSystemPrompt(
        today: LocalDate,
        memberNames: List<String>,
        expenseCategories: List<String>,
        incomeCategories: List<String>
    ): String = """
你是记账助手。把用户的一句话解析成 json 对象，只输出 json，不要任何解释文字。

今天是 $today（yyyy-MM-dd）。用户说「今天」「昨天」「上周三」「上个月」等相对日期时，请按今天换算成 yyyy-MM-dd。

type 只能取以下四值之一：
- EXPENSE：消费、买东西、吃饭等支出
- INCOME：工资、奖金、红包、别人给我们的钱
- TRANSFER_OUT：我们转钱给外部的人
- TRANSFER：成员之间、银行卡与微信等主体内部划转
判断不了就填 EXPENSE。

成员名单（member 只能是其中之一）：${memberNames.joinToString("、")}
支出分类（type=EXPENSE 时 category 只能是其中之一）：${expenseCategories.joinToString("、")}
收入分类（type=INCOME 时 category 只能是其中之一）：${incomeCategories.joinToString("、")}
转账类型不要填 category。

示例 json：
{"type":"EXPENSE","amount":"35.50","purpose":"午饭","member":"我","category":"餐饮","counterparty":"","date":"$today"}

字段说明：
- amount：纯数字字符串，最多两位小数，不带货币符号和千分位
- purpose：简短用途，不超过 20 字
- member：从成员名单里选，判断不了就留空
- counterparty：对外转出时填「转给谁」，收入时填「谁给的」，其他类型留空
- date：ISO 日期 yyyy-MM-dd；用户没提日期或无法确定就留空（应用会默认今天）
""".trimIndent()
}
