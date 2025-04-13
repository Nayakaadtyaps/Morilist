package com.moriboot.morilist.models

import java.util.Date

// Pastikan Todo memiliki properti subtasks bertipe List<Subtask>
data class Todo(
    var id: String = "",
    val title: String = "",
    var subtasks: List<Subtask> = mutableListOf(),
    var priorityLevel: String = "",
    var completed: Boolean = false,
    val timestamp: Date = Date(),
    val userId: String = "",
    val deadline: Long = 0L,
    var reminderTime: Long = 0L
)
