package com.moriboot.morilist

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class TodoAdapter(
    private val todos: MutableList<Todo>,
    private val onSubtaskClick: (Todo) -> Unit,
    private val onDeleteSubtask: (String) -> Unit,
    private val onEditSubtask: (String) -> Unit,
    private val onEditClick: (Todo) -> Unit,
    private val onDeleteClick: (Todo) -> Unit
) : RecyclerView.Adapter<TodoAdapter.ViewHolder>() {

    private val db = FirebaseFirestore.getInstance()
    private val handler = Handler(Looper.getMainLooper())

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvTaskTitle)
        val priority: TextView = view.findViewById(R.id.tvPriority) // Ganti dari Spinner ke TextView
        val checkBox: CheckBox = view.findViewById(R.id.taskCheckBox)
        val subtasksRecyclerView: RecyclerView = view.findViewById(R.id.subtasksRecyclerView)
        val timestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val deadline: TextView = view.findViewById(R.id.tvDeadline)
        val ivEdit: ImageView = view.findViewById(R.id.btnEdit)
        val ivDelete: ImageView = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_todo, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("SimpleDateFormat")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val todo = todos[position]

        holder.title.text = todo.title
        holder.priority.text = "${todo.priorityLevel}" // Tampilkan priority di TextView
        holder.timestamp.text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
            .format(todo.timestamp ?: Date())
        holder.deadline.text = todo.deadline?.let {
            "💣 Deadline: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(it)}"
        } ?: "No deadline"

        holder.checkBox.isChecked = todo.completed

        val subtaskAdapter = SubtaskAdapter(
            subtasks = todo.subtasks.toMutableList(),
            taskDeadline = todo.deadline, // ✅ Tambahkan deadline dari task utama
            onUpdateSubtask = { updatedSubtask ->
                updateSubtaskInFirebase(todo.id, updatedSubtask) // Fungsi update di Firestore
            },
            onDeleteSubtask = { subtask ->
                deleteSubtaskFromFirebase(todo.id, subtask) // Fungsi hapus dari Firestore
            },
            isEditing = false // Ubah true jika dipanggil di BottomSheet
        )
        holder.subtasksRecyclerView.layoutManager = LinearLayoutManager(holder.itemView.context)
        holder.subtasksRecyclerView.adapter = subtaskAdapter


        val isPastDeadline = System.currentTimeMillis() > (todo.deadline ?: 0)
        holder.checkBox.isEnabled = !isPastDeadline

        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (!isPastDeadline) {
                todo.completed = isChecked
                updateTodoStatus(todo)
            } else {
                holder.checkBox.isChecked = false
            }
        }


        holder.ivEdit.setOnClickListener { onEditClick(todo) }
        holder.ivDelete.setOnClickListener { onDeleteClick(todo) }
    }

    override fun getItemCount() = todos.size

    private fun updateTodoStatus(todo: Todo) {
        if (todo.id.isNotEmpty()) {
            db.collection("todos").document(todo.id)
                .update("completed", todo.completed)
                .addOnSuccessListener {
                    Log.d("Firestore", "Todo ${todo.id} berhasil diperbarui: ${todo.completed}")
                }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "Gagal update todo ${todo.id}: ${e.message}")
                }
        } else {
            Log.e("Firestore", "Gagal update: ID Todo kosong!")
        }
    }

    private fun updateSubtaskInFirebase(todoId: String, subtask: Subtask) {
        val db = FirebaseFirestore.getInstance()

        // 🔥 Pastikan subtask.id tidak kosong
        if (subtask.id.isNotEmpty()) {
            db.collection("todos").document(todoId)
                .collection("subtasks").document(subtask.id) // ✅ Akses dokumen yang benar
                .update("completed", subtask.completed)
                .addOnSuccessListener {
                    Log.d("Firestore", "Subtask ${subtask.id} updated successfully!")
                }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "Error updating subtask: ${e.message}")
                }
        } else {
            Log.e("Firestore", "Subtask ID is missing, cannot update!")
        }
    }

    fun updateList(newList: List<Todo>) {
        todos.clear()
        todos.addAll(newList)
        notifyDataSetChanged()
    }

    private fun deleteSubtaskFromFirebase(todoId: String, subtask: Subtask) {
        val todoRef = FirebaseDatabase.getInstance().getReference("todos").child(todoId)
        todoRef.child("subtasks").get().addOnSuccessListener { snapshot ->
            val subtaskList = snapshot.children.mapNotNull { it.getValue(Subtask::class.java) }.toMutableList()
            subtaskList.removeIf { it.id == subtask.id }
            todoRef.child("subtasks").setValue(subtaskList)
        }
    }

}