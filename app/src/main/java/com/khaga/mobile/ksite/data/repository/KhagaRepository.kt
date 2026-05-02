package com.khaga.mobile.ksite.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.khaga.mobile.ksite.data.db.KhagaDatabase
import com.khaga.mobile.ksite.data.model.*
import com.khaga.mobile.ksite.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class KhagaRepository(private val context: Context) {

    private val db = KhagaDatabase.getInstance(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("khaga_prefs", Context.MODE_PRIVATE)

    // ── Prefs ──────────────────────────────────────────────────────────────
    var binId: String?
        get() = prefs.getString("bin_id", null)
        set(v) = prefs.edit().putString("bin_id", v).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(v) = prefs.edit().putString("api_key", v).apply()

    // ── Sites ──────────────────────────────────────────────────────────────
    val sitesLive = db.siteDao().getAllLive()
    suspend fun getSites() = db.siteDao().getAll()
    suspend fun upsertSite(site: Site) = db.siteDao().insert(site)
    suspend fun deleteSite(id: String) = db.siteDao().deleteById(id)

    // ── Workers ────────────────────────────────────────────────────────────
    val workersLive = db.workerDao().getAllLive()
    suspend fun getWorkers() = db.workerDao().getAll()
    suspend fun upsertWorker(worker: Worker) = db.workerDao().insert(worker)
    suspend fun deleteWorker(id: String) = db.workerDao().deleteById(id)

    // ── Payments ───────────────────────────────────────────────────────────
    val paymentsLive = db.paymentDao().getAllLive()
    suspend fun getPayments() = db.paymentDao().getAll()
    fun paymentsByWorkerSiteLive(wId: String, sId: String) = db.paymentDao().getByWorkerAndSiteLive(wId, sId)
    suspend fun paymentsByWorkerSiteMonth(wId: String, sId: String, month: String) =
        db.paymentDao().getByWorkerSiteMonth(wId, sId, month)
    suspend fun upsertPayment(p: Payment) = db.paymentDao().insert(p)
    suspend fun deletePayment(id: String) = db.paymentDao().deleteById(id)

    // ── Collections ────────────────────────────────────────────────────────
    val collectionsLive = db.collectionDao().getAllLive()
    suspend fun getCollections() = db.collectionDao().getAll()
    suspend fun upsertCollection(c: Collection) = db.collectionDao().insert(c)
    suspend fun deleteCollection(id: String) = db.collectionDao().deleteById(id)

    // ── Month Closes ───────────────────────────────────────────────────────
    val monthClosesLive = db.monthCloseDao().getAllLive()
    suspend fun getMonthCloses() = db.monthCloseDao().getAll()
    suspend fun insertMonthClose(mc: MonthClose) = db.monthCloseDao().insert(mc)
    suspend fun deleteMonthClose(id: String) = db.monthCloseDao().deleteById(id)

    // ── Stats ──────────────────────────────────────────────────────────────
    suspend fun totalReceivedBySite(siteId: String) = db.collectionDao().totalReceivedBySite(siteId) ?: 0L
    suspend fun totalPaidBySite(siteId: String) = db.paymentDao().totalBySite(siteId) ?: 0L

    // ── Backup ─────────────────────────────────────────────────────────────
    sealed class BackupResult {
        data class Success(val binId: String) : BackupResult()
        data class Error(val message: String) : BackupResult()
    }

    suspend fun backup(): BackupResult = withContext(Dispatchers.IO) {
        val key = apiKey
        if (key.isBlank()) return@withContext BackupResult.Error("API key not set in Settings")

        val data = buildBackupData()
        val payload = BackupPayload(record = data)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        try {
            val existingBinId = binId
            if (existingBinId != null) {
                val resp = RetrofitClient.service.updateBin(existingBinId, key = key, payload = payload)
                if (resp.isSuccessful) BackupResult.Success(existingBinId)
                else BackupResult.Error("Server error: ${resp.code()}")
            } else {
                val resp = RetrofitClient.service.createBin(key = key, payload = payload)
                if (resp.isSuccessful) {
                    val id = resp.body()?.metadata?.id
                    if (id != null) {
                        binId = id
                        BackupResult.Success(id)
                    } else BackupResult.Error("No Bin ID in response")
                } else BackupResult.Error("Server error: ${resp.code()}")
            }
        } catch (e: Exception) {
            BackupResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun restore(id: String): BackupResult = withContext(Dispatchers.IO) {
        val key = apiKey
        if (key.isBlank()) return@withContext BackupResult.Error("API key not set in Settings")
        try {
            val resp = RetrofitClient.service.getLatest(id, key = key)
            if (resp.isSuccessful) {
                val data = resp.body()?.record ?: return@withContext BackupResult.Error("Empty response")
                // Write to DB
                data.sites.forEach { db.siteDao().insert(it) }
                data.workers.forEach { w ->
                    db.workerDao().insert(Worker(w.id, w.name, w.wagePerDay, w.mobile, w.address, null, w.createdAt))
                }
                data.payments.forEach { db.paymentDao().insert(it) }
                data.collections.forEach { db.collectionDao().insert(it) }
                data.monthCloses.forEach { db.monthCloseDao().insert(it) }
                binId = id
                BackupResult.Success(id)
            } else BackupResult.Error("Server error: ${resp.code()}")
        } catch (e: Exception) {
            BackupResult.Error(e.message ?: "Network error")
        }
    }

    private suspend fun buildBackupData(): BackupData {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        return BackupData(
            sites = db.siteDao().getAll(),
            workers = db.workerDao().getAll().map { w ->
                WorkerBackup(w.id, w.name, w.wagePerDay, w.mobile, w.address, w.createdAt)
            },
            payments = db.paymentDao().getAll(),
            collections = db.collectionDao().getAll(),
            monthCloses = db.monthCloseDao().getAll(),
            backupAt = sdf.format(Date())
        )
    }
}
