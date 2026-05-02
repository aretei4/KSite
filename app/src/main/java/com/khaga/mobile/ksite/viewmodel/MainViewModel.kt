package com.khaga.mobile.ksite.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.khaga.mobile.ksite.data.model.*
import com.khaga.mobile.ksite.data.repository.KhagaRepository
import kotlinx.coroutines.launch
import java.util.UUID

// ── Shared ViewModel (all screens share one repo instance) ───────────────────
class MainViewModel(app: Application) : AndroidViewModel(app) {

    val repo = KhagaRepository(app)

    // LiveData
    val sites        = repo.sitesLive
    val workers      = repo.workersLive
    val payments     = repo.paymentsLive
    val collections  = repo.collectionsLive
    val monthCloses  = repo.monthClosesLive

    // One-off status
    private val _status = MutableLiveData<String?>()
    val status: LiveData<String?> = _status

    private val _backupStatus = MutableLiveData<String?>()
    val backupStatus: LiveData<String?> = _backupStatus

    // ── Sites ────────────────────────────────────────────────────────────
    fun upsertSite(site: Site) = viewModelScope.launch { repo.upsertSite(site) }
    fun deleteSite(id: String) = viewModelScope.launch { repo.deleteSite(id) }

    // ── Workers ──────────────────────────────────────────────────────────
    fun upsertWorker(worker: Worker) = viewModelScope.launch { repo.upsertWorker(worker) }
    fun deleteWorker(id: String) = viewModelScope.launch { repo.deleteWorker(id) }

    // ── Payments ─────────────────────────────────────────────────────────
    fun upsertPayment(p: Payment) = viewModelScope.launch { repo.upsertPayment(p) }
    fun deletePayment(id: String) = viewModelScope.launch { repo.deletePayment(id) }

    // ── Collections ──────────────────────────────────────────────────────
    fun upsertCollection(c: SiteCollection) = viewModelScope.launch { repo.upsertCollection(c) }
    fun deleteCollection(id: String) = viewModelScope.launch { repo.deleteCollection(id) }

    // ── Month Close ──────────────────────────────────────────────────────
    fun insertMonthClose(mc: MonthClose) = viewModelScope.launch { repo.insertMonthClose(mc) }
    fun deleteMonthClose(id: String) = viewModelScope.launch { repo.deleteMonthClose(id) }

    // ── Month close calculation ──────────────────────────────────────────
    suspend fun calcMonthClose(workerId: String, siteId: String, month: String): MonthCloseSummary {
        val payments = repo.paymentsByWorkerSiteMonth(workerId, siteId, month)
        val adv   = payments.filter { it.head == "Advance"     }.sumOf { it.amount }
        val tra   = payments.filter { it.head == "Travel"      }.sumOf { it.amount }
        val other = payments.filter { !listOf("Wages","Advance","Travel").contains(it.head) }.sumOf { it.amount }
        return MonthCloseSummary(adv, tra, other, adv + tra + other)
    }

    // ── Settings ─────────────────────────────────────────────────────────
    var apiKey: String
        get() = repo.apiKey
        set(v) { repo.apiKey = v }

    var binId: String?
        get() = repo.binId
        set(v) { repo.binId = v }

    fun backup() = viewModelScope.launch {
        _backupStatus.value = "saving"
        val result = repo.backup()
        _backupStatus.value = when (result) {
            is KhagaRepository.BackupResult.Success -> "ok:${result.binId}"
            is KhagaRepository.BackupResult.Error   -> "err:${result.message}"
        }
    }

    fun restore(id: String) = viewModelScope.launch {
        _backupStatus.value = "restoring"
        val result = repo.restore(id)
        _backupStatus.value = when (result) {
            is KhagaRepository.BackupResult.Success -> "restored"
            is KhagaRepository.BackupResult.Error   -> "err:${result.message}"
        }
    }

    fun clearBackupStatus() { _backupStatus.value = null }
    fun newId() = UUID.randomUUID().toString()
}

data class MonthCloseSummary(
    val advanceTaken: Long,
    val travelTaken: Long,
    val otherTaken: Long,
    val totalTaken: Long
)
