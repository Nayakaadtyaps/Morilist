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
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
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
import com.google.firebase.firestore.ListenerRegistration
import com.moriboot.morilist.LoginActivity
import com.moriboot.morilist.R
import com.moriboot.morilist.ReminderReceiver
import com.moriboot.morilist.ShopActivity
import com.moriboot.morilist.SubtaskAdapter
import com.moriboot.morilist.TodoAdapter
import com.moriboot.morilist.models.Subtask
import com.moriboot.morilist.models.Todo
import java.util.Calendar
import java.util.UUID

class TodoFragment : Fragment() {

    private lateinit var todoRecyclerView: RecyclerView
    private lateinit var addTodoButton: FloatingActionButton
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var adapter: TodoAdapter
    private val todos = mutableListOf<Todo>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var listenerRegistration: ListenerRegistration? = null
    private val originalTodos = mutableListOf<Todo>() // semua data asli
    private val displayedTodos = mutableListOf<Todo>() // yang ditampilkan (filtered)
    private var showCompleted = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_todo, container, false)

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener { fetchTodos(getCurrentUserId()) }
        swipeRefreshLayout.setColorSchemeResources(R.color.blue)

        todoRecyclerView = view.findViewById(R.id.todoRecyclerView)
        addTodoButton = view.findViewById(R.id.addTodoFab)

        val toggleCompletedButton = view.findViewById<Button>(R.id.toggleCompletedButton)
        toggleCompletedButton.setOnClickListener {
            showCompleted = !showCompleted
            updateTodoList()
            toggleCompletedButton.text = if (showCompleted) "Sembunyikan yang Selesai" else "Tampilkan yang Selesai"
        }

        setupRecyclerView()
        setupAddButton()

        val userId = getCurrentUserId()
        if (userId != null && isUserLoggedIn()) {
            Log.d("TodoFragment", "User ID ditemukan: $userId")
            fetchTodos(userId)
        } else {
            Log.e("TodoFragment", "User ID null atau sesi tidak valid, arahkan ke login")
            showToast("Silakan login terlebih dahulu!")
            navigateToLogin()
            return view
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        return view
    }

    private fun updateTodoList() {
        displayedTodos.clear()

        val filteredList = if (showCompleted) {
            originalTodos.filter { it.completed }
        } else {
            originalTodos.filter { !it.completed }
        }

        displayedTodos.addAll(filteredList)
        adapter.updateList(displayedTodos)
    }



    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d("Permission", "Izin notifikasi diberikan")
        } else {
            Log.e("Permission", "Izin notifikasi ditolak!")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_todo, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        searchView.queryHint = "Cari tugas..."
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { searchTodo(it) }
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let { searchTodo(it) }
                return false
            }
        })
        super.onCreateOptionsMenu(menu, inflater)
    }

    private fun searchTodo(query: String) {
        val filteredList = todos.filter { it.title.contains(query, ignoreCase = true) }
        adapter.updateList(filteredList)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sort -> {
                showSortDialog()
                true
            }
            R.id.shop -> {
                startActivity(Intent(requireContext(), ShopActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSortDialog() {
        val sortOptions = arrayOf("Ascending", "Descending", "Create Date", "Deadline", "Priority")
        AlertDialog.Builder(requireContext())
            .setTitle("Sort by")
            .setItems(sortOptions) { _, which ->
                when (which) {
                    0 -> todos.sortBy { it.title }
                    1 -> todos.sortByDescending { it.title }
                    2 -> todos.sortBy { it.timestamp }
                    3 -> todos.sortBy { it.deadline }
                    4 -> todos.sortWith(compareBy { listOf("hole in one", "eagle", "albatross").indexOf(it.priorityLevel) })
                }
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun isUserLoggedIn(): Boolean {
        val sharedPref = requireActivity().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPref.getBoolean("IS_LOGGED_IN", false)
        val userId = sharedPref.getString("USER_ID", null)
        return isLoggedIn && userId != null && auth.currentUser != null
    }

    private fun getCurrentUserId(): String? {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e("TodoFragment", "Pengguna belum login atau ada masalah dengan Firebase Auth")
            return getSavedUserId()
        } else {
            Log.d("TodoFragment", "Pengguna saat ini: ${currentUser.uid}")
            return currentUser.uid
        }
    }

    private fun getSavedUserId(): String? {
        val sharedPref = requireActivity().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        return sharedPref.getString("USER_ID", null)
    }

    private fun navigateToLogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java)
        startActivity(intent)
        requireActivity().finish() // Tutup aktivitas saat ini agar pengguna harus login
    }

    private fun setupRecyclerView() {
        adapter = TodoAdapter(
            todos,
            onSubtaskClick = { todo -> showSubtasksDialog(requireContext(), todo) },
            onDeleteSubtask = { subtaskText -> showToast("Subtask deleted: $subtaskText") },
            onEditSubtask = { subtaskText -> showToast("Subtask edited: $subtaskText") },
            onEditClick = { todo -> showEditTodoDialog(requireContext(), todo) },
            onDeleteClick = { todo -> deleteTodoFromDatabase(todo.id) }
        )
        todoRecyclerView.layoutManager = LinearLayoutManager(context)
        todoRecyclerView.adapter = adapter
        Log.d("TodoFragment", "RecyclerView diatur dengan adapter")
    }

    @SuppressLint("UseRequireInsteadOfGet")
    private fun setupAddButton() {
        addTodoButton.setOnClickListener { showAddTodoDialog(requireContext()) }
    }

    private fun fetchTodos(userId: String?) {
        if (userId == null) {
            Log.e("TodoFragment", "User ID null, tidak bisa mengambil todos")
            swipeRefreshLayout.isRefreshing = false
            return
        }

        listenerRegistration = db.collection("todos")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, e ->
                if (!isAdded) {
                    Log.w("TodoFragment", "Fragment tidak terhubung, skip listener.")
                    return@addSnapshotListener
                }

                if (e != null) {
                    Log.e("TodoFragment", "Gagal mendengarkan todos: ${e.message}")
                    showToast("Gagal memuat todos: ${e.message}")
                    swipeRefreshLayout.isRefreshing = false
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val tempTodos = mutableListOf<Todo>()
                    var countLoaded = 0

                    if (snapshot.isEmpty) {
                        todos.clear()
                        originalTodos.clear()
                        displayedTodos.clear()
                        adapter.updateList(displayedTodos)
                        swipeRefreshLayout.isRefreshing = false
                        return@addSnapshotListener
                    }

                    for (document in snapshot) {
                        val todo = document.toObject(Todo::class.java).copy(id = document.id)
                        db.collection("todos").document(todo.id).collection("subtasks")
                            .get()
                            .addOnSuccessListener { subtaskSnapshot ->
                                val subtasks = subtaskSnapshot.map { subtaskDoc ->
                                    subtaskDoc.toObject(Subtask::class.java).copy(id = subtaskDoc.id)
                                }.toMutableList()
                                todo.subtasks = subtasks
                                tempTodos.add(todo)
                                countLoaded++

                                if (countLoaded == snapshot.size()) {
                                    todos.clear()
                                    todos.addAll(tempTodos.sortedByDescending { it.timestamp })

                                    // Ini bagian penting: update originalTodos dan update tampilan
                                    originalTodos.clear()
                                    originalTodos.addAll(todos)
                                    updateTodoList()

                                    swipeRefreshLayout.isRefreshing = false
                                }
                            }
                            .addOnFailureListener { ex ->
                                Log.e("TodoFragment", "Gagal mengambil subtasks: ${ex.message}")
                                countLoaded++
                                if (countLoaded == snapshot.size()) {
                                    todos.clear()
                                    todos.addAll(tempTodos.sortedByDescending { it.timestamp })

                                    originalTodos.clear()
                                    originalTodos.addAll(todos)
                                    updateTodoList()

                                    swipeRefreshLayout.isRefreshing = false
                                }
                            }
                    }
                }
            }
    }



    private fun deleteTodoFromDatabase(todoId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Konfirmasi Penghapusan")
            .setMessage("Yakin ingin menghapus todo ini? Aksi ini tidak bisa dibatalkan.")
            .setPositiveButton("Hapus") { _, _ ->
                db.collection("todos").document(todoId).delete()
                    .addOnSuccessListener {
                        showToast("Todo berhasil dihapus!")
                        fetchTodos(auth.currentUser?.uid)
                    }
                    .addOnFailureListener { e ->
                        Log.e("TodoFragment", "Gagal menghapus todo: ${e.message}")
                        showToast("Gagal menghapus todo: ${e.message}")
                    }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showToast(message: String) {
        if (isAdded && context != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        } else {
            Log.w("TodoFragment", "Tidak bisa menampilkan Toast: Context tidak tersedia")
        }
    }

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

        var selectedDeadline: Long = 0L
        val subtaskList = mutableListOf<Subtask>()

        val subtaskAdapter = SubtaskAdapter(
            subtaskList = subtaskList,
            todoId = "",
            onSubtaskUpdate = {},
            isEditMode = false,
            isDialogAddMode = true // Aktifkan mode dialog add
        )
        listSubtask.layoutManager = LinearLayoutManager(context)
        listSubtask.adapter = subtaskAdapter

        val priorities = context.resources.getStringArray(R.array.priority_levels)
        val spinnerAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, priorities)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        prioritySpinner.adapter = spinnerAdapter

        val reminderOptions = listOf("5 menit", "10 menit", "30 menit", "1 jam", "5 jam")
        val reminderAdapter =
            ArrayAdapter(context, android.R.layout.simple_spinner_item, reminderOptions)
        reminderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        reminderSpinner.adapter = reminderAdapter

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

        addSubtaskButton.setOnClickListener {
            val subtaskText = subtasksInput.text.toString().trim()
            if (subtaskText.isNotEmpty()) {
                val newSubtask = Subtask(
                    id = UUID.randomUUID().toString(),
                    text = subtaskText,
                    completed = false
                )
                subtaskList.add(newSubtask)
                subtaskAdapter.notifyItemInserted(subtaskList.size - 1)
                subtasksInput.text.clear()
            } else {
                showToast("Subtask tidak boleh kosong!")
            }
        }

        addButton.setOnClickListener {
            val title = todoTitleInput.text.toString().trim()
            val priority = prioritySpinner.selectedItem.toString()
            val reminderOption = reminderSpinner.selectedItem.toString()

            val reminderTimeInMillis = when (reminderOption) {
                "5 menit" -> 5 * 60 * 1000L
                "10 menit" -> 10 * 60 * 1000L
                "30 menit" -> 30 * 60 * 1000L
                "1 jam" -> 60 * 60 * 1000L
                "5 jam" -> 5 * 60 * 1000L
                else -> 0L
            }

            if (title.isNotEmpty() && subtaskList.isNotEmpty() && selectedDeadline != 0L) {
                AlertDialog.Builder(context)
                    .setTitle("Konfirmasi")
                    .setMessage("Apakah Anda yakin ingin menyimpan tugas ini?")
                    .setPositiveButton("Ya") { _, _ ->
                        val currentUser = auth.currentUser
                        if (currentUser == null) {
                            showToast("Anda harus login terlebih dahulu!")
                            bottomSheetDialog.dismiss()
                            navigateToLogin()
                            return@setPositiveButton
                        }

                        val userId = currentUser.uid
                        val todoRef = db.collection("todos").document()
                        val newTodo = Todo(
                            id = todoRef.id,
                            title = title,
                            subtasks = mutableListOf(),
                            priorityLevel = priority,
                            userId = userId,
                            deadline = selectedDeadline,
                            reminderTime = reminderTimeInMillis
                        )

                        todoRef.set(newTodo.copy(subtasks = mutableListOf()))
                            .addOnSuccessListener {
                                subtaskList.forEach { subtask ->
                                    val subtaskRef = todoRef.collection("subtasks").document()
                                    subtaskRef.set(subtask.copy(id = subtaskRef.id))
                                        .addOnSuccessListener {
                                            Log.d(
                                                "TodoFragment",
                                                "Subtask ${subtask.text} disimpan"
                                            )
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e(
                                                "TodoFragment",
                                                "Gagal menyimpan subtask: ${e.message}"
                                            )
                                        }
                                }
                                if (selectedDeadline - reminderTimeInMillis > System.currentTimeMillis()) {
                                    setReminderAlarm(
                                        newTodo,
                                        selectedDeadline - reminderTimeInMillis
                                    )
                                }
                                fetchTodos(userId)
                                showToast("Tugas berhasil ditambahkan!")
                                bottomSheetDialog.dismiss()
                            }
                            .addOnFailureListener { e ->
                                Log.e("Firestore", "Gagal menyimpan todo: ${e.message}")
                                showToast("Gagal menyimpan todo: ${e.message}")
                                bottomSheetDialog.dismiss()
                            }
                    }
                    .setNegativeButton("Tidak", null)
                    .show()
            } else {
                showToast("Harap isi semua bidang tuan muda")
            }
        }

        bottomSheetDialog.show()
    }



    private fun showDateTimePicker(onDateTimeSelected: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        val minDeadline = calendar.timeInMillis + (1 * 60 * 60 * 1000)

        DatePickerDialog(requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                TimePickerDialog(requireContext(),
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        val selectedTime = calendar.timeInMillis
                        if (selectedTime < minDeadline) {
                            showToast("Deadline minimal 1 jam dari sekarang!")
                        } else {
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

    @SuppressLint("ScheduleExactAlarm")
    private fun setReminderAlarm(todo: Todo, reminderTimeMillis: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("title", "Deadline Reminder")
            putExtra("message", "Deadline untuk '${todo.title}' sebentar lagi!")
            putExtra("todoId", todo.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            todo.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context?.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + (reminderTimeMillis - System.currentTimeMillis()), pendingIntent)
        showToast("Reminder diset!")
    }

    private fun showSubtasksDialog(context: Context, todo: Todo) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_subtasks, null)
        val subtasksListView = dialogView.findViewById<ListView>(R.id.subtasksRecyclerView2)

        val subtasksAdapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, todo.subtasks.map { it.text })
        subtasksListView.adapter = subtasksAdapter

        AlertDialog.Builder(context)
            .setTitle(todo.title)
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .show()
    }

    @SuppressLint("MissingInflatedId")
    private fun showEditTodoDialog(context: Context, todo: Todo) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_tugas, null)
        val bottomSheetDialog = BottomSheetDialog(context)
        bottomSheetDialog.setContentView(dialogView)

        val todoTitleInput = dialogView.findViewById<EditText>(R.id.etTask)
        val subtasksInput = dialogView.findViewById<EditText>(R.id.etSubtask)
        val listSubtask = dialogView.findViewById<RecyclerView>(R.id.subtasksRecyclerView)
        val addSubtaskButton = dialogView.findViewById<Button>(R.id.btn_add_subtask)
        val prioritySpinner = dialogView.findViewById<Spinner>(R.id.btnPriority)
        val btnDeadline = dialogView.findViewById<Button>(R.id.btn_deadline)
        val saveButton = dialogView.findViewById<Button>(R.id.btn_add)
        val reminderSpinner = dialogView.findViewById<Spinner>(R.id.reminderSpinner)

        todoTitleInput.setText(todo.title)
        var selectedDeadline = todo.deadline
        val subtaskList = todo.subtasks.toMutableList()

        // Aktifkan mode edit pada SubtaskAdapter
        val subtaskAdapter = SubtaskAdapter(
            subtaskList,
            todo.id,
            onSubtaskUpdate = { /* Tidak perlu refresh todo di sini */ },
            isEditMode = true // Aktifkan mode edit agar tombol hapus terlihat
        )
        listSubtask.layoutManager = LinearLayoutManager(context)
        listSubtask.adapter = subtaskAdapter

        val priorities = listOf("hole in one", "eagle", "albatross")
        val spinnerAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, priorities)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        prioritySpinner.adapter = spinnerAdapter
        prioritySpinner.setSelection(priorities.indexOf(todo.priorityLevel))

        val reminderOptions = listOf("5 menit", "10 menit", "30 menit", "1 jam", "5 jam")
        val reminderAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, reminderOptions)
        reminderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        reminderSpinner.adapter = reminderAdapter
        reminderSpinner.setSelection(reminderOptions.indexOf(when (todo.reminderTime) {
            5 * 60 * 1000L -> "5 menit"
            10 * 60 * 1000L -> "10 menit"
            30 * 60 * 1000L -> "30 menit"
            60 * 60 * 1000L -> "1 jam"
            5 * 60 * 60 * 1000L -> "5 jam"
            else -> "5 menit"
        }))

        if (selectedDeadline != 0L) {
            btnDeadline.text = "Deadline: ${DateFormat.format("dd-MM-yyyy HH:mm", Calendar.getInstance().apply { timeInMillis = selectedDeadline })}"
        }

        btnDeadline.setOnClickListener {
            showDateTimePicker { deadline ->
                selectedDeadline = deadline
                btnDeadline.text = "Deadline: ${DateFormat.format("dd-MM-yyyy HH:mm", Calendar.getInstance().apply { timeInMillis = selectedDeadline })}"
            }
        }

        addSubtaskButton.setOnClickListener {
            val subtaskText = subtasksInput.text.toString().trim()
            if (subtaskText.isNotEmpty()) {
                val newSubtask = Subtask(id = UUID.randomUUID().toString(), text = subtaskText, completed = false)
                subtaskList.add(newSubtask)
                subtaskAdapter.notifyItemInserted(subtaskList.size - 1)
                subtasksInput.text.clear()
            }
        }

        saveButton.setOnClickListener {
            val title = todoTitleInput.text.toString().trim()
            val priority = prioritySpinner.selectedItem.toString()
            val reminderOption = reminderSpinner.selectedItem.toString()

            val reminderTimeInMillis = when (reminderOption) {
                "5 menit" -> 5 * 60 * 1000L
                "10 menit" -> 10 * 60 * 1000L
                "30 menit" -> 30 * 60 * 1000L
                "1 jam" -> 60 * 60 * 1000L
                "5 jam" -> 5 * 60 * 60 * 1000L
                else -> 0L
            }

            if (title.isNotEmpty() && subtaskList.isNotEmpty() && selectedDeadline != 0L) {
                // Tampilkan alert konfirmasi
                AlertDialog.Builder(context)
                    .setTitle("Konfirmasi")
                    .setMessage("Yakin ingin menyimpan perubahan tugas ini?")
                    .setPositiveButton("Ya") { _, _ ->
                        // Simpan subtask yang tersisa ke Firestore
                        val todoRef = db.collection("todos").document(todo.id)
                        todoRef.collection("subtasks").get().addOnSuccessListener { snapshot ->
                            snapshot.documents.forEach { it.reference.delete() }
                            subtaskList.forEach { subtask ->
                                val subtaskRef = todoRef.collection("subtasks").document(subtask.id)
                                subtaskRef.set(subtask)
                                    .addOnSuccessListener { Log.d("TodoFragment", "Subtask ${subtask.text} disimpan") }
                                    .addOnFailureListener { e -> Log.e("TodoFragment", "Gagal menyimpan subtask: ${e.message}") }
                            }
                        }

                        val updatedTodo = todo.copy(
                            title = title,
                            subtasks = subtaskList,
                            priorityLevel = priority,
                            deadline = selectedDeadline,
                            reminderTime = reminderTimeInMillis
                        )
                        todoRef.set(updatedTodo)
                            .addOnSuccessListener {
                                fetchTodos(auth.currentUser?.uid)
                                showToast("Tugas berhasil diperbarui!")
                                bottomSheetDialog.dismiss()
                            }
                            .addOnFailureListener { e ->
                                Log.e("Firestore", "Gagal memperbarui todo: ${e.message}")
                                showToast("Gagal memperbarui todo: ${e.message}")
                            }
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            } else {
                showToast("Harap isi semua bidang dan atur deadline!")
            }
        }
        bottomSheetDialog.show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).setSupportActionBar(view.findViewById(R.id.toolbar))
        setHasOptionsMenu(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listenerRegistration?.remove()
        listenerRegistration = null
        Log.d("TodoFragment", "Listener Firestore dihentikan")
    }
}