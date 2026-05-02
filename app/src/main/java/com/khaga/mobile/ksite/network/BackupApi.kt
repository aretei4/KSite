package com.khaga.mobile.ksite.network

import com.khaga.mobile.ksite.data.model.*
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

// ── Backup payload ──────────────────────────────────────────────────────────
data class BackupPayload(
    val record: BackupData
)

data class BackupData(
    val sites: List<Site>,
    val workers: List<WorkerBackup>,   // no photo bytes
    val payments: List<Payment>,
    val collections: List<SiteCollection>,
    val monthCloses: List<MonthClose>,
    val backupAt: String
)

data class WorkerBackup(
    val id: String,
    val name: String,
    val wagePerDay: Long,
    val mobile: String,
    val address: String,
    val createdAt: Long
)

data class BackupResponse(
    val metadata: BinMeta? = null,
    val record: BackupData? = null
)

data class BinMeta(val id: String? = null)

// ── Retrofit service ─────────────────────────────────────────────────────────
interface BackupApiService {
    // Create new bin
    @POST("b")
    suspend fun createBin(
        @Header("Content-Type") ct: String = "application/json",
        @Header("X-Master-Key") key: String,
        @Header("X-Bin-Private") private_: String = "false",
        @Body payload: BackupPayload
    ): Response<BackupResponse>

    // Update existing bin
    @PUT("b/{binId}")
    suspend fun updateBin(
        @Path("binId") binId: String,
        @Header("Content-Type") ct: String = "application/json",
        @Header("X-Master-Key") key: String,
        @Body payload: BackupPayload
    ): Response<BackupResponse>

    // Get latest
    @GET("b/{binId}/latest")
    suspend fun getLatest(
        @Path("binId") binId: String,
        @Header("X-Master-Key") key: String
    ): Response<BackupResponse>
}

object RetrofitClient {
    private const val BASE_URL = "https://api.jsonbin.io/v3/"

    val service: BackupApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BackupApiService::class.java)
    }
}
