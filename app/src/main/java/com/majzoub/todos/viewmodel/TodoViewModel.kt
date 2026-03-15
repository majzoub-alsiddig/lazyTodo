package com.majzoub.todos.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.majzoub.todos.data.TodoRepository
import com.majzoub.todos.model.Importance
import com.majzoub.todos.model.RepeatMode
import com.majzoub.todos.model.Todo
import com.majzoub.todos.model.TodoTime
import com.majzoub.todos.model.toTimestamp
import com.majzoub.todos.worker.AiTodoWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

enum class TodoFilter { DEFAULT, IMPORTANCE, TIME, DONE, TIMELINE }

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TodoRepository(application)
    private val workManager = WorkManager.getInstance(application)

    private val _filter = MutableStateFlow(TodoFilter.DEFAULT)
    val filter: StateFlow<TodoFilter> = _filter

    private val _isSyncing = MutableStateFlow(false)

    val aiWorkStatus: StateFlow<Boolean> = workManager
        .getWorkInfosByTagFlow("AI_TASK")
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isLoading: StateFlow<Boolean> = combine(_isSyncing, aiWorkStatus) { syncing, aiRunning ->
        syncing || aiRunning
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val filteredTodos: StateFlow<List<Todo>> = combine(repository.todosFlow, _filter) { todos, filter ->
        when (filter) {
            TodoFilter.DEFAULT -> todos
                .filter { !it.isDone }
                .sortedWith(compareBy<Todo> { it.time.toTimestamp() }.thenByDescending { it.importance })
            TodoFilter.IMPORTANCE -> todos
                .filter { !it.isDone }
                .sortedByDescending { it.importance }
            TodoFilter.TIME -> todos
                .filter { !it.isDone }
                .sortedBy { it.time.toTimestamp() }
            TodoFilter.DONE -> todos.filter { it.isDone }
            TodoFilter.TIMELINE -> {
                val allTasks = mutableListOf<Todo> ()
                todos.forEach { parent ->
                    if (!parent.isDone) {
                        allTasks.add(parent)
                    }
                    parent.subTodos.forEach { sub ->
                        if (!sub.isDone && sub.time.toTimestamp() > 0) {
                            allTasks.add(sub.copy(title = "${parent.title} > ${sub.title}"))
                        }
                    }
                }
                allTasks.sortedBy { it.time.toTimestamp() }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun syncWithFirestore() {
        viewModelScope.launch {
            _isSyncing.value = true
            try { repository.fetchFromFirestore() } finally { _isSyncing.value = false }
        }
    }

    fun addTodo(
        title: String,
        details: String,
        importance: Importance,
        time: TodoTime,
        subTodos: List<Todo>,
        repeatMode: RepeatMode = RepeatMode.NONE,
        repeatDays: List<Int> = emptyList()
    ) {
        val newTodo = Todo(
            title = title,
            details = details,
            importance = importance,
            time = time,
            subTodos = subTodos,
            repeatMode = repeatMode,
            repeatDays = repeatDays
        )
        viewModelScope.launch { repository.saveTodo(newTodo) }
    }

    fun processAiPromptInBackground(prompt: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val aiWorkRequest = OneTimeWorkRequestBuilder<AiTodoWorker>()
            .setInputData(workDataOf("PROMPT" to prompt))
            .setConstraints(constraints)
            .addTag("AI_TASK")
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniqueWork(
            "AI_PROMPT_${prompt.hashCode()}",
            ExistingWorkPolicy.REPLACE,
            aiWorkRequest
        )
    }

    fun toggleDone(todo: Todo) {
        viewModelScope.launch { repository.saveTodo(todo.copy(isDone = !todo.isDone)) }
    }

    fun deleteTodo(todo: Todo) {
        viewModelScope.launch { repository.deleteTodo(todo.id) }
    }

    fun updateTodo(todo: Todo) {
        viewModelScope.launch { repository.saveTodo(todo) }
    }

    fun setFilter(filter: TodoFilter) { _filter.value = filter }
}
