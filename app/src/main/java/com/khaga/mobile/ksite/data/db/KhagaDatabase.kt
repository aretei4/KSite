package com.khaga.mobile.ksite.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.khaga.mobile.ksite.data.model.*

@Database(
    entities = [Site::class, Worker::class, Payment::class, SiteCollection::class, MonthClose::class],
    version = 1,
    exportSchema = false
)
abstract class KhagaDatabase : RoomDatabase() {

    abstract fun siteDao(): SiteDao
    abstract fun workerDao(): WorkerDao
    abstract fun paymentDao(): PaymentDao
    abstract fun collectionDao(): CollectionDao
    abstract fun monthCloseDao(): MonthCloseDao

    companion object {
        @Volatile
        private var INSTANCE: KhagaDatabase? = null

        fun getInstance(context: Context): KhagaDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    KhagaDatabase::class.java,
                    "khaga_site.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
