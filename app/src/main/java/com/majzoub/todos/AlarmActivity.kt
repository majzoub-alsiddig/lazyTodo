package com.majzoub.todos

import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.majzoub.todos.data.TodoRepository
import com.majzoub.todos.model.RepeatMode
import com.majzoub.todos.model.Todo
import com.majzoub.todos.model.TodoTime
import com.majzoub.todos.model.toTimestamp
import com.majzoub.todos.model.toTodoTime
import com.majzoub.todos.ui.theme.TodosTheme
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null
    private val repository by lazy { TodoRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupLockScreen()
        playAlarmSound()

        val todoTitle = intent.getStringExtra("TODO_TITLE") ?: "Todo Reminder"
        val todoId = intent.getStringExtra("TODO_ID") ?: ""

        enableEdgeToEdge()
        setContent {
            TodosTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.errorContainer) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "ALARM",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = todoTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(48.dp))
                        
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { markAsDone(todoId) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                            ) {
                                Text("Mark as Done")
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { delayFiveMinutes(todoId) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Text("Delay 5m")
                                }
                                
                                Button(
                                    onClick = { showTimePicker(todoId) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Delay Task")
                                }
                            }

                            TextButton(
                                onClick = { dismissAlarmAndRescheduleIfNecessary(todoId) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Exit", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    private fun playAlarmSound() {
        val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
        ringtone?.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        ringtone?.play()
    }

    private fun markAsDone(todoId: String) {
        updateTodo(todoId, forceDismiss = true) { it.copy(isDone = true) }
    }

    private fun delayFiveMinutes(todoId: String) {
        val newTime = System.currentTimeMillis() + 5 * 60 * 1000
        updateTodo(todoId, forceDismiss = true) { it.copy(time = newTime.toTodoTime()) }
    }

    private fun showTimePicker(todoId: String) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(this, { _, hour, minute ->
            val newCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            updateTodo(todoId, forceDismiss = true) { it.copy(time = newCalendar.timeInMillis.toTodoTime()) }
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
    }

    private fun updateTodo(todoId: String, forceDismiss: Boolean = false, action: (Todo) -> Todo) {
        lifecycleScope.launch {
            repository.loadInitialData()
            val todos = repository.getLocalTodos()

            val mainTodo = todos.find { it.id == todoId }
            if (mainTodo != null) {
                var updatedTodo = action(mainTodo)

                if (mainTodo.repeatMode != RepeatMode.NONE && updatedTodo.isDone) {
                    val nextTime = calculateNextOccurrence(mainTodo)
                    updatedTodo = mainTodo.copy(time = nextTime.toTodoTime(), isDone = false)
                }

                repository.saveTodo(updatedTodo)
                if (forceDismiss) dismissAlarm()
                return@launch
            }

            for (parent in todos) {
                val subIndex = parent.subTodos.indexOfFirst { it.id == todoId }
                if (subIndex != -1) {
                    val updatedSubTodos = parent.subTodos.toMutableList()
                    updatedSubTodos[subIndex] = action(parent.subTodos[subIndex])
                    repository.saveTodo(parent.copy(subTodos = updatedSubTodos))
                    if (forceDismiss) dismissAlarm()
                    return@launch
                }
            }
            
            if (forceDismiss) dismissAlarm()
        }
    }

    private fun dismissAlarmAndRescheduleIfNecessary(todoId: String) {
        lifecycleScope.launch {
            repository.loadInitialData()
            val mainTodo = repository.getLocalTodos().find { it.id == todoId }
            
            if (mainTodo?.repeatMode != null && mainTodo.repeatMode != RepeatMode.NONE) {
                val nextTime = calculateNextOccurrence(mainTodo)
                repository.saveTodo(mainTodo.copy(time = nextTime.toTodoTime(), isDone = false))
            }
            dismissAlarm()
        }
    }

    private fun calculateNextOccurrence(todo: Todo): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = todo.time.toTimestamp() }   // <-- convert
        val now = System.currentTimeMillis()

        fun advance() {
            when (todo.repeatMode) {
                RepeatMode.MINUTELY -> calendar.add(Calendar.MINUTE, 1)
                RepeatMode.HOURLY -> calendar.add(Calendar.HOUR_OF_DAY, 1)
                RepeatMode.DAILY -> calendar.add(Calendar.DAY_OF_YEAR, 1)
                RepeatMode.WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
                RepeatMode.MONTHLY -> calendar.add(Calendar.MONTH, 1)
                RepeatMode.DAYS_OF_WEEK -> {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                    if (todo.repeatDays.isNotEmpty()) {
                        while (!todo.repeatDays.contains(calendar.get(Calendar.DAY_OF_WEEK))) {
                            calendar.add(Calendar.DAY_OF_YEAR, 1)
                        }
                    }
                }
                RepeatMode.NONE -> {}
            }
        }

        advance()

        while (calendar.timeInMillis <= now) {
            advance()
        }

        return calendar.timeInMillis
    }

    private fun dismissAlarm() {
        ringtone?.stop()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(intent.getStringExtra("TODO_ID")?.hashCode() ?: 0)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        ringtone?.stop()
    }
}