package com.moriboot.morilist.models

import java.util.Date

data class Task(
    var title: String = "",
    var description: String = "",
    var deadline: Date? = null,
    var subtasks: List<String> = emptyList(),
    var priorityLevel: String = "",
    var completionStatus: Boolean = false
)
