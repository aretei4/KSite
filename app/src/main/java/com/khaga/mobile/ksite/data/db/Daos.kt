package com.khaga.mobile.ksite.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.khaga.mobile.ksite.data.model.*

@Dao
interface SiteDao {
    @Query("SELECT * FROM sites ORDER BY createdAt DESC")
    fun getAllLive(): LiveData<List<Site>>

    @Query("SELECT * FROM sites ORDER BY createdAt DESC")
    suspend fun getAll(): List<Site>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(site: Site)

    @Update
    suspend fun update(site: Site)

    @Delete
    suspend fun delete(site: Site)

    @Query("DELETE FROM sites WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface WorkerDao {
    @Query("SELECT * FROM workers ORDER BY name ASC")
    fun getAllLive(): LiveData<List<Worker>>

    @Query("SELECT * FROM workers ORDER BY name ASC")
    suspend fun getAll(): List<Worker>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(worker: Worker)

    @Update
    suspend fun update(worker: Worker)

    @Query("DELETE FROM workers WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY date DESC, createdAt DESC")
    fun getAllLive(): LiveData<List<Payment>>

    @Query("SELECT * FROM payments ORDER BY date DESC, createdAt DESC")
    suspend fun getAll(): List<Payment>

    @Query("SELECT * FROM payments WHERE workerId = :wId AND siteId = :sId ORDER BY date DESC")
    fun getByWorkerAndSiteLive(wId: String, sId: String): LiveData<List<Payment>>

    @Query("SELECT * FROM payments WHERE workerId = :wId AND siteId = :sId AND date LIKE :monthPrefix || '%' ORDER BY date DESC")
    suspend fun getByWorkerSiteMonth(wId: String, sId: String, monthPrefix: String): List<Payment>

    @Query("SELECT SUM(amount) FROM payments WHERE siteId = :sId")
    suspend fun totalBySite(sId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: Payment)

    @Update
    suspend fun update(payment: Payment)

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY date DESC, createdAt DESC")
    fun getAllLive(): LiveData<List<SiteCollection>>

    @Query("SELECT * FROM collections ORDER BY date DESC, createdAt DESC")
    suspend fun getAll(): List<SiteCollection>

    @Query("SELECT * FROM collections WHERE siteId = :sId ORDER BY date DESC")
    fun getBySiteLive(sId: String): LiveData<List<SiteCollection>>

    @Query("SELECT SUM(received) FROM collections WHERE siteId = :sId")
    suspend fun totalReceivedBySite(sId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(collection: SiteCollection)

    @Update
    suspend fun update(collection: SiteCollection)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface MonthCloseDao {
    @Query("SELECT * FROM month_closes ORDER BY month DESC, closedAt DESC")
    fun getAllLive(): LiveData<List<MonthClose>>

    @Query("SELECT * FROM month_closes ORDER BY month DESC, closedAt DESC")
    suspend fun getAll(): List<MonthClose>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mc: MonthClose)

    @Query("DELETE FROM month_closes WHERE id = :id")
    suspend fun deleteById(id: String)
}
