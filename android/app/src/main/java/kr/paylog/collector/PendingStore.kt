package kr.paylog.collector

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 시트로 아직 못 보낸 행을 담아 두는 폰 안의 대기열.
 * 전송에 성공한 행만 지우므로 꺼져 있거나 오프라인이어도 잃어버리지 않는다.
 */
class PendingStore private constructor(context: Context) :
    SQLiteOpenHelper(context, "queue.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE pending (id TEXT PRIMARY KEY, json TEXT NOT NULL, created INTEGER NOT NULL)")
        // 같은 알림이 다시 올라오는 경우(알림 갱신 등)를 걸러 내기 위한 최근 ID
        db.execSQL("CREATE TABLE recent (id TEXT PRIMARY KEY, at INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** 최근 10분 안에 같은 ID가 없었으면 대기열에 넣고 true */
    @Synchronized
    fun addIfNew(id: String, json: String, now: Long): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("recent", "at < ?", arrayOf((now - DAY_MS).toString()))
            db.rawQuery("SELECT at FROM recent WHERE id = ?", arrayOf(id)).use { c ->
                if (c.moveToFirst() && now - c.getLong(0) < DEDUP_MS) return false
            }
            db.insertWithOnConflict("recent", null, ContentValues().apply {
                put("id", id); put("at", now)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.insertWithOnConflict("pending", null, ContentValues().apply {
                put("id", id); put("json", json); put("created", now)
            }, SQLiteDatabase.CONFLICT_IGNORE)
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun peek(limit: Int): List<Pair<String, String>> =
        readableDatabase.rawQuery("SELECT id, json FROM pending ORDER BY created LIMIT $limit", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
        }

    @Synchronized
    fun remove(ids: Collection<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            ids.forEach { db.delete("pending", "id = ?", arrayOf(it)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun count(): Long =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM pending", null).use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0
        }

    companion object {
        private const val DEDUP_MS = 10 * 60 * 1000L
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        @Volatile private var instance: PendingStore? = null

        fun get(context: Context): PendingStore =
            instance ?: synchronized(this) {
                instance ?: PendingStore(context.applicationContext).also { instance = it }
            }
    }
}
