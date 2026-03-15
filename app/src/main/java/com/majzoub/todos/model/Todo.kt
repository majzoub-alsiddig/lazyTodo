package com.majzoub.todos.model

import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class TodoTime(
    val year: Int = 0,
    val month: Int = 0,
    val day: Int = 0,
    val hour: Int = 0,
    val minute: Int = 0,
    val second: Int = 0
) {
    companion object {
        val UNSCHEDULED = TodoTime(0, 0, 0, 0, 0, 0)
    }
}

fun TodoTime.toTimestamp(): Long {
    if (this == TodoTime.UNSCHEDULED) return 0L
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, day)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, second)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

fun Long.toTodoTime(): TodoTime {
    if (this == 0L) return TodoTime.UNSCHEDULED
    return Calendar.getInstance().apply { timeInMillis = this@toTodoTime }.let {
        TodoTime(
            year = it.get(Calendar.YEAR),
            month = it.get(Calendar.MONTH) + 1,
            day = it.get(Calendar.DAY_OF_MONTH),
            hour = it.get(Calendar.HOUR_OF_DAY),
            minute = it.get(Calendar.MINUTE),
            second = it.get(Calendar.SECOND)
        )
    }
}

@Serializable
data class Todo(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val details: String = "",
    val importance: Importance = Importance.LOW,
    val time: TodoTime = TodoTime.UNSCHEDULED,
    val subTodos: List<Todo> = emptyList(),
    val isDone: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val repeatDays: List<Int> = emptyList()
)

@Serializable
enum class Importance { LOW, MEDIUM, HIGH, URGENT }
@Serializable
enum class RepeatMode { NONE, MINUTELY, HOURLY, DAILY, WEEKLY, MONTHLY, DAYS_OF_WEEK }