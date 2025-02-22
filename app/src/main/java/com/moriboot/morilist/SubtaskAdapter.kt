package com.moriboot.morilist

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SubtaskAdapter(
    private val subtasks: MutableList<Subtask>,
    private val taskDeadline: Long?, // 🔥 Ambil deadline dari task utama
    private val onUpdateSubtask: (Subtask) -> Unit,
    private val onDeleteSubtask: (Subtask) -> Unit,
    private val isEditing: Boolean
) : RecyclerView.Adapter<SubtaskAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val subtaskText: TextView = view.findViewById(R.id.subtaskText)
        val subtaskCheckbox: CheckBox = view.findViewById(R.id.subtaskCheckBox)
        val deleteButton: ImageButton = view.findViewById(R.id.deleteSubtaskButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_subtask, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val subtask = subtasks[position]

        Log.d("SubtaskAdapter", "onBindViewHolder: ${subtask.text} - Completed: ${subtask.completed}")

        holder.subtaskText.text = subtask.text
        holder.subtaskCheckbox.isChecked = subtask.completed

        val isPastDeadline = taskDeadline?.let { System.currentTimeMillis() > it } ?: false
        holder.subtaskCheckbox.isEnabled = !isPastDeadline

        holder.subtaskCheckbox.setOnClickListener {
            val newState = !holder.subtaskCheckbox.isChecked
            Log.d("SubtaskAdapter", "Checkbox clicked: ${subtask.text} - New State: $newState")

            if (!isPastDeadline) {
                holder.subtaskCheckbox.isChecked = newState
                val updatedSubtask = subtask.copy(completed = newState)
                onUpdateSubtask(updatedSubtask)
            } else {
                holder.subtaskCheckbox.isChecked = false
            }
        }
    }

    override fun getItemCount() = subtasks.size
}