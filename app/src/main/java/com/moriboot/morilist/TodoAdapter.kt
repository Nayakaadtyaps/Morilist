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
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.moriboot.morilist.models.Subtask
import com.moriboot.morilist.models.Todo
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
    private val awardedPoints = mutableMapOf<String, Boolean>() // Map untuk melacak status poin

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvTaskTitle)
        val priority: TextView = view.findViewById(R.id.tvPriority)
        val checkBox: CheckBox = view.findViewById(R.id.taskCheckBox)
        val subtasksRecyclerView: RecyclerView = view.findViewById(R.id.subtasksRecyclerView)
        val timestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val deadline: TextView = view.findViewById(R.id.tvDeadline)
        val ivEdit: ImageView = view.findViewById(R.id.btnEdit)
        val ivDelete: ImageView = view.findViewById(R.id.btnDelete)
        var wasCompleted: Boolean = false // Lacak status completed sebelumnya
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_todo, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("SimpleDateFormat")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val todo = todos[position]
        Log.d("TodoAdapter", "Mengikat todo: ${todo.title}, subtasks: ${todo.subtasks.size}")

        holder.title.text = todo.title
        holder.priority.text = "${todo.priorityLevel}"
        holder.timestamp.text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
            .format(todo.timestamp ?: Date())
        holder.deadline.text = todo.deadline?.let {
            "💣 Deadline: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(it)}"
        } ?: "No deadline"

        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = todo.completed
        holder.wasCompleted = todo.completed // Simpan status awal

        val subtaskAdapter = SubtaskAdapter(
            subtaskList = todo.subtasks.toMutableList(),
            todoId = todo.id,
            onSubtaskUpdate = {
                Log.d("TodoAdapter", "Subtask updated for todo: ${todo.id}")
                canCompleteTodo(todo.id) { allCompleted ->
                    holder.checkBox.isEnabled = allCompleted
                }
            }
        )
        holder.subtasksRecyclerView.layoutManager = LinearLayoutManager(holder.itemView.context)
        holder.subtasksRecyclerView.adapter = subtaskAdapter

        listenForSubtasks(todo.id, subtaskAdapter)

        val isPastDeadline = System.currentTimeMillis() > (todo.deadline ?: 0)
        canCompleteTodo(todo.id) { allCompleted ->
            holder.checkBox.isEnabled = allCompleted
        }

        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (holder.checkBox.isEnabled) {
                todo.completed = isChecked
                updateTodoStatus(todo)
                if (isChecked && !holder.wasCompleted && !awardedPoints.getOrDefault(todo.id, false)) {
                    val pointsToAdd = if (isPastDeadline) 5 else 10
                    addProductivityPoint(todo.userId, pointsToAdd)
                    Toast.makeText(holder.itemView.context, "+$pointsToAdd points!", Toast.LENGTH_SHORT).show()
                    awardedPoints[todo.id] = true // Tandai poin sudah diberikan
                }
                holder.wasCompleted = isChecked // Update status setelah perubahan
            } else {
                holder.checkBox.isChecked = false
            }
        }

        holder.ivEdit.visibility = if (isPastDeadline) View.GONE else View.VISIBLE
        holder.ivEdit.setOnClickListener { onEditClick(todo) }
        holder.ivDelete.setOnClickListener {
            awardedPoints.remove(todo.id) // Hapus dari cache saat task dihapus
            onDeleteClick(todo)
        }
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
        }
    }

    private fun addProductivityPoint(userId: String, points: Int) {
        Log.d("Productivity", "Menambahkan poin untuk userId: $userId")
        val userRef = db.collection("users").document(userId)
        userRef.get().addOnSuccessListener { document ->
            val currentPoints = document.getLong("points") ?: 0
            val newPoints = currentPoints + points
            userRef.update("points", newPoints)
                .addOnSuccessListener {
                    Log.d("Productivity", "Poin berhasil ditambahkan! Total: $newPoints")
                }
                .addOnFailureListener { e ->
                    Log.e("Productivity", "Gagal menambah poin: ${e.message}")
                }
        }.addOnFailureListener { e ->
            Log.e("Productivity", "Gagal mengambil dokumen pengguna: ${e.message}")
        }
    }

    private fun listenForSubtasks(todoId: String, subtaskAdapter: SubtaskAdapter) {
        Log.d("TodoAdapter", "Memulai listener untuk subtasks todoId: $todoId")
        db.collection("todos").document(todoId).collection("subtasks")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("TodoAdapter", "Listen subtasks gagal: ${e.message}")
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    val updatedSubtasks = snapshots.map { it.toObject(Subtask::class.java) }.toMutableList()
                    Log.d("TodoAdapter", "Subtasks diperbarui: ${updatedSubtasks.size}")
                    subtaskAdapter.updateList(updatedSubtasks)
                } else {
                    Log.d("TodoAdapter", "Tidak ada subtasks untuk todoId: $todoId")
                }
            }
    }

    private fun canCompleteTodo(todoId: String, callback: (Boolean) -> Unit) {
        db.collection("todos").document(todoId).collection("subtasks")
            .get()
            .addOnSuccessListener { snapshot ->
                val allCompleted = snapshot.documents.all { it.toObject(Subtask::class.java)?.completed == true }
                callback(allCompleted)
            }
            .addOnFailureListener { e ->
                Log.e("TodoAdapter", "Gagal memeriksa subtask completion: ${e.message}")
                callback(false)
            }
    }

    fun updateList(newList: List<Todo>) {
        // Perbarui awardedPoints berdasarkan status completed
        newList.forEach { todo ->
            if (todo.completed) {
                awardedPoints[todo.id] = true // Anggap task yang sudah completed tidak beri poin lagi
            }
        }
        // Bersihkan awardedPoints untuk task yang sudah tidak ada
        val newIds = newList.map { it.id }.toSet()
        awardedPoints.keys.retainAll { it in newIds }
        todos.clear()
        todos.addAll(newList)
        notifyDataSetChanged()
        Log.d("TodoAdapter", "Daftar todos diperbarui, ukuran: ${todos.size}")
    }
}
