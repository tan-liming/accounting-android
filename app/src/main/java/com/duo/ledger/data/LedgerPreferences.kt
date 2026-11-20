package com.duo.ledger.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.duo.ledger.ai.DEFAULT_AI_BASE_URL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ledger_settings")

/** 只放轻量偏好；流水一律走 Room，不用 DataStore 存业务数据。 */
class LedgerPreferences(private val context: Context) {
    companion object {
        const val DEFAULT_AI_MODEL = "deepseek-flash"
    }

    private object Keys {
        val LAST_MEMBER = longPreferencesKey("last_member_id")
        val LAST_CATEGORY = longPreferencesKey("last_category_id")
        val WEEK_STARTS_MONDAY = booleanPreferencesKey("week_starts_monday")
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val AI_ENABLED = booleanPreferencesKey("ai_enabled")
    }

    val lastMemberId: Flow<Long?> = context.dataStore.data.map { it[Keys.LAST_MEMBER] }
    val lastCategoryId: Flow<Long?> = context.dataStore.data.map { it[Keys.LAST_CATEGORY] }
    val weekStartsMonday: Flow<Boolean> = context.dataStore.data.map { it[Keys.WEEK_STARTS_MONDAY] ?: true }

    // Key 只存在应用私有目录，绝不写进代码或版本库。
    val aiApiKey: Flow<String> = context.dataStore.data.map { it[Keys.AI_API_KEY] ?: "" }
    val aiModel: Flow<String> = context.dataStore.data.map { it[Keys.AI_MODEL] ?: DEFAULT_AI_MODEL }
    val aiBaseUrl: Flow<String> = context.dataStore.data.map { it[Keys.AI_BASE_URL] ?: DEFAULT_AI_BASE_URL }
    val aiEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AI_ENABLED] ?: true }

    suspend fun setLastMember(id: Long) {
        context.dataStore.edit { it[Keys.LAST_MEMBER] = id }
    }

    suspend fun setLastCategory(id: Long) {
        context.dataStore.edit { it[Keys.LAST_CATEGORY] = id }
    }

    suspend fun setWeekStartsMonday(value: Boolean) {
        context.dataStore.edit { it[Keys.WEEK_STARTS_MONDAY] = value }
    }

    suspend fun saveAiSettings(apiKey: String, model: String, enabled: Boolean, baseUrl: String) {
        context.dataStore.edit {
            it[Keys.AI_API_KEY] = apiKey.trim()
            it[Keys.AI_MODEL] = model.trim().ifEmpty { DEFAULT_AI_MODEL }
            it[Keys.AI_ENABLED] = enabled
            it[Keys.AI_BASE_URL] = baseUrl.trim().ifEmpty { DEFAULT_AI_BASE_URL }
        }
    }

    /**
     * 恢复备份里的偏好。**刻意不写 AI_API_KEY**：备份是明文文件，
     * 恢复后 Key 保持用户当前填的值（新装则为空），不随备份覆盖。
     */
    suspend fun restoreFrom(p: BackupPreferences) {
        context.dataStore.edit {
            if (p.lastMemberId != null) it[Keys.LAST_MEMBER] = p.lastMemberId else it.remove(Keys.LAST_MEMBER)
            if (p.lastCategoryId != null) it[Keys.LAST_CATEGORY] = p.lastCategoryId else it.remove(Keys.LAST_CATEGORY)
            it[Keys.WEEK_STARTS_MONDAY] = p.weekStartsMonday
            it[Keys.AI_MODEL] = p.aiModel
            it[Keys.AI_BASE_URL] = p.aiBaseUrl
            it[Keys.AI_ENABLED] = p.aiEnabled
        }
    }
}
