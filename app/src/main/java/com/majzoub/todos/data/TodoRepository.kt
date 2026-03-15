package com.majzoub.todos.data

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.majzoub.todos.model.*
import com.majzoub.todos.receiver.AlarmScheduler
import com.majzoub.todos.widget.TodoWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import androidx.glance.appwidget.updateAll

class TodoRepository(private val context: Context) {
    private val firestore = FirebaseFirestore.getInstance()
    private val collection = firestore.collection("todos")
    private val localFile = File(context.filesDir, "todos.json")
    private val alarmScheduler = AlarmScheduler(context)

    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }

    private val _todosFlow = MutableStateFlow<List<Todo>>(loadInitialDataSync())
    val todosFlow: StateFlow<List<Todo>> = _todosFlow.asStateFlow()

    fun loadInitialData() {
        _todosFlow.value = loadInitialDataSync()
    }

    private fun loadInitialDataSync(): List<Todo> {
        if (!localFile.exists()) return emptyList()
        return try {
            val content = localFile.readText()
            json.decodeFromString<List<Todo>>(content)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun saveLocalTodosToDisk(todos: List<Todo>) = withContext(Dispatchers.IO) {
        try {
            val content = json.encodeToString(todos)
            localFile.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun mapToTodoTime(map: Map<String, Any>?): TodoTime {
        if (map == null) return TodoTime.UNSCHEDULED
        return TodoTime(
            year = (map["year"] as? Long)?.toInt() ?: 0,
            month = (map["month"] as? Long)?.toInt() ?: 0,
            day = (map["day"] as? Long)?.toInt() ?: 0,
            hour = (map["hour"] as? Long)?.toInt() ?: 0,
            minute = (map["minute"] as? Long)?.toInt() ?: 0,
            second = (map["second"] as? Long)?.toInt() ?: 0
        )
    }

    suspend fun fetchFromFirestore() = withContext(Dispatchers.IO) {
        try {
            val snapshot = collection.get().await()
            val todos = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                try {
                    Todo(
                        id = doc.id,
                        title = data["title"] as? String ?: "",
                        details = data["details"] as? String ?: "",
                        importance = Importance.valueOf(data["importance"] as? String ?: "LOW"),
                        time = mapToTodoTime(data["time"] as? Map<String, Any>),
                        isDone = data["done"] as? Boolean ?: false,
                        repeatMode = RepeatMode.valueOf(data["repeatMode"] as? String ?: "NONE"),
                        repeatDays = (data["repeatDays"] as? List<Long>)?.map { it.toInt() } ?: emptyList(),
                        subTodos = (data["subTodos"] as? List<Map<String, Any>>)?.map {
                            Todo(
                                title = it["title"] as? String ?: "",
                                isDone = it["done"] as? Boolean ?: false,
                                time = mapToTodoTime(it["time"] as? Map<String, Any>)
                            )
                        } ?: emptyList()
                    )
                } catch (e: Exception) {
                    null
                }
            }
            _todosFlow.value = todos
            saveLocalTodosToDisk(todos)
            todos.forEach { alarmScheduler.schedule(it) }
            
            TodoWidget().updateAll(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveTodo(todo: Todo) {
        val currentList = _todosFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == todo.id }
        if (index != -1) {
            currentList[index] = todo
        } else {
            currentList.add(todo)
        }
        _todosFlow.value = currentList

        if (todo.isDone && todo.repeatMode == RepeatMode.NONE) {
            alarmScheduler.cancel(todo.id)
            todo.subTodos.forEach { alarmScheduler.cancel(it.id) }
        } else {
            alarmScheduler.schedule(todo)
        }

        saveLocalTodosToDisk(currentList)
        
        TodoWidget().updateAll(context)

        withContext(Dispatchers.IO) {
            try {
                val todoMap = hashMapOf(
                    "title" to todo.title,
                    "details" to todo.details,
                    "importance" to todo.importance.name,
                    "time" to mapOf(
                        "year" to todo.time.year,
                        "month" to todo.time.month,
                        "day" to todo.time.day,
                        "hour" to todo.time.hour,
                        "minute" to todo.time.minute,
                        "second" to todo.time.second
                    ),
                    "done" to todo.isDone,
                    "repeatMode" to todo.repeatMode.name,
                    "repeatDays" to todo.repeatDays,
                    "subTodos" to todo.subTodos.map {
                        mapOf(
                            "title" to it.title,
                            "done" to it.isDone,
                            "time" to mapOf(
                                "year" to it.time.year,
                                "month" to it.time.month,
                                "day" to it.time.day,
                                "hour" to it.time.hour,
                                "minute" to it.time.minute,
                                "second" to it.time.second
                            )
                        )
                    }
                )
                collection.document(todo.id).set(todoMap).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun deleteTodo(todoId: String) {
        _todosFlow.value.find { it.id == todoId }?.let { todo ->
            alarmScheduler.cancel(todo.id)
            todo.subTodos.forEach { alarmScheduler.cancel(it.id) }
        }

        val currentList = _todosFlow.value.filterNot { it.id == todoId }
        _todosFlow.value = currentList

        saveLocalTodosToDisk(currentList)

        TodoWidget().updateAll(context)

        withContext(Dispatchers.IO) {
            try {
                collection.document(todoId).delete().await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun getLocalTodos(): List<Todo> = _todosFlow.value
}
