package com.moriboot.morilist

import java.util.Date

// Pastikan Todo memiliki properti subtasks bertipe List<Subtask>
data class Todo(
    var id: String = "",
    val title: String = "",
    var subtasks: List<Subtask> = emptyList(),
    var priorityLevel: String = "",
    var completed: Boolean = false,
    val timestamp: Date = Date(),
    val userId: String = "",
    val deadline: Long = 0L,
    var reminderTime: Long = 0L
)
