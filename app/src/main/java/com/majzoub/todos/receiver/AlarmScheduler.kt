package com.majzoub.todos.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.majzoub.todos.model.Todo
import com.majzoub.todos.model.toTimestamp

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(todo: Todo) {
        if (todo.isDone) return

        val mainTime = todo.time.toTimestamp()

        if (mainTime > System.currentTimeMillis()) {
            scheduleExact(mainTime, todo.id, todo.title, isReminder = false)

            val reminderTime = mainTime - 5 * 60 * 1000
            if (reminderTime > System.currentTimeMillis()) {
                scheduleExact(reminderTime, todo.id + "_reminder", todo.title, isReminder = true)
            }
        }
        
        todo.subTodos.forEach { subTodo ->
            val subTime = subTodo.time.toTimestamp()
            if (subTime > System.currentTimeMillis() && !subTodo.isDone) {
                val combinedTitle = "${todo.title} > ${subTodo.title}"
                scheduleExact(subTime, subTodo.id, combinedTitle, isReminder = false)
                
                val subReminderTime = subTime - 5 * 60 * 1000
                if (subReminderTime > System.currentTimeMillis()) {
                    scheduleExact(subReminderTime, subTodo.id + "_reminder", combinedTitle, isReminder = true)
                }
            }
        }
    }

    private fun scheduleExact(time: Long, id: String, title: String, isReminder: Boolean) {
        val intent = Intent(context, TodoAlarmReceiver::class.java).apply {
            putExtra("EXTRA_TITLE", title)
            putExtra("EXTRA_ID", id)
            putExtra("IS_REMINDER", isReminder)
        }

        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            flag
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(time, pendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pendingIntent)
        }
    }

    fun cancel(todoId: String) {
        cancelInternal(todoId)
        cancelInternal(todoId + "_reminder")
    }

    private fun cancelInternal(id: String) {
        val intent = Intent(context, TodoAlarmReceiver::class.java)
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            flag
        )
        alarmManager.cancel(pendingIntent)
    }
}