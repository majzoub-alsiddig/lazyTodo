package com.majzoub.todos.worker

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.majzoub.todos.data.TodoRepository
import com.majzoub.todos.model.*
import com.majzoub.todos.receiver.TodoAlarmReceiver
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

class AiTodoWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val apiKeys = listOf( // APIs comes here
        
    )

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        val prompt = inputData.getString("PROMPT") ?: return Result.failure()
        val repository = TodoRepository(applicationContext)

        showProcessingNotification()

        if (apiKeys.isEmpty()) {
            Log.e("AiTodoWorker", "No API keys provided")
            showErrorNotification("No API keys configured.")
            return Result.failure()
        }

        val calendar = Calendar.getInstance()
        val timezoneId = TimeZone.getDefault().id
        val currentMillis = System.currentTimeMillis()

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).apply { timeZone = TimeZone.getDefault() }
        val formattedNow = sdf.format(Date(currentMillis))

        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonthName = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.ENGLISH) ?: ""

        val fullPrompt = """
            You are a professional Task Extraction & Scheduling Assistant. Your mission is to parse conversational messages (family, friends, or university) and transform them into a structured, actionable JSON todo list.

            SYSTEM GOALS:
            Identify tasks hidden in messy conversational text.
            If a task is large or complex, break it into logical sub-tasks.
            Create a Balanced Plan: never schedule multiple subtasks at the exact same timestamp. Space them out logically (30–60 minutes) or across different days to avoid alert fatigue.
            Prefer concrete future scheduling: when ambiguous, pick the most helpful future time rather than leaving time empty.
            Preserve the exact output schema and formatting (JSON only).

            CONTEXT (THE ABSOLUTE TRUTH):
            - Reference Clock: $formattedNow
            - Current Year: $currentYear
            - Current Month: $currentMonthName
            - Timezone: $timezoneId
            - Reference Unix Millis: $currentMillis

            CRITICAL INSTRUCTION FOR CALCULATIONS:
            1. All relative dates (today, tomorrow, next week) MUST be calculated starting from $formattedNow.
            2. If a user mentions a date like "April 8th", and the current month is March, you MUST use the year $currentYear.
            3. If the user says "end of day", use 23:59:00 of that specific date.
            4. NEVER use the AI's internal training date. ONLY use the Reference Unix Millis ($currentMillis) as your 0 point for all math.

            ========================
            TIME INTERPRETATION RULES (priority order)
            All "time" fields MUST be an object with the following structure:
            {
              "year": integer (e.g., 2026),
              "month": integer (1-12),
              "day": integer,
              "hour": integer (0-23),
              "minute": integer,
              "second": integer (default 0 if not specified)
            }
            If the task has no scheduled time, set all fields to 0 (year=0, month=0, ...).
            Interpret times relative to the Current Date/Time above and in the user's timezone.

            Full date + time (highest confidence)
            Examples: "March 20 at 5pm", "10/3 17:00", "2026-04-27 09:00"
            Convert directly to the structured object in the user's timezone.

            Specific date (day + month) with no time
            Formats: "March 20", "10/3", "10-3" -> Interpret as DAY/MONTH.
            Use Current Year. If that date already passed this year, use NEXT year.
            Default time: 09:00 local time.

            Day-number only ("on the 10th")
            If the day hasn't occurred yet this month -> use current month.
            If it already passed -> use next month.
            Default time: 09:00 local time.

            Relative terms
            "today" -> same day. If no time given, default to Current Time + 30 minutes but never earlier than now.
            "tonight" -> today at 20:00.
            "tomorrow" -> tomorrow at 09:00.
            "this weekend" -> upcoming Saturday at 10:00.
            "next week" -> same weekday 7 days from today at 09:00.

            Day names (e.g., "Monday")
            Use the next occurrence of that weekday. Default time: 09:00 unless a time is provided.
            Anchor rule: if one task in the same message explicitly uses a weekday, and other tasks lack dates, infer that weekday for those tasks unless context contradicts it.

            Time-only (e.g., "at 6pm")
            If the time is later than Current Time today -> schedule today at that time.
            If the time already passed today -> schedule TOMORROW at that time.

            No usable date/time or completely ambiguous
            Set all time fields to 0 (unscheduled).
            Still create the task object with default importance MEDIUM.

            General ambiguity rule:
            When multiple interpretations are possible, choose the earliest reasonable future time that preserves user intent.

            ========================
            BALANCED SCHEDULING RULES
            If a message contains multiple related sub-tasks for the same higher-level task, put them in "subTodos".
            Every todo object and every subTodo MUST include all required fields (title, importance, details, time, subTodos for parent; title, time, isDone for subTodos).
            SubTodos must never be scheduled earlier than their parent task's time. If the parent has time=0, infer a sensible start time for subtasks.
            Avoid identical timestamps:
            If a conflict occurs, push the later subtasks forward by 30–60 minutes on the same day.
            If the day fills up (e.g., past 20:00), spill remaining subtasks to the next day starting at 09:00.
            If a big task lacks times but has many subtasks, create a sensible schedule: start the first at 09:00 (or Current Time + 30min if today), then space following subtasks by 30–60 minutes.

            ========================
            IMPORTANCE RULES
            URGENT: deadlines or events happening today, explicit "ASAP", "immediately", "urgent", "due now".
            HIGH: upcoming deadlines, exams, important work due soon but not immediate.
            MEDIUM: regular tasks (default).
            LOW: optional or casual tasks.

            Deterministic keyword mapping:
            "ASAP", "immediately", "urgent", "due now" -> URGENT.
            "deadline", "due", "due by [date]" -> HIGH or URGENT depending on date proximity.
            "tomorrow" with explicit time-sensitive phrasing -> HIGH.

            ========================
            TASK STRUCTURE RULES
            Every todo object MUST include all fields: "title", "importance", "details", "time", "subTodos". No field may be omitted.
            Separate responsibilities -> separate todo objects.
            Steps of one task -> subTodos array (each subTodo must include title, time, isDone:false).
            Include "details" with useful context when present in the message (e.g., "From Mom", "Bring ID", message snippet).
            All time objects must follow the structured format described above.

            ========================
            OUTPUT and VALIDATION RULES
            Return ONLY valid JSON (no markdown, no explanation).
            Strict schema. Ensure output serializes to a JSON array of objects and nothing else.
            Before returning, validate that:
            1) The output is valid JSON parseable by standard JSON parsers.
            2) Every object contains the required fields and types.
            3) All time objects have the correct structure (year, month, day, hour, minute, second).
            If validation fails, adjust output until it passes.

            Enforce these field rules:
            "title": non-empty string
            "importance": one of ["LOW","MEDIUM","HIGH","URGENT"]
            "details": string ("" if none)
            "time": object { year: int, month: int, day: int, hour: int, minute: int, second: int } — use all zeros if unscheduled
            "subTodos": array of objects { "title": string, "time": same as above, "isDone": false }

            Example output shape:
            [
              {
                "title": "string",
                "importance": "LOW | MEDIUM | HIGH | URGENT",
                "details": "string",
                "time": {"year": 2026, "month": 3, "day": 20, "hour": 17, "minute": 0, "second": 0},
                "subTodos": [
                  { "title": "string", "time": {"year": 0, "month": 0, "day": 0, "hour": 0, "minute": 0, "second": 0}, "isDone": false }
                ]
              }
            ]

            Final instruction:
            Produce the JSON array and ONLY the JSON array. No extra keys, no logging, no commentary.
            Prefer future scheduling, consistent defaults, and conflict-free timestamps.

            User prompt:
            "$prompt"
        """.trimIndent()

        var lastException: Exception? = null
        for ((index, apiKey) in apiKeys.withIndex()) {
            Log.d("AiTodoWorker", "Trying API key #${index + 1}")
            try {
                val generativeModel = GenerativeModel(
                    modelName = "gemini-2.5-flash-lite",
                    apiKey = apiKey,
                    generationConfig = generationConfig { responseMimeType = "application/json" }
                )

                val response = generativeModel.generateContent(fullPrompt)
                val responseText = response.text ?: continue

                val jsonStartIndex = responseText.indexOf("[")
                val jsonEndIndex = responseText.lastIndexOf("]")

                if (jsonStartIndex != -1 && jsonEndIndex != -1) {
                    val jsonString = responseText.substring(jsonStartIndex, jsonEndIndex + 1)
                    val extractedList = json.decodeFromString<List<Todo>>(jsonString)
                    Log.d("AiTodoWorker", "Parsed todos: $extractedList")

                    extractedList.forEach { extracted ->
                        repository.saveTodo(extracted)
                        scheduleAlarm(applicationContext, extracted)
                        extracted.subTodos.filter { it.time.toTimestamp() > 0 }.forEach {
                            scheduleAlarm(applicationContext, it, extracted.title)
                        }
                    }

                    showSuccessNotification(extractedList.size)
                    return Result.success()
                } else {
                    Log.w("AiTodoWorker", "Invalid JSON response, trying next key")
                    continue
                }
            } catch (e: Exception) {
                lastException = e
                Log.w("AiTodoWorker", "API key #${index + 1} failed: ${e.message}")
            }
        }

        Log.e("AiTodoWorker", "All API keys failed", lastException)
        showErrorNotification(lastException?.message ?: "All API keys failed")
        return Result.retry()
    }

    private fun scheduleAlarm(context: Context, todo: Todo, parentTitle: String? = null) {
        val timestamp = todo.time.toTimestamp()
        if (timestamp <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) return
        }

        val intent = Intent(context, TodoAlarmReceiver::class.java).apply {
            putExtra("EXTRA_TITLE", if (parentTitle != null) "$parentTitle: ${todo.title}" else todo.title)
            putExtra("EXTRA_ID", todo.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            todo.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            timestamp,
            pendingIntent
        )
    }

    private fun showProcessingNotification() {
        val channelId = "ai_worker_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "AI Assistant", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("AI is thinking...")
            .setContentText("Processing your mission request")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        notificationManager.notify(1001, notification)
    }

    private fun showSuccessNotification(count: Int) {
        val channelId = "ai_worker_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.cancel(1001)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("Mission Created")
            .setContentText("AI successfully added $count task(s)")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1002, notification)
    }

    private fun showErrorNotification(error: String) {
        val channelId = "ai_worker_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.cancel(1001)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("AI Error")
            .setContentText(error)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1003, notification)
    }
}
