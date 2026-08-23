package com.dialect.voice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dialect.voice.api.ElevenLabsClient
import com.dialect.voice.api.OpenAIClient
import com.dialect.voice.domain.DIALECTS
import com.dialect.voice.domain.Message
import com.dialect.voice.domain.MessageRole
import com.dialect.voice.domain.MessageStatus

@Composable
fun ChatScreen(
    openAiClient: OpenAIClient,
    elevenLabsClient: ElevenLabsClient,
    viewModel: ChatViewModel = remember {
        ChatViewModel(openAiClient, elevenLabsClient)
    }
) {
    val selectedDialect by viewModel.selectedDialect.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var dialectDropdownExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // Header with dialect selector
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dialect:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.width(8.dp))

                // Dialect dropdown
                Button(onClick = { dialectDropdownExpanded = true }) {
                    Text(DIALECTS[selectedDialect]?.label ?: "Select")
                }

                DropdownMenu(
                    expanded = dialectDropdownExpanded,
                    onDismissRequest = { dialectDropdownExpanded = false }
                ) {
                    DIALECTS.forEach { (key, dialect) ->
                        DropdownMenuItem(
                            text = { Text(dialect.label) },
                            onClick = {
                                viewModel.setDialect(key)
                                dialectDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Chat messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp),
            state = listState
        ) {
            items(messages) { message ->
                ChatBubble(message = message)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Error message
        if (error != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                color = Color(0xFFFFCDD2),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = error ?: "",
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFFC62828),
                    fontSize = 12.sp
                )
            }
        }

        // Input bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.Transparent),
                    placeholder = { Text("Type or speak...") },
                    textStyle = TextStyle(fontSize = 14.sp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isLoading) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    enabled = !isLoading && inputText.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send"
                    )
                }
            }
        }

        if (isLoading) {
            Text(
                "Processing...",
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(12.dp)
            )
        }
    }
}

@Composable
fun ChatBubble(message: Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.role == MessageRole.USER)
            androidx.compose.foundation.layout.Arrangement.End
        else
            androidx.compose.foundation.layout.Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(4.dp),
            shape = RoundedCornerShape(12.dp),
            color = if (message.role == MessageRole.USER)
                Color(0xFF1976D2)
            else
                Color(0xFFE0E0E0),
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.text,
                    color = if (message.role == MessageRole.USER)
                        Color.White
                    else
                        Color.Black,
                    fontSize = 14.sp
                )

                // Show audio player for messages with audio
                if (message.audioUrl != null && message.role == MessageRole.ASSISTANT) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AudioPlayer(audioUrl = message.audioUrl)
                }

                // Show status for pending/error
                if (message.status == MessageStatus.PENDING) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Loading...", fontSize = 10.sp, color = Color.Gray)
                }
                if (message.status == MessageStatus.ERROR) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Error",
                        fontSize = 10.sp,
                        color = Color(0xFFC62828)
                    )
                }
            }
        }
    }
}

@Composable
fun AudioPlayer(audioUrl: String) {
    var isPlaying by remember { mutableStateOf(false) }

    Button(
        onClick = {
            // TODO: Implement audio playback
            isPlaying = !isPlaying
        },
        modifier = Modifier.height(32.dp)
    ) {
        Text(if (isPlaying) "⏸ Playing..." else "▶ Play audio", fontSize = 12.sp)
    }
}
