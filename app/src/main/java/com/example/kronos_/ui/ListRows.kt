package com.example.kronos_.ui

import android.app.DatePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.kronos_.R
import com.example.kronos_.data.AttendanceStatus
import com.example.kronos_.data.RequestStatus
import com.example.kronos_.logic.Formats
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate

/** Vista de una fila de lista (layout item_row). */
class RowView(val root: View) {
    private val row: View = root.findViewById(R.id.row)
    private val avatar: TextView = root.findViewById(R.id.avatar)
    val title: TextView = root.findViewById(R.id.title)
    val subtitle: TextView = root.findViewById(R.id.subtitle)
    private val status: TextView = root.findViewById(R.id.status)

    init {
        subtitle.isVisible = false
        status.isVisible = false
    }

    fun setAvatar(name: String) {
        avatar.isVisible = true
        avatar.text = Formats.initials(name)
    }

    fun setSubtitle(text: String) {
        subtitle.isVisible = true
        subtitle.text = text
    }

    fun setStatus(text: String, @ColorRes color: Int = R.color.muted) {
        status.isVisible = true
        status.text = text
        status.setTextColor(ContextCompat.getColor(root.context, color))
    }

    fun onClick(action: () -> Unit) = row.setOnClickListener { action() }
}

/** Llena el contenedor con una fila por elemento; si la lista está vacía muestra [emptyText]. */
fun <T> LinearLayout.showRows(items: List<T>, emptyText: String, bind: (RowView, T) -> Unit) {
    removeAllViews()
    val inflater = LayoutInflater.from(context)
    if (items.isEmpty()) {
        val row = RowView(inflater.inflate(R.layout.item_row, this, false))
        row.title.text = emptyText
        addView(row.root)
        return
    }
    items.forEach { item ->
        val row = RowView(inflater.inflate(R.layout.item_row, this, false))
        bind(row, item)
        addView(row.root)
    }
}

@ColorRes
fun AttendanceStatus.color(): Int = when (this) {
    AttendanceStatus.ON_TIME -> R.color.ok
    AttendanceStatus.LATE -> R.color.warn
    AttendanceStatus.ABSENT -> R.color.bad
}

@ColorRes
fun RequestStatus.color(): Int = when (this) {
    RequestStatus.PENDING -> R.color.warn
    RequestStatus.APPROVED -> R.color.ok
    RequestStatus.REJECTED -> R.color.bad
}

fun Context.chooseOption(title: String, options: List<String>, onChosen: (Int) -> Unit) {
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setItems(options.toTypedArray()) { _, index -> onChosen(index) }
        .show()
}

fun Context.chooseDate(initial: LocalDate, onChosen: (LocalDate) -> Unit) {
    DatePickerDialog(
        this,
        { _, year, month, day -> onChosen(LocalDate.of(year, month + 1, day)) },
        initial.year, initial.monthValue - 1, initial.dayOfMonth,
    ).show()
}

fun Context.confirm(message: String, confirmLabel: String, onConfirmed: () -> Unit) {
    MaterialAlertDialogBuilder(this)
        .setMessage(message)
        .setPositiveButton(confirmLabel) { _, _ -> onConfirmed() }
        .setNegativeButton("Cancelar", null)
        .show()
}
