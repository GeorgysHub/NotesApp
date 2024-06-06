package com.example.notesapp

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.MultiAutoCompleteTextView
import androidx.activity.ComponentActivity
import java.util.Calendar
import java.util.Locale

class NoteEditActivity : ComponentActivity() {
    private lateinit var dbHelper: DatabaseHelper
    private var selectedDate: String = ""
    private var noteId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.add_new_note)
        dbHelper = DatabaseHelper(this)

        noteId = intent.getIntExtra("NOTE_ID", -1)

        val tags = dbHelper.getAllTags()
        val autoCompleteTextView: MultiAutoCompleteTextView = findViewById(R.id.autoCompleteTags)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tags)
        autoCompleteTextView.setAdapter(adapter)
        autoCompleteTextView.setTokenizer(MultiAutoCompleteTextView.CommaTokenizer())

        val buttonDelete = findViewById<Button>(R.id.deleteButton)
        buttonDelete.setOnClickListener {
            // Очистка полей ввода
            findViewById<EditText>(R.id.dateEdit).text.clear()
            findViewById<EditText>(R.id.editMainText).text.clear()
            findViewById<EditText>(R.id.textNoteEdit).text.clear()
            autoCompleteTextView.text.clear()
        }

        val dateButton: EditText = findViewById(R.id.dateEdit)
        dateButton.inputType = InputType.TYPE_NULL
        dateButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val datePickerDialog = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                    dateButton.text = Editable.Factory.getInstance().newEditable(selectedDate)
                },
                year,
                month,
                day
            )

            datePickerDialog.show()
        }

        val buttonSave = findViewById<Button>(R.id.buttonSave)
        buttonSave.setOnClickListener {
            val editTextTitle = findViewById<EditText>(R.id.editMainText)
            val editTextContent = findViewById<EditText>(R.id.textNoteEdit)
            val title = editTextTitle.text.toString()
            val text = editTextContent.text.toString()
            val selectedTags = autoCompleteTextView.text.toString()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            selectedTags.forEach { tag ->
                if (!tags.contains(tag)) {
                    dbHelper.saveTag(tag)
                    adapter.add(tag)
                    adapter.notifyDataSetChanged()
                }
            }

            if (noteId != -1) {
                dbHelper.updateNote(noteId, title, text, selectedDate, selectedTags)
            } else {
                dbHelper.saveNote(title, text, selectedDate, selectedTags)
            }

            dateButton.text.clear()
            editTextTitle.text.clear()
            editTextContent.text.clear()
            autoCompleteTextView.text.clear()
            startActivity(Intent(this, MainActivity::class.java))
        }

        val cancelButton: Button = findViewById<Button>(R.id.buttonCancel)
        cancelButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        // Загрузка данных заметки для редактирования
        if (noteId != -1) {
            loadNoteData(noteId)
        }
    }

    private fun loadNoteData(noteId: Int) {
        val note = dbHelper.getNoteById(noteId)
        note?.let { noteData ->
            findViewById<EditText>(R.id.editMainText).text = Editable.Factory.getInstance().newEditable(noteData.title)
            findViewById<EditText>(R.id.textNoteEdit).text = Editable.Factory.getInstance().newEditable(noteData.text)
            findViewById<EditText>(R.id.dateEdit).text = Editable.Factory.getInstance().newEditable(noteData.date)
            selectedDate = noteData.date
            val tags = dbHelper.getTagsForNoteId(noteId)
            findViewById<MultiAutoCompleteTextView>(R.id.autoCompleteTags).setText(tags.joinToString(", "))
        }
    }
}
