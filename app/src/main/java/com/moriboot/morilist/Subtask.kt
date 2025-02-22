package com.moriboot.morilist

data class Subtask(
    var id: String = "",
    val text: String = "",
    var completed: Boolean = false,
    val deadline : Long? = null
)


