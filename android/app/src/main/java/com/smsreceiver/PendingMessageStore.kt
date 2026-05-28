package com.smsreceiver

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class PendingSmsMessage(
    val id: Long,
    val sender: String,
    val body: String,
    val receivedAt: String,
)

class PendingMessageStore(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE pending_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender TEXT NOT NULL,
                body TEXT NOT NULL,
                received_at TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS pending_messages")
        onCreate(db)
    }

    fun enqueue(sender: String, body: String, receivedAt: String) {
        val values = ContentValues().apply {
            put("sender", sender)
            put("body", body)
            put("received_at", receivedAt)
            put("created_at", receivedAt)
        }
        writableDatabase.insert("pending_messages", null, values)
    }

    fun count(): Int {
        val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM pending_messages", null)
        cursor.use {
            if (!cursor.moveToFirst()) {
                return 0
            }
            return cursor.getInt(0)
        }
    }

    fun clearAll() {
        writableDatabase.delete("pending_messages", null, null)
    }

    fun listAll(): List<PendingSmsMessage> {
        val cursor = readableDatabase.query(
            "pending_messages",
            arrayOf("id", "sender", "body", "received_at"),
            null,
            null,
            null,
            null,
            "id ASC",
        )

        cursor.use {
            val items = mutableListOf<PendingSmsMessage>()
            while (cursor.moveToNext()) {
                items.add(
                    PendingSmsMessage(
                        id = cursor.getLong(0),
                        sender = cursor.getString(1),
                        body = cursor.getString(2),
                        receivedAt = cursor.getString(3),
                    ),
                )
            }
            return items
        }
    }

    fun remove(id: Long) {
        writableDatabase.delete("pending_messages", "id = ?", arrayOf(id.toString()))
    }

    companion object {
        private const val DB_NAME = "pending_messages.db"
        private const val DB_VERSION = 1
    }
}
