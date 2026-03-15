package com.majzoub.todos.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.majzoub.todos.model.Importance
import com.majzoub.todos.model.RepeatMode
import com.majzoub.todos.model.Todo
import com.majzoub.todos.model.TodoTime
import com.majzoub.todos.model.toTimestamp          // <-- import conversion
import com.majzoub.todos.ui.theme.*
import com.majzoub.todos.viewmodel.TodoFilter
import com.majzoub.todos.viewmodel.TodoViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(viewModel: TodoViewModel = viewModel()) {
    val todos by viewModel.filteredTodos.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var todoToEdit by remember { mutableStateOf<Todo?>(null) }

    LaunchedEffect(Unit) {
        viewModel.syncWithFirestore()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Todo")
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(modifier = Modifier.padding(padding).padding(top = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lazy Todos",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        
                        IconButton(onClick = { viewModel.syncWithFirestore() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Update todos from DB",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                FilterChips(currentFilter = filter, onFilterSelected = { viewModel.setFilter(it) })
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp, start = 8.dp, end = 8.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(todos) { todo ->
                        if (filter == TodoFilter.TIMELINE) {
                            TimelineItem(todo)
                        } else {
                            TodoItem(
                                todo = todo,
                                onToggleDone = { viewModel.toggleDone(todo) },
                                onDelete = { viewModel.deleteTodo(todo) },
                                onEdit = { todoToEdit = todo }
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 4.dp,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Working...",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        TodoEditDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false },
            onSave = { title, details, importance, time, subTodos, repeatMode, repeatDays ->
                viewModel.addTodo(title, details, importance, time, subTodos, repeatMode, repeatDays)
                showAddDialog = false
            }
        )
    }

    todoToEdit?.let { todo ->
        TodoEditDialog(
            todo = todo,
            viewModel = viewModel,
            onDismiss = { todoToEdit = null },
            onSave = { title, details, importance, time, subTodos, repeatMode, repeatDays ->
                viewModel.updateTodo(todo.copy(
                    title = title, 
                    details = details, 
                    importance = importance, 
                    time = time, 
                    subTodos = subTodos,
                    repeatMode = repeatMode,
                    repeatDays = repeatDays
                ))
                todoToEdit = null
            }
        )
    }
}

@Composable
fun FilterChips(currentFilter: TodoFilter, onFilterSelected: (TodoFilter) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(TodoFilter.entries.toTypedArray()) { filter ->
            FilterChip(
                selected = currentFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.name, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
fun TimelineItem(todo: Todo) {
    val isDark = isSystemInDarkTheme()
    val importanceColor = when (todo.importance) {
        Importance.LOW -> if (isDark) LowImportanceDark else LowImportanceLight
        Importance.MEDIUM -> if (isDark) MediumImportanceDark else MediumImportanceLight
        Importance.HIGH -> if (isDark) HighImportanceDark else HighImportanceLight
        Importance.URGENT -> if (isDark) UrgentImportanceDark else UrgentImportanceLight
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.width(70.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(todo.time.toTimestamp())),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(todo.time.toTimestamp())),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .background(importanceColor.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp)
        )

        Card(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, importanceColor.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = importanceColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (todo.details.isNotBlank()) {
                    Text(
                        text = todo.details,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun TodoItem(
    todo: Todo,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    
    val importanceColor = when (todo.importance) {
        Importance.LOW -> if (isDark) LowImportanceDark else LowImportanceLight
        Importance.MEDIUM -> if (isDark) MediumImportanceDark else MediumImportanceLight
        Importance.HIGH -> if (isDark) HighImportanceDark else HighImportanceLight
        Importance.URGENT -> if (isDark) UrgentImportanceDark else UrgentImportanceLight
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.5.dp, importanceColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleDone) {
                Icon(
                    if (todo.isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Toggle Done",
                    tint = if (todo.isDone) MaterialTheme.colorScheme.primary else importanceColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (todo.isDone) TextDecoration.LineThrough else null,
                    color = if (todo.isDone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface
                )
                if (todo.details.isNotBlank()) {
                    Text(
                        text = todo.details,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(todo.time.toTimestamp())),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (todo.repeatMode != RepeatMode.NONE) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (todo.subTodos.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${todo.subTodos.count { it.isDone }}/${todo.subTodos.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Time Left
                    if (!todo.isDone) {
                        Spacer(modifier = Modifier.width(12.dp))
                        val timeLeft = todo.time.toTimestamp() - System.currentTimeMillis()
                        val timeLeftText = if (timeLeft < 0) {
                            "Overdue"
                        } else {
                            val days = TimeUnit.MILLISECONDS.toDays(timeLeft)
                            val hours = TimeUnit.MILLISECONDS.toHours(timeLeft) % 24
                            val minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeft) % 60
                            
                            when {
                                days > 0 -> "${days}d ${hours}h left"
                                hours > 0 -> "${hours}h ${minutes}m left"
                                else -> "${minutes}m left"
                            }
                        }
                        
                        Surface(
                            color = if (timeLeft < 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = timeLeftText,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = if (timeLeft < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(importanceColor, CircleShape)
            )

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditDialog(
    todo: Todo? = null,
    viewModel: TodoViewModel,
    onDismiss: () -> Unit,
    onSave: (String, String, Importance, TodoTime, List<Todo>, RepeatMode, List<Int>) -> Unit
) {
    var title by remember { mutableStateOf(todo?.title ?: "") }
    var details by remember { mutableStateOf(todo?.details ?: "") }
    var importance by remember { mutableStateOf(todo?.importance ?: Importance.LOW) }
    var todoTime by remember { mutableStateOf(todo?.time ?: TodoTime.UNSCHEDULED) }
    var subTodos by remember { mutableStateOf(todo?.subTodos ?: emptyList<Todo>()) }
    var repeatMode by remember { mutableStateOf(todo?.repeatMode ?: RepeatMode.NONE) }
    var repeatDays by remember { mutableStateOf(todo?.repeatDays ?: emptyList<Int>()) }
    var isAiMode by remember { mutableStateOf(false) }
    var aiPrompt by remember { mutableStateOf("") }
    
    val context = LocalContext.current

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.95f),
        content = {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (todo == null) "New Task" else "Edit Task",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Row {
                            IconButton(onClick = { isAiMode = !isAiMode }) {
                                Icon(
                                    if (isAiMode) Icons.Default.Edit else Icons.Default.AutoAwesome,
                                    contentDescription = "AI Mode",
                                    tint = if (isAiMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isAiMode) {
                        AiPromptSection(
                            prompt = aiPrompt,
                            onPromptChange = { aiPrompt = it },
                            onProcess = {
                                viewModel.processAiPromptInBackground(aiPrompt)
                                onDismiss()
                            }
                        )
                    } else {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("What needs to be done?") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = details,
                            onValueChange = { details = it },
                            label = { Text("Details (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Importance", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Importance.entries.forEach { level ->
                                FilterChip(
                                    selected = importance == level,
                                    onClick = { importance = level },
                                    label = { Text(level.name, fontSize = 10.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Time", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val calendar = if (todoTime == TodoTime.UNSCHEDULED) {
                                        Calendar.getInstance()
                                    } else {
                                        Calendar.getInstance().apply {
                                            set(Calendar.YEAR, todoTime.year)
                                            set(Calendar.MONTH, todoTime.month - 1)
                                            set(Calendar.DAY_OF_MONTH, todoTime.day)
                                            set(Calendar.HOUR_OF_DAY, todoTime.hour)
                                            set(Calendar.MINUTE, todoTime.minute)
                                            set(Calendar.SECOND, todoTime.second)
                                        }
                                    }
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            calendar.set(y, m, d)
                                            TimePickerDialog(
                                                context,
                                                { _, hh, mm ->
                                                    calendar.set(Calendar.HOUR_OF_DAY, hh)
                                                    calendar.set(Calendar.MINUTE, mm)
                                                    todoTime = TodoTime(
                                                        year = calendar.get(Calendar.YEAR),
                                                        month = calendar.get(Calendar.MONTH) + 1,
                                                        day = calendar.get(Calendar.DAY_OF_MONTH),
                                                        hour = calendar.get(Calendar.HOUR_OF_DAY),
                                                        minute = calendar.get(Calendar.MINUTE),
                                                        second = 0
                                                    )
                                                },
                                                calendar.get(Calendar.HOUR_OF_DAY),
                                                calendar.get(Calendar.MINUTE),
                                                true
                                            ).show()
                                        },
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Event, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            val displayText = if (todoTime == TodoTime.UNSCHEDULED) {
                                "Not scheduled"
                            } else {
                                SimpleDateFormat("EEEE, MMM dd 'at' HH:mm", Locale.getDefault()).format(Date(todoTime.toTimestamp()))
                            }
                            Text(displayText)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Repeat", style = MaterialTheme.typography.labelLarge)
                        var expandedRepeat by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedCard(
                                onClick = { expandedRepeat = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (repeatMode == RepeatMode.DAYS_OF_WEEK) {
                                        val days = repeatDays.sorted().joinToString(", ") { dayInt ->
                                            when (dayInt) {
                                                Calendar.SUNDAY -> "Sun"
                                                Calendar.MONDAY -> "Mon"
                                                Calendar.TUESDAY -> "Tue"
                                                Calendar.WEDNESDAY -> "Wed"
                                                Calendar.THURSDAY -> "Thu"
                                                Calendar.FRIDAY -> "Fri"
                                                Calendar.SATURDAY -> "Sat"
                                                else -> ""
                                            }
                                        }
                                        "Every: $days"
                                    } else repeatMode.name,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = expandedRepeat,
                                onDismissRequest = { expandedRepeat = false }
                            ) {
                                RepeatMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.name) },
                                        onClick = {
                                            repeatMode = mode
                                            expandedRepeat = false
                                        }
                                    )
                                }
                            }
                        }

                        if (repeatMode == RepeatMode.DAYS_OF_WEEK) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val dayNames = listOf("S", "M", "T", "W", "T", "F", "S")
                                val dayInts = listOf(
                                    Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
                                    Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
                                )
                                dayInts.forEachIndexed { index, dayInt ->
                                    val isSelected = repeatDays.contains(dayInt)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            repeatDays = if (isSelected) {
                                                repeatDays - dayInt
                                            } else {
                                                repeatDays + dayInt
                                            }
                                        },
                                        label = { Text(dayNames[index], fontSize = 10.sp) },
                                        shape = CircleShape,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        SubTodoSection(subTodos = subTodos, onSubTodosChange = { subTodos = it })

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { onSave(title, details, importance, todoTime, subTodos, repeatMode, repeatDays) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = title.isNotBlank()
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Task")
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun AiPromptSection(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onProcess: () -> Unit
) {
    val context = LocalContext.current
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    var isListening by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer.destroy()
        }
    }

    Column {
        Text(
            "Extract Tasks(AI) ",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            placeholder = { Text("Type or speak...") },
            trailingIcon = {
                IconButton(onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak")
                    }
                    
                    speechRecognizer.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) { isListening = true }
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() { isListening = false }
                        override fun onError(error: Int) { isListening = false }
                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                onPromptChange(prompt + " " + matches[0])
                            }
                        }
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                    speechRecognizer.startListening(intent)
                }) {
                    Icon(
                        if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                        contentDescription = "Voice Input",
                        tint = if (isListening) Color.Red else MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onProcess,
            modifier = Modifier.fillMaxWidth(),
            enabled = prompt.isNotBlank(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Extract Tasks")
        }
    }
}

@Composable
fun SubTodoSection(subTodos: List<Todo>, onSubTodosChange: (List<Todo>) -> Unit) {
    var newSubTitle by remember { mutableStateOf("") }

    Column {
        Text("Checklist", style = MaterialTheme.typography.labelLarge)
        
        subTodos.forEachIndexed { index, sub ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = sub.isDone,
                    onCheckedChange = { checked ->
                        val updated = subTodos.toMutableList()
                        updated[index] = sub.copy(isDone = checked)
                        onSubTodosChange(updated)
                    }
                )
                Text(
                    text = sub.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                IconButton(onClick = {
                    val updated = subTodos.toMutableList()
                    updated.removeAt(index)
                    onSubTodosChange(updated)
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newSubTitle,
                onValueChange = { newSubTitle = it },
                label = { Text("Add sub-task", fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            )
            IconButton(
                onClick = {
                    if (newSubTitle.isNotBlank()) {
                        onSubTodosChange(subTodos + Todo(title = newSubTitle))
                        newSubTitle = ""
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    }
}