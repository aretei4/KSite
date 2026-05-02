package com.khaga.mobile.ksite.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

object Fmt {
    private val INR = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    fun money(amount: Long): String {
        return "₹" + "%,d".format(amount)
    }

    fun date(iso: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dt = sdf.parse(iso) ?: return iso
            SimpleDateFormat("d MMM", Locale.getDefault()).format(dt)
        } catch (e: Exception) { iso }
    }

    fun monthLabel(ym: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val dt = sdf.parse(ym) ?: return ym
            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(dt)
        } catch (e: Exception) { ym }
    }

    fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    fun currentMonthIso(): String = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
    fun daysInMonth(ym: String): Int {
        return try {
            val (y, m) = ym.split("-").map { it.toInt() }
            val cal = Calendar.getInstance()
            cal.set(y, m - 1, 1)
            cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        } catch (e: Exception) { 30 }
    }
}

val PAY_HEADS = listOf("Wages", "Advance", "Travel", "Maintenance", "Bonus", "Other")
val PAY_MODES = listOf("Cash", "UPI", "Bank Transfer", "Cheque")
