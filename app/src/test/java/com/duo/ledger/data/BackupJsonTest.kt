package com.duo.ledger.data

import com.duo.ledger.data.local.CategoryEntity
import com.duo.ledger.data.local.EntryType
import com.duo.ledger.data.local.MemberEntity
import com.duo.ledger.data.local.RecordEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonTest {
    private fun sample(): BackupData = BackupData(
        exportedAtMillis = 1_700_000_000_000L,
        members = listOf(
            MemberEntity(1, "我", 0, false),
            MemberEntity(2, "对象", 1, false),
            MemberEntity(3, "C", 2, true)
        ),
        categories = listOf(
            CategoryEntity(1, "餐饮", 0, true, false, EntryType.EXPENSE),
            CategoryEntity(2, "工资", 100, true, false, EntryType.INCOME)
        ),
        records = listOf(
            RecordEntity(1, EntryType.EXPENSE, 10000, 20000, 1, 1, "午饭", null, null, 100, 100, null),
            RecordEntity(2, EntryType.INCOME, 200000, 20001, 2, 2, "工资", "公司", "", 200, 200, null),
            RecordEntity(3, EntryType.TRANSFER_OUT, 30000, 20002, 1, null, "给朋友", "我", "朋友", 300, 300, null),
            RecordEntity(4, EntryType.TRANSFER, 50000, 20003, 1, null, "互转", "我", "对象", 400, 400, 999)
        ),
        preferences = BackupPreferences(
            lastMemberId = 1,
            lastCategoryId = 1,
            weekStartsMonday = true,
            aiModel = "deepseek-flash",
            aiBaseUrl = "https://api.deepseek.com/v1",
            aiEnabled = false
        )
    )

    @Test
    fun roundTripKeepsEveryFieldIncludingSoftDeleted() {
        val original = sample()
        val json = buildBackupJson(original)
        val parsed = parseBackupJson(json)
        assertEquals(original, parsed)
        assertEquals(4, parsed.records.size)
        assertEquals(999L, parsed.records.last().deletedAt)
        assertEquals(3, parsed.activeRecordCount)
        assertEquals(3, parsed.info().activeRecordCount)
        assertEquals(1, parsed.info().deletedRecordCount)
        validateBackup(parsed)
    }

    @Test
    fun apiKeyIsNeverWrittenIntoBackup() {
        val root = JSONObject(buildBackupJson(sample()))
        assertEquals(BACKUP_SCHEMA_VERSION, root.getInt("schemaVersion"))
        assertEquals(BACKUP_APP_ID, root.getString("app"))
        assertFalse(root.getJSONObject("preferences").has("aiApiKey"))
        assertFalse(root.toString().contains("sk-"))
    }

    @Test
    fun missingPreferencesFallsBackToDefaults() {
        val root = JSONObject(buildBackupJson(sample()))
        root.remove("preferences")
        val parsed = parseBackupJson(root.toString())
        assertEquals(LedgerPreferences.DEFAULT_AI_MODEL, parsed.preferences.aiModel)
        assertTrue(parsed.preferences.weekStartsMonday)
        validateBackup(parsed)
    }

    @Test
    fun rejectsWrongVersionAndForeignApp() {
        val json = buildBackupJson(sample())
        val wrongVersion = JSONObject(json).put("schemaVersion", 99).toString()
        assertThrows(IllegalArgumentException::class.java) { parseBackupJson(wrongVersion) }
        val foreign = JSONObject(json).put("app", "some.other.app").toString()
        assertThrows(IllegalArgumentException::class.java) { parseBackupJson(foreign) }
    }

    @Test
    fun rejectsMalformedJson() {
        assertThrows(IllegalArgumentException::class.java) { parseBackupJson("{oops") }
        val missing = JSONObject(buildBackupJson(sample())).apply { remove("records") }.toString()
        assertThrows(IllegalArgumentException::class.java) { parseBackupJson(missing) }
    }

    @Test
    fun rejectsUnknownEnumValues() {
        val badType = JSONObject(buildBackupJson(sample()))
        badType.getJSONArray("records").getJSONObject(0).put("type", "MAGIC")
        assertThrows(IllegalArgumentException::class.java) { parseBackupJson(badType.toString()) }

        val badKind = JSONObject(buildBackupJson(sample()))
        badKind.getJSONArray("categories").getJSONObject(0).put("kind", "TRANSFER")
        assertThrows(IllegalArgumentException::class.java) { parseBackupJson(badKind.toString()) }
    }

    @Test
    fun rejectsDanglingForeignKeys() {
        val badMember = JSONObject(buildBackupJson(sample()))
        badMember.getJSONArray("records").getJSONObject(0).put("memberId", 999)
        val danglingMember = parseBackupJson(badMember.toString())
        assertThrows(IllegalArgumentException::class.java) { validateBackup(danglingMember) }

        val badCategory = JSONObject(buildBackupJson(sample()))
        badCategory.getJSONArray("records").getJSONObject(0).put("categoryId", 999)
        val danglingCategory = parseBackupJson(badCategory.toString())
        assertThrows(IllegalArgumentException::class.java) { validateBackup(danglingCategory) }
    }

    @Test
    fun rejectsDuplicateIdsAndEmptyMembers() {
        val duplicate = sample().let {
            it.copy(records = it.records + it.records.first().copy(purpose = "重复 id"))
        }
        assertThrows(IllegalArgumentException::class.java) { validateBackup(duplicate) }
        assertThrows(IllegalArgumentException::class.java) {
            validateBackup(sample().copy(members = emptyList()))
        }
    }

    @Test
    fun rejectsPreferencesPointingAtMissingEntities() {
        assertThrows(IllegalArgumentException::class.java) {
            validateBackup(sample().copy(preferences = BackupPreferences(lastMemberId = 999)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateBackup(sample().copy(preferences = BackupPreferences(lastCategoryId = 999)))
        }
    }

    @Test
    fun completeRollbackRevivesDeletedRecordsAndMembers() {
        val original = sample()
        val revived = original.reviveDeleted()

        assertTrue(revived.members.all { !it.archived })
        assertTrue(revived.categories.all { !it.archived })
        assertTrue(revived.records.all { it.deletedAt == null })
        assertEquals(0, revived.info().deletedRecordCount)
        assertEquals(4, revived.activeRecordCount)

        // 只翻状态，不动 id / 金额 / 偏好，且原始快照不被就地修改。
        assertEquals(original.members.map { it.id }, revived.members.map { it.id })
        assertEquals(original.records.map { it.amountCents }, revived.records.map { it.amountCents })
        assertEquals(original.preferences, revived.preferences)
        assertEquals(999L, original.records.last().deletedAt)
        validateBackup(revived)
    }
}
