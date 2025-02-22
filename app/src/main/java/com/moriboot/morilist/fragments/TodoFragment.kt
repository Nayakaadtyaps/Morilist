package com.moriboot.morilist.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moriboot.morilist.R
import com.moriboot.morilist.ReminderReceiver
import com.moriboot.morilist.ShopActivity
import com.moriboot.morilist.Subtask
import com.moriboot.morilist.SubtaskAdapter
import com.moriboot.morilist.Todo
import com.moriboot.morilist.TodoAdapter
import java.util.Calendar
import java.util.Date


class TodoFragment : Fragment() {

    private lateinit var todoRecyclerView: RecyclerView
    private lateinit var addTodoButton: FloatingActionButton
    private lateinit var hpProgressBar: ProgressBar
    private lateinit var adapter: TodoAdapter
    private var selectedDeadline: Long = 0L
    private lateinit var subtaskAdapter: SubtaskAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
//    private val todoCheckedState = mutableMapOf<String, Boolean>()


    private val todos = mutableListOf<Todo>()

    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_todo, container, false)
        swipeRefreshLayout = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)

        swipeRefreshLayout.setOnRefreshListener {
            loadTodos()  // Panggil fungsi buat nge-refresh data
        }
        swipeRefreshLayout.setColorSchemeResources(
            R.color.blue,
            R.color.blue,
            R.color.blue
        )
        loadTodos()
        createNotificationChannel(requireContext())

        todoRecyclerView = view.findViewById(R.id.todoRecyclerView)
        addTodoButton = view.findViewById(R.id.addTodoFab)
//        hpProgressBar = view.findViewById(R.id.healthBar)

        setupRecyclerView()
        setupAddButton()
         // Notifikasi akan muncul dalam 5 detik

        val savedUserId = getSavedUserId()
        if (savedUserId != null) {
            fetchTodos(savedUserId)
        } else {
            val currentUser = FirebaseAuth.getInstance().currentUser
            currentUser?.let {
                saveLoginState(it.uid) // Simpan user ID agar tetap login
                fetchTodos(it.uid)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        return view
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("Permission", "Izin notifikasi diberikan")
            } else {
                Log.e("Permission", "Izin notifikasi ditolak!")
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_todo, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        searchView.queryHint = "Cari tugas..."
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { searchTodo(it) } // Fungsi pencarian
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let { searchTodo(it) } // Update pencarian realtime
                return false
            }
        })

        super.onCreateOptionsMenu(menu,inflater)
    }
    private fun searchTodo(query: String) {
        val filteredList = todos.filter {
            it.title.contains(query, ignoreCase = true)
        }
        adapter.updateList(filteredList)
    }




    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sort -> {
                showSortDialog()
                true
            }
            R.id.shop -> {
                // Pindah ke ShopActivity dari Fragment
                val intent = Intent(requireContext(), ShopActivity::class.java)
                startActivity(intent)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadTodos() {
        val db = FirebaseFirestore.getInstance()

        db.collection("todos").get()
            .addOnSuccessListener { result ->
                val newTodos = mutableListOf<Todo>()
                for (document in result) {
                    val todo = document.toObject(Todo::class.java)
                    todo.id = document.id // ✅ Simpan ID dari Firestore
                    newTodos.add(todo)
                }
                todos.clear()
                todos.addAll(newTodos)
                adapter.notifyDataSetChanged()
                swipeRefreshLayout.isRefreshing = false
            }
            .addOnFailureListener {
                swipeRefreshLayout.isRefreshing = false
            }
    }
    private fun showSortDialog() {
        val sortOptions = arrayOf("Ascending", "Descending", "Create Date", "Deadline", "Priority")
        AlertDialog.Builder(requireContext())
            .setTitle("Sort by")
            .setItems(sortOptions) { _, which ->
                when (which) {
                    0 -> sortTodosByTitle(true)
                    1 -> sortTodosByTitle(false)
                    2 -> sortTodosByCreateDate()
                    3 -> sortTodosByDeadline()
                    4 -> sortTodosByPriority()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun sortTodosByTitle(ascending: Boolean) {
        if (ascending) {
            todos.sortBy { it.title }
        } else {
            todos.sortByDescending { it.title }
        }
        adapter.notifyDataSetChanged()
    }

    private fun sortTodosByCreateDate() {
        // Anda perlu menambahkan field createDate di model Todo
        todos.sortBy { it.timestamp }
        adapter.notifyDataSetChanged()
    }

    private fun sortTodosByDeadline() {
        todos.sortBy { it.deadline }
        adapter.notifyDataSetChanged()
    }

    private fun sortTodosByPriority() {
        val priorityOrder = listOf("hole in one", "eagle", "albatros")
        todos.sortBy { priorityOrder.indexOf(it.priorityLevel) }
        adapter.notifyDataSetChanged()
    }
    private fun saveLoginState(userId: String) {
        val sharedPref = requireActivity().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("USER_ID", userId)
            apply()
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).setSupportActionBar(view.findViewById(R.id.toolbar))
        setHasOptionsMenu(true)
    }

    private fun getSavedUserId(): String? {
        val sharedPref = requireActivity().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        return sharedPref.getString("USER_ID", null)
    }

    private fun clearLoginState() {
        val sharedPref = requireActivity().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            remove("USER_ID")
            apply()
        }
    }



    private fun setupRecyclerView() {
        adapter = TodoAdapter(
            todos,
            onSubtaskClick = { todo ->
                showSubtasksDialog(requireContext(), todo)
            },
            onDeleteSubtask = { subtaskText ->
                Toast.makeText(requireContext(), "Subtask deleted: $subtaskText", Toast.LENGTH_SHORT).show()
            },
            onEditSubtask = { subtaskText ->
                Toast.makeText(requireContext(), "Subtask edited: $subtaskText", Toast.LENGTH_SHORT).show()
            },
            onEditClick = { todo ->  // Tambahkan fungsi edit
                showEditTodoDialog(requireContext(), todo)
            },
            onDeleteClick = { todo ->  // Tambahkan fungsi delete
                deleteTodoFromDatabase(todo.id)
            }
        )

        todoRecyclerView.layoutManager = LinearLayoutManager(context)
        todoRecyclerView.adapter = adapter
    }

    @SuppressLint("UseRequireInsteadOfGet")
    private fun setupAddButton() {
        addTodoButton.setOnClickListener {
            showAddTodoDialog(requireContext())
        }
    }

    private fun fetchTodos(userId: String) {
        db.collection("todos")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { result ->
                todos.clear()
                for (document in result) {
                    val todo = document.toObject(Todo::class.java).copy(id = document.id)
                    todos.add(todo)

                    // 🔥 Ambil Subtasks dari Firestore
                    db.collection("todos").document(todo.id).collection("subtasks")
                        .get()
                        .addOnSuccessListener { subtaskResult ->
                            val subtasks = subtaskResult.map { subtaskDoc ->
                                subtaskDoc.toObject(Subtask::class.java).copy(id = subtaskDoc.id)
                            }
                            todo.subtasks = subtasks.toMutableList() // ✅ Simpan ke todo

                            Log.d("Firestore", "Loaded Subtasks for ${todo.title}: $subtasks")
                            adapter.notifyDataSetChanged() // 🔥 Refresh RecyclerView setelah subtask dimuat
                        }
                }

                if (todos.isEmpty()) {
                    Toast.makeText(requireContext(), "No todos found!", Toast.LENGTH_SHORT).show()
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Log.w("TodoFragment", "Error getting documents: ", exception)
            }
    }

//    private fun updateHpProgress() {
//        val totalSubtasks = todos.sumOf { it.subtasks.size }
//        val completedSubtasks = todos.sumOf { todo -> todo.subtasks.count { it.completed } }
//        val progress = if (totalSubtasks == 0) 0 else (completedSubtasks * 100 / totalSubtasks)
//
//        hpProgressBar.progress = progress
//
//        when {
//            progress < 25 -> hpProgressBar.progressDrawable.setColorFilter(
//                resources.getColor(R.color.red), android.graphics.PorterDuff.Mode.SRC_IN
//            )
//            progress < 75 -> hpProgressBar.progressDrawable.setColorFilter(
//                resources.getColor(R.color.blue), android.graphics.PorterDuff.Mode.SRC_IN
//            )
//            else -> hpProgressBar.progressDrawable.setColorFilter(
//                resources.getColor(R.color.green), android.graphics.PorterDuff.Mode.SRC_IN
//            )
//        }
//    }

    @SuppressLint("MissingInflatedId")
    private fun showAddTodoDialog(context: Context) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_tugas, null)
        val bottomSheetDialog = BottomSheetDialog(context)
        bottomSheetDialog.setContentView(dialogView)

        val todoTitleInput = dialogView.findViewById<EditText>(R.id.etTask)
        val subtasksInput = dialogView.findViewById<EditText>(R.id.etSubtask)
        val listSubtask = dialogView.findViewById<RecyclerView>(R.id.subtasksRecyclerView)
        val addSubtaskButton = dialogView.findViewById<Button>(R.id.btn_add_subtask)
        val prioritySpinner = dialogView.findViewById<Spinner>(R.id.btnPriority)
        val btnDeadline = dialogView.findViewById<Button>(R.id.btn_deadline)
        val reminderSpinner = dialogView.findViewById<Spinner>(R.id.reminderSpinner)
        val addButton = dialogView.findViewById<Button>(R.id.btn_add)

        // Setup reminder spinner
        val reminderOptions = listOf("5 menit", "10 menit", "30 menit", "1 jam", "5 jam")
        val reminderAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, reminderOptions)
        reminderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        reminderSpinner.adapter = reminderAdapter

        // Setup subtask list dan adapter
        val subtaskList = mutableListOf<Subtask>()
        val subtaskAdapter = SubtaskAdapter(
            subtaskList,
            taskDeadline = selectedDeadline, // 🔥 Ambil deadline dari task
            onUpdateSubtask = { subtask ->
                val index = subtaskList.indexOfFirst { it.id == subtask.id }
                if (index != -1) {
                    subtaskList[index] = subtask.copy(completed = subtask.completed)
                    subtaskAdapter.notifyItemChanged(index)
                }
            },
            onDeleteSubtask = { subtask ->
                subtaskList.remove(subtask)
                subtaskAdapter.notifyDataSetChanged()
            },
            isEditing = true
        )
        listSubtask.layoutManager = LinearLayoutManager(context)
        listSubtask.adapter = subtaskAdapter

        val priorities = context.resources.getStringArray(R.array.priority_levels)
        val spinnerAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, priorities)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        prioritySpinner.adapter = spinnerAdapter
       // Simpan ke Firebase

        // Handle deadline button click
        btnDeadline.setOnClickListener {
            showDateTimePicker { deadline ->
                selectedDeadline = deadline
                btnDeadline.text = "Deadline: ${DateFormat.format("dd-MM-yyyy HH:mm", Calendar.getInstance().apply { timeInMillis = selectedDeadline })}"
            }
        }

        // Handle add subtask button click
        addSubtaskButton.setOnClickListener {
            val subtaskText = subtasksInput.text.toString().trim()

            if (subtaskText.isNotEmpty()) {
                val db = FirebaseFirestore.getInstance()
                val todoId = "id_todo_yang_sedang_diedit" // 🔥 Pastikan ini ID todo yang benar

                val subtaskRef = db.collection("todos").document(todoId)
                    .collection("subtasks").document() // ✅ Buat ID otomatis dari Firestore

                val newSubtask = Subtask(
                    id = subtaskRef.id, // ✅ Simpan ID unik yang dibuat Firestore
                    text = subtaskText,
                    completed = false
                )

                subtaskRef.set(newSubtask).addOnSuccessListener {
                    Log.d("Firestore", "Subtask berhasil disimpan dengan ID: ${newSubtask.id}")

                    // 🔥 Tambahkan ke RecyclerView
                    subtaskList.add(newSubtask)
                    subtaskAdapter.notifyItemInserted(subtaskList.size - 1)

                    subtasksInput.text.clear() // Kosongkan input setelah ditambahkan
                }.addOnFailureListener { e ->
                    Log.e("Firestore", "Gagal menyimpan subtask: ${e.message}")
                }
            } else {
                Toast.makeText(context, "Subtask tidak boleh kosong!", Toast.LENGTH_SHORT).show()
            }
        }

        // Handle add button click
        addButton.setOnClickListener {
            val title = todoTitleInput.text.toString().trim()
            val priority = prioritySpinner.selectedItem.toString()
            val reminderOption = reminderSpinner.selectedItem.toString()

            // Convert reminder option to milliseconds
            val reminderTimeInMillis = when (reminderOption) {
                "5 menit" -> 5 * 60 * 1000L
                "10 menit" -> 10 * 60 * 1000L
                "30 menit" -> 30 * 60 * 1000L
                "1 jam" -> 60 * 60 * 1000L
                "5 jam" -> 5 * 60 * 60 * 1000L
                else -> 0L
            }

            if (title.isNotEmpty() && subtaskList.isNotEmpty() && selectedDeadline != 0L) {
                val currentUser = FirebaseAuth.getInstance().currentUser
                currentUser?.let {
                    val newTodo = Todo(
                        title = title,
                        subtasks = subtaskList,
                        priorityLevel = priority,
                        userId = it.uid,
                        deadline = selectedDeadline,
                        reminderTime = reminderTimeInMillis // Simpan reminderTime
                    )

                    saveTodoToDatabase(newTodo)
                    updateTodoStatus(newTodo)

                    // Hitung waktu alarm (reminderTime sebelum deadline)
                    val reminderTriggerTime = selectedDeadline - reminderTimeInMillis

                    // Cegah alarm kalau deadline sudah lewat
                    if (reminderTriggerTime > System.currentTimeMillis()) {
                        setReminderAlarm(newTodo, reminderTriggerTime)
                    } else {
                        Log.d("ReminderDebug", "Reminder tidak diset karena sudah lewat deadline")
                    }

                    bottomSheetDialog.dismiss()
                }
            } else {
                Toast.makeText(context, "Harap isi semua bidang dan atur deadline!", Toast.LENGTH_SHORT).show()
            }
        }

        bottomSheetDialog.show()
    }




    private fun showDateTimePicker(onDateTimeSelected: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        val minDeadline = calendar.timeInMillis + (1 * 60 * 60 * 1000) // 24 jam dari sekarang

        DatePickerDialog(requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                TimePickerDialog(requireContext(),
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)

                        val selectedTime = calendar.timeInMillis
                        if (selectedTime < minDeadline) {
                            Toast.makeText(requireContext(), "Deadline minimal 1 jam dari sekarang!", Toast.LENGTH_SHORT).show()
                        } else {
                            selectedDeadline = selectedTime
                            onDateTimeSelected(selectedTime)
                        }
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()

    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            val alarmManager = requireContext().getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                startActivity(intent)
            }
        }
    }

    @SuppressLint("ScheduleExactAlarm", "UseRequireInsteadOfGet")
    private fun setReminderAlarm(todo: Todo, reminderTimeMillis: Long) {
        val currentTime = System.currentTimeMillis()

        // Cek apakah deadline sudah lewat
        if (todo.deadline <= currentTime) {
            Log.d("AlarmDebug", "Deadline sudah lewat, alarm tidak di-set untuk ${todo.title}")
            return // Jangan set alarm untuk task yang sudah lewat
        }

        // Cek apakah alarm sudah pernah ditampilkan
        val sharedPreferences = context!!.getSharedPreferences("ReminderPrefs", Context.MODE_PRIVATE)
        val alreadyNotified = sharedPreferences.getBoolean(todo.id, false)

        if (alreadyNotified) {
            Log.d("AlarmDebug", "Alarm untuk ${todo.title} sudah pernah muncul, tidak di-set ulang")
            return
        }

        // Hitung triggerTime berdasarkan waktu reminder yang dipilih user
        val delay = todo.deadline - currentTime - reminderTimeMillis
        val triggerTime = SystemClock.elapsedRealtime() + maxOf(0, delay) // Pastikan tidak negatif

        // Buat intent untuk alarm
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("title", "Deadline Reminder")
            putExtra("message", "Deadline untuk '${todo.title}' sebentar lagi!")
            putExtra("todoId", todo.id)
        }

        // Buat PendingIntent dengan ID unik agar bisa dihapus sebelum mengatur alarm baru
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            todo.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context!!.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Hapus alarm lama (jika ada) sebelum membuat alarm baru
        alarmManager.cancel(pendingIntent)

        // Set alarm yang baru
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent)

        // Simpan status bahwa alarm sudah pernah ditampilkan
        sharedPreferences.edit().putBoolean(todo.id, true).apply()

        Log.d("AlarmDebug", "Alarm di-set untuk ${todo.title} pada ${Date(currentTime + delay)}")
        Toast.makeText(context, "Reminder diset!", Toast.LENGTH_SHORT).show()
    }

    private fun saveTodoToDatabase(todo: Todo) {
        val db = FirebaseFirestore.getInstance()
        val todoRef = db.collection("todos").document() // 🔥 Buat ID Todo baru

        val newTodo = todo.copy(id = todoRef.id) // ✅ Simpan ID Todo yang dibuat Firestore

        todoRef.set(newTodo).addOnSuccessListener {
            Log.d("Firestore", "Todo berhasil disimpan dengan ID: ${newTodo.id}")

            // 🔥 Simpan setiap subtask ke dalam koleksi subtasks
            for (subtask in newTodo.subtasks) {
                val subtaskRef = todoRef.collection("subtasks").document()
                val subtaskWithId = subtask.copy(id = subtaskRef.id)

                subtaskRef.set(subtaskWithId).addOnSuccessListener {
                    Log.d("Firestore", "Subtask ${subtaskWithId.text} berhasil disimpan")
                }.addOnFailureListener { e ->
                    Log.e("Firestore", "Gagal menyimpan subtask: ${e.message}")
                }
            }

        }.addOnFailureListener { e ->
            Log.e("Firestore", "Gagal menyimpan todo: ${e.message}")
        }
    }

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

    private fun showSubtasksDialog(context: Context, todo: Todo) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_subtasks, null)
        val subtasksListView = dialogView.findViewById<ListView>(R.id.subtasksRecyclerView2)

        val subtasksAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_list_item_1,
            todo.subtasks.map { it.text })
        subtasksListView.adapter = subtasksAdapter

        AlertDialog.Builder(context)
            .setTitle(todo.title)
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showOptionsDialog(context: Context, todo: Todo) {
        val options = arrayOf("Edit", "Delete")
        AlertDialog.Builder(context)
            .setTitle("Choose an option")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditTodoDialog(context, todo)
                    1 -> deleteTodoFromDatabase(todo.id)
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    @SuppressLint("MissingInflatedId")
    private fun showEditTodoDialog(context: Context, todo: Todo) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_tugas, null)
        val bottomSheetDialog = BottomSheetDialog(context)
        bottomSheetDialog.setContentView(dialogView)

        val todoTitleInput = dialogView.findViewById<EditText>(R.id.etTask)
        val listSubtask = dialogView.findViewById<RecyclerView>(R.id.subtasksRecyclerView)
        val addSubtaskButton = dialogView.findViewById<Button>(R.id.btn_add_subtask)
        val prioritySpinner = dialogView.findViewById<Spinner>(R.id.btnPriority)
        val btnDeadline = dialogView.findViewById<Button>(R.id.btn_deadline)
        val saveButton = dialogView.findViewById<Button>(R.id.btn_add)
        val reminderSpinner =
            dialogView.findViewById<Spinner>(R.id.reminderSpinner) // Inisialisasi reminderSpinner

        // Set nilai awal dari todo
        todoTitleInput.setText(todo.title)
        var selectedDeadline = todo.deadline

        // Ubah list subtask dari String ke Subtask
        val subtaskList = todo.subtasks.toMutableList()

        // Setup RecyclerView dengan parameter onUpdateSubtask & onDeleteSubtask
        val subtaskAdapter = SubtaskAdapter(
            subtaskList,
            taskDeadline = todo.deadline,
            onUpdateSubtask = { updatedSubtask ->
                val index = subtaskList.indexOfFirst { it.id == updatedSubtask.id }
                if (index != -1) {
                    subtaskList[index] = updatedSubtask
                    subtaskAdapter.notifyItemChanged(index)
                }
            },
            onDeleteSubtask = { subtask ->
                val index = subtaskList.indexOf(subtask)
                if (index != -1) {
                    subtaskList.removeAt(index)
                    subtaskAdapter.notifyItemRemoved(index) // Gunakan notifyItemRemoved
                    Log.d("TodoFragment", "Subtask dihapus: ${subtask.text}")
                } else {
                    Log.e("TodoFragment", "Gagal menghapus subtask, tidak ditemukan!")
                }
            },
            isEditing = true
        )

        listSubtask.layoutManager = LinearLayoutManager(context)
        listSubtask.adapter = subtaskAdapter

        // Setup priority spinner
        val priorities = listOf("hole in one", "eagle", "albatross")
        val spinnerAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, priorities)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        prioritySpinner.adapter = spinnerAdapter
        prioritySpinner.setSelection(priorities.indexOf(todo.priorityLevel))

        // Setup reminder spinner
        val reminderOptions = listOf("5 menit", "10 menit", "30 menit", "1 jam", "5 jam")
        val reminderAdapter =
            ArrayAdapter(context, android.R.layout.simple_spinner_item, reminderOptions)
        reminderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        reminderSpinner.adapter = reminderAdapter

        // Set reminder spinner ke nilai yang sudah ada (jika ada)
        val reminderIndex = reminderOptions.indexOf(
            when (todo.reminderTime) {
                5 * 60 * 1000L -> "5 menit"
                10 * 60 * 1000L -> "10 menit"
                30 * 60 * 1000L -> "30 menit"
                60 * 60 * 1000L -> "1 jam"
                5 * 60 * 60 * 1000L -> "5 jam"
                else -> "5 menit" // Default
            }
        )
        reminderSpinner.setSelection(reminderIndex)

        // Set deadline jika sudah ada sebelumnya
        if (selectedDeadline != 0L) {
            btnDeadline.text = "Deadline: ${
                DateFormat.format(
                    "dd-MM-yyyy HH:mm",
                    Calendar.getInstance().apply { timeInMillis = selectedDeadline })
            }"
        }

        // Handle tombol deadline
        btnDeadline.setOnClickListener {
            showDateTimePicker { deadline ->
                selectedDeadline = deadline
                btnDeadline.text = "Deadline: ${
                    DateFormat.format(
                        "dd-MM-yyyy HH:mm",
                        Calendar.getInstance().apply { timeInMillis = selectedDeadline })
                }"
            }
        }

        // Handle add button click
        addSubtaskButton.setOnClickListener {
            val title = todoTitleInput.text.toString().trim()
            val priority = prioritySpinner.selectedItem.toString()
            val reminderOption = reminderSpinner.selectedItem.toString()

            // Convert reminder option to milliseconds
            val reminderTimeInMillis = when (reminderOption) {
                "5 menit" -> 5 * 60 * 1000L
                "10 menit" -> 10 * 60 * 1000L
                "30 menit" -> 30 * 60 * 1000L
                "1 jam" -> 60 * 60 * 1000L
                "5 jam" -> 5 * 60 * 60 * 1000L
                else -> 0L
            }

            if (title.isNotEmpty() && subtaskList.isNotEmpty() && selectedDeadline != 0L) {
                val currentUser = FirebaseAuth.getInstance().currentUser
                currentUser?.let {
                    val newTodo = Todo(
                        title = title,
                        subtasks = subtaskList,
                        priorityLevel = priority,
                        userId = it.uid,
                        deadline = selectedDeadline,
                        reminderTime = reminderTimeInMillis // Simpan reminderTime
                    )

                    saveTodoToDatabase(newTodo)
                    updateTodoStatus(newTodo)

                    // Hitung waktu alarm (reminderTime sebelum deadline)
                    val reminderTriggerTime = selectedDeadline - reminderTimeInMillis

                    // Cegah alarm kalau deadline sudah lewat
                    if (reminderTriggerTime > System.currentTimeMillis()) {
                        setReminderAlarm(newTodo, reminderTriggerTime)
                    } else {
                        Log.d("ReminderDebug", "Reminder tidak diset karena sudah lewat deadline")
                    }


                    bottomSheetDialog.dismiss()
                }
            } else {
                Toast.makeText(
                    context,
                    "Harap isi semua bidang dan atur deadline!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        bottomSheetDialog.show()
    }


    private fun updateTodoInDatabase(todo: Todo) {
        db.collection("todos")
            .document(todo.id)
            .set(todo)
            .addOnSuccessListener {
                fetchTodos(FirebaseAuth.getInstance().currentUser?.uid ?: "")
            }
            .addOnFailureListener { e ->
                Log.w("TodoFragment", "Error updating document", e)
            }
    }

    private fun deleteTodoFromDatabase(todoId: String) {
        db.collection("todos")
            .document(todoId)
            .delete()
            .addOnSuccessListener {
                fetchTodos(FirebaseAuth.getInstance().currentUser?.uid ?: "")
            }
            .addOnFailureListener { e ->
                Log.w("TodoFragment", "Error deleting document", e)
            }
    }

}