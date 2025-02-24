package com.reewhy.androidclient

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

const val SERVER_PORT = 8080
const val SHIFT = 3

data class Message(val msg: String, val me: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        actionBar?.hide()

        setContent {
            UDPClientApp()
        }
    }
}

// Caesar cipher for encoding/decoding
fun caesarCipher(input: String, shift: Int): String {
    return input.map { char ->
        if (char.isLetter()) {
            val base = if (char.isUpperCase()) 'A' else 'a'
            ((char - base + shift + 26) % 26 + base.code).toChar()
        } else {
            char
        }
    }.joinToString("")
}

@Composable
fun UDPClientApp() {
    var serverIp by remember { mutableStateOf("192.168.1.45") }
    var message by remember { mutableStateOf("") }
    val receivedMessages = remember { mutableStateListOf<Message>() }

    // Launch the receiving coroutine
    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            receiveMessages { decryptedMsg ->
                receivedMessages.add(Message(decryptedMsg, false)) // Add new message to the list
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            "UDP Chat",
            fontSize = 28.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Server IP Input
        TextField(
            value = serverIp,
            onValueChange = { serverIp = it },
            label = { Text("Server IP") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        // Chat History
        Box(
            modifier = Modifier
                .weight(1f) // Takes up available space
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            LazyColumn(
                reverseLayout = true, // Scrolls from bottom to top
                modifier = Modifier.fillMaxSize()
            ) {
                items(receivedMessages.asReversed()) { msg ->
                    MessageView(msg)
                }
            }
        }

        // Message Input and Send Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Message") },
                modifier = Modifier
                    .weight(1f) // Input takes most of the space
                    .padding(end = 8.dp)
            )

            Button(onClick = {
                if (message.isNotBlank()) {
                    val toSend: String = message
                    CoroutineScope(Dispatchers.IO).launch {
                        receivedMessages.add(Message(toSend, true))
                        sendMessage(toSend, serverIp)
                    }
                    message = ""
                }
            }) {
                Text("Send")
            }
        }
    }
}


@Composable
fun MessageView(msg: Message) {
    val bubbleColor = if (msg.me) MaterialTheme.colorScheme.tertiaryContainer
    else MaterialTheme.colorScheme.primaryContainer

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        horizontalArrangement = if (msg.me) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = bubbleColor,
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (msg.me) 20.dp else 0.dp,
                        bottomEnd = if (msg.me) 0.dp else 20.dp
                    )
                )
                .padding(12.dp) // Padding inside the bubble
        ) {
            Text(
                text = msg.msg,
                fontSize = 18.sp,
                color = if (msg.me) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}





// Function to send an encrypted message
fun sendMessage(message: String, SERVER_IP: String) {
    Log.d("UDPClient", "Sending: $message")
    try {
        val socket = DatagramSocket()
        val address = InetAddress.getByName(SERVER_IP)

        val encryptedMessage = caesarCipher(message, SHIFT)
        val buffer = encryptedMessage.toByteArray()

        val packet = DatagramPacket(buffer, buffer.size, address, SERVER_PORT)
        socket.send(packet)

        Log.d("UDPClient", "Sent: $encryptedMessage")

        socket.close()
    } catch (e: Exception) {
        Log.e("UDPClient", "Error sending message: ${e.message}")
    }
}

// Function to receive and decrypt messages
fun receiveMessages(onMessageReceived: (String) -> Unit) {
    try {
        val socket = DatagramSocket(SERVER_PORT)
        val buffer = ByteArray(1024)

        while (true) {
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)

            val encryptedMessage = String(packet.data, 0, packet.length)
            val decryptedMessage = caesarCipher(encryptedMessage, -SHIFT)

            Log.d("UDPClient", "Received: $decryptedMessage")

            onMessageReceived(decryptedMessage)
        }
    } catch (e: Exception) {
        Log.e("UDPClient", "Error receiving message: ${e.message}")
    }
}
