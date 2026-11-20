package com.duo.ledger.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.duo.ledger.AppContainer
import com.duo.ledger.ai.AiDraft
import com.duo.ledger.ai.DeepSeekClient
import com.duo.ledger.ai.validateAiDraft
import com.duo.ledger.data.BackupData
import com.duo.ledger.data.LedgerPreferences
import com.duo.ledger.data.LedgerSummary
import com.duo.ledger.data.parseBackupJson
import com.duo.ledger.data.validateBackup
import com.duo.ledger.data.Period
import com.duo.ledger.data.PeriodRange
import com.duo.ledger.data.rangeOf
import com.duo.ledger.data.shift
import com.duo.ledger.data.local.ActiveRecord
import com.duo.ledger.data.local.CategoryEntity
import com.duo.ledger.data.local.EntryType
import com.duo.ledger.data.local.MemberEntity
import com.duo.ledger.data.local.MemberImpact
import com.duo.ledger.util.yuanToCents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class Tab(val label: String) {
    ADD("记一笔"),
    RECORDS("流水"),
    STATS("统计")
}

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppContainer.repository(app)
    private val aiClient = DeepSeekClient()

    var tab by mutableStateOf(Tab.RECORDS)
        private set
    var period by mutableStateOf(Period.MONTH)
        private set
    var anchor by mutableStateOf(LocalDate.now())
        private set
    var memberFilter by mutableStateOf<Long?>(null)
        private set
    var typeFilter by mutableStateOf<EntryType?>(null)
        private set

    val members: StateFlow<List<MemberEntity>> = repo.members
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    /** 含已删除成员，仅用于把历史流水里的 memberId 映射成名字。 */
    val allMembers: StateFlow<List<MemberEntity>> = repo.allMembers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories: StateFlow<List<CategoryEntity>> = repo.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    /** 含已删除分类，仅用于把历史流水里的 categoryId 映射成名字。 */
    val allCategories: StateFlow<List<CategoryEntity>> = repo.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    // 偏好类 Flow 用 Eagerly：它们会被 .value 直接读取（识别、导出区间）。
    // WhileSubscribed 在订阅者归零 5 秒后会把值重置成初始值，
    // 会让「明明填过 Key」被误判成未配置，也会让设置页刚打开时显示空白。
    val lastMemberId: StateFlow<Long?> = repo.preferences.lastMemberId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val lastCategoryId: StateFlow<Long?> = repo.preferences.lastCategoryId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val aiApiKey: StateFlow<String> = repo.preferences.aiApiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val aiModel: StateFlow<String> = repo.preferences.aiModel
        .stateIn(viewModelScope, SharingStarted.Eagerly, LedgerPreferences.DEFAULT_AI_MODEL)
    val aiBaseUrl: StateFlow<String> = repo.preferences.aiBaseUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.duo.ledger.ai.DEFAULT_AI_BASE_URL)
    val aiEnabled: StateFlow<Boolean> = repo.preferences.aiEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val weekStartsMonday: StateFlow<Boolean> = repo.preferences.weekStartsMonday
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val range: StateFlow<PeriodRange> = combine(
        snapshotFlow { period },
        snapshotFlow { anchor },
        weekStartsMonday
    ) { p, a, monday -> p.rangeOf(a, monday) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Period.MONTH.rangeOf(LocalDate.now()))

    val records: StateFlow<List<ActiveRecord>> = combine(
        range,
        snapshotFlow { memberFilter },
        snapshotFlow { typeFilter }
    ) { r, m, t -> Triple(r, m, t) }
        .flatMapLatest { (r, m, t) -> repo.records(r, m, t) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 汇总只跟随周期与成员筛选；类型筛选只影响流水列表。 */
    val summary: StateFlow<LedgerSummary> = combine(
        range,
        snapshotFlow { memberFilter }
    ) { r, m -> r to m }
        .flatMapLatest { (r, m) -> repo.summary(r, m) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LedgerSummary.EMPTY)

    private var lastDeletedId: Long? = null

    // 方法名不能与 mutableStateOf 为属性自动生成的 setXxx 相同，否则 JVM 签名冲突。
    fun selectTab(value: Tab) { tab = value }
    fun selectPeriod(value: Period) { period = value }
    fun shiftPeriod(delta: Int) { anchor = period.shift(anchor, delta) }
    fun filterByMember(value: Long?) { memberFilter = value }
    fun filterByType(value: EntryType?) { typeFilter = value }

    /** 方法名避开 mutableStateOf 为 anchor 自动生成的 setAnchor，防止 JVM 签名冲突。 */
    fun jumpToDate(value: LocalDate) { anchor = value }

    /** AI 只填表不写库：这里只返回草稿，最终保存仍由 vm.save 在用户点击后执行。 */
    suspend fun recognize(text: String): Result<AiDraft> {
        // 识别是信任边界，直接读 DataStore 当前值，不读面向 UI 的 StateFlow 缓存：
        // 缓存可能还停在初始值，也避免「保存设置尚未落盘就点识别」的竞态。
        val prefs = repo.preferences
        if (!prefs.aiEnabled.first()) {
            return Result.failure(IllegalStateException("AI 识别已在设置里关闭"))
        }
        val key = prefs.aiApiKey.first()
        if (key.isBlank()) {
            return Result.failure(IllegalStateException("请先在右上角设置里填写 DeepSeek API Key"))
        }
        val model = prefs.aiModel.first()
        val baseUrl = prefs.aiBaseUrl.first()
        val memberList = repo.members.first()
        val categoryList = repo.categories.first()
        val expenseCats = categoryList.filter { it.kind == EntryType.EXPENSE }
        val incomeCats = categoryList.filter { it.kind == EntryType.INCOME }

        val today = LocalDate.now()
        val rawResult = aiClient.recognize(
            apiKey = key,
            model = model,
            baseUrl = baseUrl,
            today = today,
            text = text,
            memberNames = memberList.map { it.name },
            expenseCategories = expenseCats.map { it.name },
            incomeCategories = incomeCats.map { it.name }
        )
        val raw = rawResult.getOrNull()
            ?: return Result.failure(rawResult.exceptionOrNull() ?: IllegalStateException("识别失败"))

        val draft = validateAiDraft(
            raw = raw,
            members = memberList.map { it.id to it.name },
            expenseCategories = expenseCats.map { it.id to it.name },
            incomeCategories = incomeCats.map { it.id to it.name },
            defaultMemberId = prefs.lastMemberId.first() ?: memberList.firstOrNull()?.id,
            today = today
        ) ?: return Result.failure(IllegalStateException("模型没给出可用金额，请换句话试试"))
        return Result.success(draft)
    }

    /** suspend 让设置页等写盘完成后再关闭，避免保存后立即识别读到旧值。 */
    suspend fun saveAiSettings(key: String, model: String, enabled: Boolean, baseUrl: String) {
        repo.preferences.saveAiSettings(key, model, enabled, baseUrl)
    }

    // 全量备份/恢复：解析在 IO 上做，确认框看到的数据就是实际恢复的那份。
    suspend fun exportBackup(): String = repo.exportBackup()

    suspend fun inspectBackup(text: String): Result<BackupData> = withContext(Dispatchers.IO) {
        runCatching {
            val data = parseBackupJson(text)
            validateBackup(data)
            data
        }
    }

    suspend fun restoreBackup(data: BackupData, reviveDeleted: Boolean): Result<Int> = runCatching {
        repo.restoreBackup(data, reviveDeleted)
        data.records.size
    }

    /** 四种流水类型共用一条保存路径；金额校验集中在信任边界做一次。 */
    fun save(
        type: EntryType,
        amountText: String,
        day: Long,
        memberId: Long,
        categoryId: Long?,
        purpose: String,
        fromParty: String? = null,
        toParty: String? = null
    ): Boolean {
        val cents = amountText.yuanToCents() ?: return false
        // 负数只允许用于支出红冲（退款/报销），其他类型必须为正。
        if (cents == 0L || (type != EntryType.EXPENSE && cents < 0L)) return false
        viewModelScope.launch {
            repo.addRecord(type, cents, day, memberId, categoryId, purpose, fromParty, toParty)
            repo.preferences.setLastMember(memberId)
            if (categoryId != null) repo.preferences.setLastCategory(categoryId)
        }
        return true
    }

    /** 编辑与新增共用金额校验口径；编辑不改写「上次成员/分类」偏好，免得影响下次新增。 */
    fun saveEdit(
        id: Long,
        type: EntryType,
        amountText: String,
        day: Long,
        memberId: Long,
        categoryId: Long?,
        purpose: String,
        fromParty: String? = null,
        toParty: String? = null
    ): Boolean {
        val cents = amountText.yuanToCents() ?: return false
        if (cents == 0L || (type != EntryType.EXPENSE && cents < 0L)) return false
        viewModelScope.launch {
            repo.updateRecord(id, type, cents, day, memberId, categoryId, purpose, fromParty, toParty)
        }
        return true
    }

    /** 返回新分类 id 供选择器自动选中；空名返回 null。 */
    suspend fun addCategory(name: String, kind: EntryType): Long? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        return repo.addCategory(trimmed, kind).takeIf { it > 0 }
    }

    fun renameCategory(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repo.renameCategory(id, trimmed) }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch { repo.deleteCategory(id) }
    }

    fun restoreCategory(id: Long) {
        viewModelScope.launch { repo.restoreCategory(id) }
    }

    fun addMember(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repo.addMember(trimmed) }
    }

    fun renameMember(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repo.renameMember(id, trimmed) }
    }

    /** 二次确认框用：该成员名下还有多少有效流水、多少钱。 */
    suspend fun memberImpact(id: Long): MemberImpact = repo.memberImpact(id)

    private data class DeletedMember(val id: Long, val name: String, val recordIds: List<Long>)
    private var lastDeletedMember: DeletedMember? = null

    /** 返回 false 表示被「至少保留一个成员」规则拒绝。 */
    suspend fun deleteMember(id: Long, name: String): Boolean {
        val recordIds = repo.deleteMember(id) ?: return false
        lastDeletedMember = DeletedMember(id, name, recordIds)
        // 成员被删后 chips 里已无此项，筛选若还指向它会留下一个看不懂的空筛选。
        if (memberFilter == id) memberFilter = null
        return true
    }

    fun undoDeleteMember() {
        val item = lastDeletedMember ?: return
        lastDeletedMember = null
        viewModelScope.launch { repo.undoDeleteMember(item.id, item.recordIds) }
    }

    /** Snackbar 的 3 秒窗口过后调用，撤销入口随提示一起消失。 */
    fun clearUndoMember() { lastDeletedMember = null }

    /** 恢复已删除成员（不改动流水）；撤销窗口过后或旧版遗留成员用。 */
    fun restoreMember(id: Long) {
        viewModelScope.launch { repo.restoreMember(id) }
    }

    fun delete(id: Long) {
        lastDeletedId = id
        viewModelScope.launch { repo.delete(id) }
    }

    fun undoDelete() {
        val id = lastDeletedId ?: return
        lastDeletedId = null
        viewModelScope.launch { repo.restore(id) }
    }

    /** 导出范围按当前 anchor 重新算，不受页面正在看的周期类型影响。 */
    fun rangeFor(period: Period): PeriodRange = period.rangeOf(anchor, weekStartsMonday.value)

    suspend fun exportCsv(range: PeriodRange?, memberId: Long?): String =
        repo.exportCsv(range, memberId)
}
