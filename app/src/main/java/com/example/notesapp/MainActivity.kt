package com.example.notesapp

import android.app.AlertDialog
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var tagsContainer: LinearLayout
    private lateinit var databaseHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        databaseHelper = DatabaseHelper(this)

        tagsContainer = findViewById(R.id.tagsContainer)
        val buttonNote = findViewById<Button>(R.id.buttonNote)
        val buttonTags = findViewById<Button>(R.id.buttonTags)
        val buttonNew = findViewById<Button>(R.id.buttonNew)

        buttonNote.setOnClickListener {
            tagsContainer.orientation = LinearLayout.VERTICAL
            tagsContainer.removeAllViews()
            displayNotesFromDatabase()
        }

        buttonTags.setOnClickListener {
            tagsContainer.orientation = LinearLayout.VERTICAL
            tagsContainer.removeAllViews()
            displayTagsFromDatabase()
        }

        buttonNew.setOnClickListener {
            val intent = Intent(this, NoteCreationActivity::class.java)
            startActivity(intent)
        }
    }

    private fun displayNotesFromDatabase() {
        val db: SQLiteDatabase = databaseHelper.readableDatabase
        val cursor: Cursor = db.rawQuery("SELECT id, title, text, date FROM notes", null)

        if (cursor.count == 0) {
            // Показать сообщение о том, что заметок нет
        } else {
            tagsContainer.removeAllViews()
            cursor.moveToFirst()

            val idIndex = cursor.getColumnIndex("id")
            val titleIndex = cursor.getColumnIndex("title")
            val textIndex = cursor.getColumnIndex("text")
            val dateIndex = cursor.getColumnIndex("date")

            while (!cursor.isAfterLast) {
                val id = cursor.getInt(idIndex)
                val title = cursor.getString(titleIndex)
                val text = cursor.getString(textIndex)
                val date = cursor.getString(dateIndex)

                val tags = databaseHelper.getTagsForNoteId(id)
                val formattedDate = formatDate(date)

                val noteBlock = FrameLayout(this)

                val noteContent = LinearLayout(this)
                noteContent.orientation = LinearLayout.VERTICAL

                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams.setMargins(0, 0, 0, 8)
                noteContent.layoutParams = layoutParams

                val noteTextView = TextView(this)
                noteTextView.textSize = 18f
                val tagTextView = TextView(this)
                tagTextView.textSize = 18f

                val builder = SpannableStringBuilder()
                builder.append("$formattedDate\n")
                val titleStart = builder.length
                builder.append("$title\n")
                builder.setSpan(StyleSpan(Typeface.BOLD), titleStart, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.append("$text\n\n")

                noteTextView.text = builder
                tagTextView.text = "Тэги: ${tags.joinToString(", ")}"

                noteContent.addView(noteTextView)
                noteContent.addView(tagTextView)
                noteContent.setBackgroundResource(R.drawable.border)

                noteBlock.addView(noteContent)

                // Добавление кнопки удаления
                val deleteButton = Button(this)
                deleteButton.text = "Удалить"
                val deleteButtonParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                deleteButtonParams.gravity = Gravity.END or Gravity.TOP
                deleteButton.layoutParams = deleteButtonParams
                deleteButton.setOnClickListener {
                    databaseHelper.deleteNoteById(id)
                    tagsContainer.removeView(noteBlock)
                    Toast.makeText(this, "Заметка удалена", Toast.LENGTH_SHORT).show()
                }

                noteBlock.addView(deleteButton)

                // Добавление двойного нажатия для редактирования
                noteBlock.setOnClickListener(object : View.OnClickListener {
                    private var lastClickTime: Long = 0

                    override fun onClick(v: View?) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastClickTime < 300) {  // 300 миллисекунд интервал для двойного нажатия
                            val intent = Intent(this@MainActivity, NoteEditActivity::class.java)
                            intent.putExtra("NOTE_ID", id)
                            startActivity(intent)
                        }
                        lastClickTime = currentTime
                    }
                })

                tagsContainer.addView(noteBlock)

                cursor.moveToNext()
            }
        }

        cursor.close()
        db.close()
    }


    private fun formatDate(dateString: String): String {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        return outputFormat.format(date)
    }

    private fun displayTagsFromDatabase() {
        tagsContainer.orientation = LinearLayout.VERTICAL
        val tags = databaseHelper.getAllTags()

        for (tag in tags) {
            val tagLayout = FrameLayout(this)

            val tagButton = Button(this)
            tagButton.text = tag
            tagButton.setOnClickListener {
                displayNotesByTag(tag)
            }

            tagButton.setOnLongClickListener {
                showEditTagDialog(tag)
                true
            }

            val layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(2, 0, 2, 0)
            tagButton.layoutParams = layoutParams

            // Добавление кнопки удаления для тега
            val deleteTagButton = Button(this)
            deleteTagButton.text = "X"
            deleteTagButton.setOnClickListener {
                databaseHelper.deleteTagByName(tag)
                tagsContainer.removeView(tagLayout)
                Toast.makeText(this, "Тег удален", Toast.LENGTH_SHORT).show()
            }
            val deleteTagButtonParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            deleteTagButtonParams.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            deleteTagButton.layoutParams = deleteTagButtonParams

            tagLayout.addView(tagButton)
            tagLayout.addView(deleteTagButton)

            tagsContainer.addView(tagLayout)
        }
    }

    private fun showEditTagDialog(oldTag: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Изменить тег")

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setText(oldTag)
        builder.setView(input)

        builder.setPositiveButton("Сохранить") { dialog, which ->
            val newTag = input.text.toString().trim()
            if (newTag.isNotEmpty()) {
                databaseHelper.updateTagName(oldTag, newTag)
                tagsContainer.removeAllViews() // Очистка контейнера тегов перед повторным отображением
                displayTagsFromDatabase() // Отображение тегов после обновления
                Toast.makeText(this, "Тег обновлен", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Имя тега не может быть пустым", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Отмена") { dialog, which -> dialog.cancel() }

        builder.show()
    }



    private fun displayNotesByTag(tag: String) {
        val db: SQLiteDatabase = databaseHelper.readableDatabase
        val cursor: Cursor = db.rawQuery("""
            SELECT notes.id, notes.title, notes.text, notes.date
            FROM notes
            INNER JOIN note_tags ON notes.id = note_tags.note_id
            INNER JOIN tags ON note_tags.tag_id = tags.tag_id
            WHERE tags.tag_name = ?
        """, arrayOf(tag))

        if (cursor.count == 0) {
            // Показать сообщение о том, что заметок с выбранным тегом нет
            Toast.makeText(this, "Заметок с тегом $tag нет", Toast.LENGTH_SHORT).show()
        } else {
            tagsContainer.removeAllViews()

            // Добавляем заголовок с именем тега
            val tagHeader = TextView(this)
            tagHeader.text = "Тег: $tag"
            tagHeader.setTypeface(null, Typeface.BOLD)
            tagHeader.textSize = 22f
            tagsContainer.addView(tagHeader)

            cursor.moveToFirst()
            val idIndex = cursor.getColumnIndex("id")
            val titleIndex = cursor.getColumnIndex("title")
            val textIndex = cursor.getColumnIndex("text")
            val dateIndex = cursor.getColumnIndex("date")

            while (!cursor.isAfterLast) {
                val id = cursor.getInt(idIndex)
                val title = cursor.getString(titleIndex)
                val text = cursor.getString(textIndex)
                val date = cursor.getString(dateIndex)

                val formattedDate = formatDate(date)

                val noteBlock = FrameLayout(this)

                val noteContent = LinearLayout(this)
                noteContent.orientation = LinearLayout.VERTICAL

                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams.setMargins(0, 0, 0, 8)
                noteContent.layoutParams = layoutParams

                val noteTextView = TextView(this)
                noteTextView.textSize = 18f
                val tagTextView = TextView(this)
                tagTextView.textSize = 18f

                val builder = SpannableStringBuilder()
                builder.append("$formattedDate\n")
                val titleStart = builder.length
                builder.append("$title\n")
                builder.setSpan(StyleSpan(Typeface.BOLD), titleStart, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.append("$text\n\n")

                noteTextView.text = builder

                // Получение тегов для текущей заметки
                val noteTags = databaseHelper.getTagsForNoteId(id)
                tagTextView.text = "Тэги: ${noteTags.joinToString(", ")}"

                noteContent.addView(noteTextView)
                noteContent.addView(tagTextView)
                noteContent.setBackgroundResource(R.drawable.border)

                val deleteButton = Button(this)
                deleteButton.text = "Удалить"
                val deleteButtonParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                deleteButtonParams.gravity = Gravity.END or Gravity.TOP
                deleteButton.layoutParams = deleteButtonParams
                deleteButton.setOnClickListener {
                    databaseHelper.deleteNoteById(id)
                    tagsContainer.removeView(noteBlock)
                    Toast.makeText(this, "Заметка удалена", Toast.LENGTH_SHORT).show()
                }

                noteBlock.addView(noteContent)
                noteBlock.addView(deleteButton)

                tagsContainer.addView(noteBlock)

                cursor.moveToNext()
            }
        }

        cursor.close()
        db.close()
    }
}
