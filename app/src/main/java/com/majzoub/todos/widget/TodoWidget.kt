package com.majzoub.todos.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.tv.material3.Border
import com.majzoub.todos.MainActivity
import com.majzoub.todos.data.TodoRepository
import com.majzoub.todos.model.Importance
import com.majzoub.todos.model.Todo
import com.majzoub.todos.model.toTimestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TodoWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = TodoRepository(context)
        val todos = repository.getLocalTodos()

        provideContent {
            GlanceTheme {
                Content(todos)
            }
        }
    }

    @Composable
    private fun Content(todos: List<Todo>) {
        val activeTodos = todos.filter { !it.isDone }
            .sortedBy { it.time.toTimestamp() }
        
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.background)
                .cornerRadius(24.dp)
                .padding(16.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "Lazy Todos",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = GlanceTheme.colors.onSurface
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = GlanceModifier
                                .size(6.dp)
                                .cornerRadius(3.dp)
                                .background(if (activeTodos.isNotEmpty()) GlanceTheme.colors.error else GlanceTheme.colors.secondary)
                        ) {}
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Text(
                            text = if (activeTodos.isNotEmpty()) "${activeTodos.size} PENDING" else "ALL Done",
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = GlanceTheme.colors.onSurfaceVariant
                            )
                        )
                    }
                }

                Box(
                    modifier = GlanceModifier
                        .size(38.dp)
                        .cornerRadius(30.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "New",
                        style = TextStyle(
                            fontSize = 16.sp
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(12.dp))

            if (activeTodos.isEmpty()) {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No Active Tasks",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(activeTodos) { todo ->
                        TodoItem(todo)
                    }
                }
            }
        }
    }

    @Composable
    private fun TodoItem(todo: Todo) {
        val importanceColor = when (todo.importance) {
            Importance.LOW -> GlanceTheme.colors.tertiary
            Importance.MEDIUM -> GlanceTheme.colors.secondary
            Importance.HIGH -> GlanceTheme.colors.primary
            Importance.URGENT -> GlanceTheme.colors.error
        }

        Column() {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .cornerRadius(14.dp)
                    .background(GlanceTheme.colors.surfaceVariant)
            ) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = GlanceModifier
                            .width(4.dp)
                            .height(36.dp)
                            .cornerRadius(14.dp)
                            .background(importanceColor)
                    ) {}

                    Spacer(modifier = GlanceModifier.width(16.dp))

                    Column(modifier = GlanceModifier.defaultWeight().cornerRadius(14.dp)) {
                        Text(
                            text = todo.title.uppercase(),
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = GlanceTheme.colors.onSurface
                            ),
                            maxLines = 1
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (todo.time.toTimestamp() > 0) {
                                val dateText = SimpleDateFormat("HH:mm", Locale.getDefault())
                                    .format(Date(todo.time.toTimestamp()))
                                Text(
                                    text = "Time: $dateText",
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GlanceTheme.colors.primary
                                    )
                                )
                            }
                        }
                    }

                    Box(
                        modifier = GlanceModifier
                            .size(60.dp, 40.dp)
                            .cornerRadius(10.dp)
                            .background(GlanceTheme.colors.primaryContainer)
                            .clickable(
                                actionRunCallback<ToggleTodoAction>(
                                    actionParametersOf(ToggleTodoAction.todoIdKey to todo.id)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "DONE",
                            style = TextStyle(
                                color = GlanceTheme.colors.onPrimaryContainer,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
        }
    }
}

class ToggleTodoAction : ActionCallback {
    companion object {
        val todoIdKey = ActionParameters.Key<String>("todo_id")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val todoId = parameters[todoIdKey] ?: return
        val repository = TodoRepository(context)
        val todo = repository.getLocalTodos().find { it.id == todoId }
        if (todo != null) {
            repository.saveTodo(todo.copy(isDone = true))
        }
        TodoWidget().updateAll(context)
    }
}

class TodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoWidget()
}
