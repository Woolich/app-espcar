package com.example.esp32control

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DbHelper(context: Context) : SQLiteOpenHelper(context, "esp32app.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE settings (
                key TEXT PRIMARY KEY,
                value TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE command_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                command TEXT NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun setSetting(key: String, value: String) {
        val cv = ContentValues().apply {
            put("key", key); put("value", value)
        }
        writableDatabase.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getSetting(key: String): String? {
        readableDatabase.rawQuery("SELECT value FROM settings WHERE key=?", arrayOf(key)).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    fun insertLog(command: String) {
        val cv = ContentValues().apply {
            put("ts", System.currentTimeMillis())
            put("command", command)
        }
        writableDatabase.insert("command_log", null, cv)
    }

    fun countLogs(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM command_log", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun lastLogs(limit: Int = 50): Cursor =
        readableDatabase.rawQuery("SELECT ts, command FROM command_log ORDER BY id DESC LIMIT $limit", null)
}
