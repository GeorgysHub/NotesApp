package com.example.notesapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class DatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 5
        private const val DATABASE_NAME = "notes.db"
        private const val TABLE_NOTES = "notes"
        private const val KEY_ID = "id"
        private const val KEY_TITLE = "title"
        private const val KEY_TEXT = "text"
        private const val KEY_DATE = "date"
        private const val TABLE_TAGS = "tags"
        private const val KEY_TAG = "tag_id"
        private const val KEY_TAG_ID = "tag_id"
        private const val KEY_TAG_NAME = "tag_name"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val CREATE_NOTES_TABLE = ("CREATE TABLE " + TABLE_NOTES + "("
                + KEY_ID + " INTEGER PRIMARY KEY," + KEY_TITLE + " TEXT,"
                + KEY_TEXT + " TEXT," + KEY_DATE + " TEXT" + ")")
        db?.execSQL(CREATE_NOTES_TABLE)

        val CREATE_TAGS_TABLE = ("CREATE TABLE " + TABLE_TAGS + "("
                + KEY_TAG_ID + " INTEGER PRIMARY KEY," + KEY_TAG_NAME + " TEXT UNIQUE" + ")")
        db?.execSQL(CREATE_TAGS_TABLE)

        val CREATE_NOTE_TAGS_TABLE = ("CREATE TABLE note_tags ("
                + "note_id INTEGER,"
                + "tag_id INTEGER,"
                + "FOREIGN KEY (note_id) REFERENCES notes(id),"
                + "FOREIGN KEY (tag_id) REFERENCES tags(tag_id)" + ")")
        db?.execSQL(CREATE_NOTE_TAGS_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_NOTES")
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_TAGS")
        db?.execSQL("DROP TABLE IF EXISTS note_tags")
        onCreate(db)
    }


    fun saveNote(title: String, text: String, date: String, tagNames: List<String>) {
        val db = this.writableDatabase

        // Сохранение заметки
        val noteValues = ContentValues().apply {
            put(KEY_TITLE, title)
            put(KEY_TEXT, text)
            put(KEY_DATE, date)
        }
        val noteId = db.insert(TABLE_NOTES, null, noteValues)

        if (noteId == -1L) {
            Log.e("DatabaseHelper", "Failed to insert note")
            return
        }

        // Сохранение тегов и их связей с заметкой
        for (tagName in tagNames) {
            val tagId = getTagIdByName(tagName) ?: run {
                saveTag(tagName)
                getTagIdByName(tagName)
            }

            if (tagId != null) {
                val noteTagValues = ContentValues().apply {
                    put("note_id", noteId)
                    put("tag_id", tagId)
                }
                db.insert("note_tags", null, noteTagValues)
            } else {
                Log.e("DatabaseHelper", "Failed to get or create tag")
            }
        }

        db.close()
    }


    fun saveTag(tag: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_TAG_NAME, tag)
        }
        val result = db.insert(TABLE_TAGS, null, values)
        if (result == -1L) {
            Log.e("DatabaseHelper", "Failed to insert tag")
        } else {
            Log.i("DatabaseHelper", "Tag inserted with ID: $result")
        }
        db.close()
    }

    fun getTagIdByName(tag_name: String): Int? {
        val db = this.readableDatabase
        var tag_id: Int? = null

        val cursor = db.rawQuery("SELECT $KEY_TAG_ID FROM $TABLE_TAGS WHERE $KEY_TAG_NAME = ?", arrayOf(tag_name))

        if (cursor != null && cursor.moveToFirst()) {
            val columnIndex = cursor.getColumnIndex(KEY_TAG_ID)
            if (columnIndex != -1) {
                tag_id = cursor.getInt(columnIndex)
            }
        }

        cursor?.close()

        return tag_id
    }

    fun getAllTags(): List<String> {
        val tags = mutableListOf<String>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_TAGS", null)
        cursor.use {
            val columnIndex = cursor.getColumnIndex(KEY_TAG_NAME)
            while (cursor.moveToNext()) {
                val tag = cursor.getString(columnIndex)
                tags.add(tag)
            }
        }
        db.close()
        return tags
    }
    fun getTagNameById(tagId: Int): String? {
        val db = this.readableDatabase
        var tagName: String? = null

        val cursor = db.rawQuery("SELECT $KEY_TAG_NAME FROM $TABLE_TAGS WHERE $KEY_TAG_ID = ?", arrayOf(tagId.toString()))

        if (cursor != null && cursor.moveToFirst()) {
            val columnIndex = cursor.getColumnIndex(KEY_TAG_NAME)
            if (columnIndex != -1) {
                tagName = cursor.getString(columnIndex)
            }
        }

        cursor?.close()

        return tagName
    }
    fun getTagsForNoteId(noteId: Int): List<String> {
        val db = this.readableDatabase
        val tags = mutableListOf<String>()

        val cursor = db.rawQuery(
            "SELECT $KEY_TAG_NAME FROM $TABLE_TAGS INNER JOIN note_tags ON $TABLE_TAGS.$KEY_TAG_ID = note_tags.tag_id WHERE note_tags.note_id = ?",
            arrayOf(noteId.toString())
        )

        if (cursor != null) {
            while (cursor.moveToNext()) {
                val columnIndex = cursor.getColumnIndex(KEY_TAG_NAME)
                if (columnIndex != -1) {
                    val tagName = cursor.getString(columnIndex)
                    tags.add(tagName)
                }
            }
            cursor.close()
        }

        return tags
    }
    fun deleteNoteById(noteId: Int) {
        val db = this.writableDatabase

        // Удаление связей заметки с тегами
        db.delete("note_tags", "note_id = ?", arrayOf(noteId.toString()))

        // Удаление самой заметки
        db.delete(TABLE_NOTES, "$KEY_ID = ?", arrayOf(noteId.toString()))

        db.close()
    }
    fun deleteTagByName(tagName: String) {
        val db = this.writableDatabase

        // Получаем ID тега
        val tagIdCursor = db.rawQuery("SELECT $KEY_TAG_ID FROM $TABLE_TAGS WHERE $KEY_TAG_NAME = ?", arrayOf(tagName))
        if (tagIdCursor.moveToFirst()) {
            val tagIdIndex = tagIdCursor.getColumnIndex(KEY_TAG_ID)
            if (tagIdIndex != -1) {
                val tagId = tagIdCursor.getInt(tagIdIndex)

                db.delete("note_tags", "tag_id = ?", arrayOf(tagId.toString()))

                db.delete(TABLE_TAGS, "$KEY_TAG_ID = ?", arrayOf(tagId.toString()))
            }
        }
        tagIdCursor.close()
        db.close()
    }
    fun updateNote(noteId: Int, title: String, text: String, date: String, tagNames: List<String>) {
        val db = this.writableDatabase

        val noteValues = ContentValues().apply {
            put(KEY_TITLE, title)
            put(KEY_TEXT, text)
            put(KEY_DATE, date)
        }
        db.update(TABLE_NOTES, noteValues, "$KEY_ID = ?", arrayOf(noteId.toString()))

        db.delete("note_tags", "note_id = ?", arrayOf(noteId.toString()))
        for (tagName in tagNames) {
            val tagId = getTagIdByName(tagName) ?: run {
                saveTag(tagName)
                getTagIdByName(tagName)
            }

            if (tagId != null) {
                val noteTagValues = ContentValues().apply {
                    put("note_id", noteId)
                    put("tag_id", tagId)
                }
                db.insert("note_tags", null, noteTagValues)
            } else {
                Log.e("DatabaseHelper", "Failed to get or create tag")
            }
        }
        db.close()
    }

    fun getNoteById(noteId: Int): Note? {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NOTES WHERE $KEY_ID = ?", arrayOf(noteId.toString()))

        var note: Note? = null
        if (cursor != null && cursor.moveToFirst()) {
            val idIndex = cursor.getColumnIndex(KEY_ID)
            val titleIndex = cursor.getColumnIndex(KEY_TITLE)
            val textIndex = cursor.getColumnIndex(KEY_TEXT)
            val dateIndex = cursor.getColumnIndex(KEY_DATE)

            if (idIndex != -1 && titleIndex != -1 && textIndex != -1 && dateIndex != -1) {
                val id = cursor.getInt(idIndex)
                val title = cursor.getString(titleIndex)
                val text = cursor.getString(textIndex)
                val date = cursor.getString(dateIndex)
                note = Note(id, title, text, date)
            }
        }
        cursor?.close()
        return note
    }


    data class Note(
        val id: Int,
        val title: String,
        val text: String,
        val date: String
    )
    fun updateTagName(oldTagName: String, newTagName: String) {
        val db = this.writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put(KEY_TAG_NAME, newTagName)
            }
            db.update(TABLE_TAGS, values, "$KEY_TAG_NAME = ?", arrayOf(oldTagName))

            val oldTagId = getTagIdByName(oldTagName)
            if (oldTagId != null) {
                val newTagId = getTagIdByName(newTagName)
                if (newTagId != null) {
                    val noteTagValues = ContentValues().apply {
                        put("tag_id", newTagId)
                    }
                    db.update("note_tags", noteTagValues, "tag_id = ?", arrayOf(oldTagId.toString()))
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        db.close()
    }








}
