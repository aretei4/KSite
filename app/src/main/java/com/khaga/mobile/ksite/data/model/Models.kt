package com.khaga.mobile.ksite.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sites")
data class Site(
    @PrimaryKey val id: String,
    val name: String,
    val estimate: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "workers")
data class Worker(
    @PrimaryKey val id: String,
    val name: String,
    val wagePerDay: Long,
    val mobile: String = "",
    val address: String = "",
    val photoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey val id: String,
    val workerId: String,
    val siteId: String,
    val head: String,
    val amount: Long,
    val mode: String,
    val date: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

// Named SiteCollection to avoid clash with java.util.Collection
@Entity(tableName = "collections")
data class SiteCollection(
    @PrimaryKey val id: String,
    val siteId: String,
    val from: String,
    val amount: Long,
    val received: Long,
    val mode: String,
    val date: String,
    val status: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "month_closes")
data class MonthClose(
    @PrimaryKey val id: String,
    val workerId: String,
    val siteId: String,
    val month: String,
    val daysPresent: Int,
    val daysHalf: Int,
    val daysAbsent: Int,
    val totalDays: Int,
    val wageEarned: Long,
    val advanceTaken: Long,
    val travelTaken: Long,
    val otherTaken: Long,
    val totalTaken: Long,
    val netPayable: Long,
    val closedAt: Long = System.currentTimeMillis()
)