package com.moriboot.morilist

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.moriboot.morilist.models.Subtask

class SubtaskAdapter(
    private val subtaskList: MutableList<Subtask>,
    private val todoId: String,
    private val onSubtaskUpdate: () -> Unit,
    private var isEditMode: Boolean = false,
    private val isDialogAddMode: Boolean = false
) : RecyclerView.Adapter<SubtaskAdapter.SubtaskViewHolder>() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubtaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_subtask, parent, false)
        return SubtaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubtaskViewHolder, position: Int) {
        val subtask = subtaskList[position]

        // Hapus listener lama sebelum update UI
        holder.checkbox.setOnCheckedChangeListener(null)

        // Update UI
        holder.textView.text = subtask.text
        holder.checkbox.isChecked = subtask.completed

        // Atur visibilitas checkbox dan textView
        if (isDialogAddMode) {
            holder.checkbox.visibility = View.GONE
            holder.textView.visibility = View.VISIBLE
        } else {
            holder.checkbox.visibility = View.VISIBLE
            holder.textView.visibility = View.VISIBLE
        }

        // Tampilkan tombol hapus di dialog add atau edit mode
        holder.deleteButton.visibility = if (isDialogAddMode || isEditMode) View.VISIBLE else View.GONE

        // Tambahkan listener untuk checkbox (hanya aktif di luar dialog add)
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (todoId.isNotEmpty() && subtask.id.isNotEmpty() && !isDialogAddMode) {
                Log.d("SubtaskAdapter", "Checkbox diklik: ${subtask.text} - Status Baru: $isChecked")
                val subtaskRef = db.collection("todos").document(todoId)
                    .collection("subtasks").document(subtask.id)

                subtaskRef.update("completed", isChecked)
                    .addOnSuccessListener {
                        Log.d("Firestore", "Subtask ${subtask.id} berhasil diperbarui!")
                        subtaskList[position] = subtask.copy(completed = isChecked)
                        notifyItemChanged(position)
                        onSubtaskUpdate()
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "Gagal memperbarui subtask: ${e.message}")
                    }
            }
        }

        // Tambahkan listener untuk tombol hapus
        holder.deleteButton.setOnClickListener {
            if (isDialogAddMode) {
                // Hapus dari subtaskList lokal di dialog add
                Log.d("SubtaskAdapter", "Menghapus subtask lokal: ${subtask.text}")
                subtaskList.removeAt(position)
                notifyItemRemoved(position)
                notifyItemRangeChanged(position, subtaskList.size)
            } else if (isEditMode && todoId.isNotEmpty() && subtask.id.isNotEmpty()) {
                // Hapus dari Firestore di edit mode
                Log.d("SubtaskAdapter", "Menghapus subtask Firestore: ${subtask.text}")
                val subtaskRef = db.collection("todos").document(todoId)
                    .collection("subtasks").document(subtask.id)

                subtaskRef.delete()
                    .addOnSuccessListener {
                        Log.d("Firestore", "Subtask ${subtask.id} berhasil dihapus!")
                        subtaskList.removeAt(position)
                        notifyItemRemoved(position)
                        notifyItemRangeChanged(position, subtaskList.size)
                        onSubtaskUpdate()
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "Gagal menghapus subtask: ${e.message}")
                    }
            }
        }
    }

    fun updateList(newSubtasks: List<Subtask>) {
        subtaskList.clear()
        subtaskList.addAll(newSubtasks)
        notifyDataSetChanged()
    }

    fun setEditMode(enabled: Boolean) {
        isEditMode = enabled
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = subtaskList.size

    class SubtaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkbox: CheckBox = itemView.findViewById(R.id.subtaskCheckBox)
        val textView: TextView = itemView.findViewById(R.id.subtaskText)
        val deleteButton: ImageButton = itemView.findViewById(R.id.deleteSubtaskButton)
    }
}