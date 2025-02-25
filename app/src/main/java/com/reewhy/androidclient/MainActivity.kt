package com.reewhy.androidclient

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import java.net.NetworkInterface

// Porta e chiave
const val SERVER_PORT = 8080
const val SHIFT = 3

data class Message(val msg: String, val me: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Rimuovere la status bar
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        actionBar?.hide()

        setContent {
            UDPClientApp()
        }
    }
}

// Funzione per cifrare e decifrare
fun caesarCipher(input: String, shift: Int): String {
    // Ciclare per i caratteri di una stringa
    return input.map { char ->
        // Controllare che la stringa sia un lettere
        // (I caratteri speciali non verranno criptati e rimarranno tali)
        if (char.isLetter()) {
            val base = if (char.isUpperCase()) 'A' else 'a'
            // Cambiamo di posizione
            ((char - base + shift + 26) % 26 + base.code).toChar()
        } else {
            char
        }
    }.joinToString("")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UDPClientApp() {
    // IP destinatario
    var serverIp by remember { mutableStateOf("192.168.1.1") }
    // Messaggio della TextField
    var message by remember { mutableStateOf("") }
    // Lista di messaggi ricevuti e inviati
    val receivedMessages = remember { mutableStateListOf<Message>() }
    // Lista dei peers nella rete
    val peers = remember { mutableStateListOf<String>() }
    // Stato della select box
    var expanded by remember { mutableStateOf<Boolean>(false) }

    // Mandiamo un messaggio per avvissare che ci siamo uniti alla rete
    newPeer()

    // startiamo la coroutine
    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.IO).launch {

            receiveMessages (
                // Quando si riceve dei messaggi li salviamo nella lista dei messaggi ricevuti
                onMessageReceived = { decryptedMsg ->
                    receivedMessages.add(Message(decryptedMsg, false))
                },
                // Quando si riceve il messaggio di un nuovo peer, si aggiunge alla lista dei peerss
                onPeerReceived = { peer ->
                    val peerStr = peer.toString().replace("/","")

                    // Aggiungiamo solo se il peer è nuovo
                    if(!(peerStr in peers)) {
                        peers.add(peerStr)
                    }
                    Log.d("UDPClient", peer)
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "UDP Chat",
            fontSize = 28.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Select box dei peers
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                readOnly = true,
                value = serverIp,
                onValueChange = {},
                label = { Text(text = "Server IP")},
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = OutlinedTextFieldDefaults.colors(),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false}
            ) {
                peers.forEach { peer: String ->
                    DropdownMenuItem(
                        text = { Text(text = peer)},
                        onClick = {
                            expanded = false
                            serverIp = peer
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ){
            Button(
                onClick = {
                    newPeer()
                }
            ) {
                Text("Aggiorna")
            }

            Button(
                onClick = {
                    // Resettiamo la lista dei messaggi
                    receivedMessages.clear()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )

            ) {
                Text("Reset")
            }
        }


        // Chat
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            LazyColumn(
                reverseLayout = true, // Ci permette di scrollare la lista da sotto a sopra (normalmente è il contrario, quindi gli oggetti iniziano ad essere inseriti sopra)
                modifier = Modifier.fillMaxSize()
            ) {
                // Aggiungiamo alla colonna tutti i messaggi nella lista
                items(receivedMessages.asReversed()) { msg ->
                    MessageView(msg)
                }
            }
        }

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

            // Quando viene mandato un messaggio si controlla per prima cosa che la TextField non sia vuota
            // Successivamente salviamo in una variabile temporanea il messaggio
            // Aggiungiamo il messaggio nella nostra lista di messaggi
            // Mandiamo il messaggio
            // Resettiamo la variabile del messaggio
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


fun newPeer(){
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val socket = DatagramSocket()

            // Opzioni per mandare un messaggio in broadcast
            socket.broadcast = true
            val address = InetAddress.getByName("255.255.255.255")

            // Messaggio standard da inviare per avvisare che c'è un nuovo
            // TO-DO: Cambiarlo in caratteri
            val message = "con"

            val buffer = message.toByteArray()

            val packet = DatagramPacket(buffer, buffer.size, address, SERVER_PORT)
            socket.send(packet)

            Log.d("UDPClient", "New peer sent")

            socket.close()
        } catch (e: Exception) {
            Log.e("UDPClient", "Error sending message new peer: ${e.message}")
            e.printStackTrace()
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

// Funzione per ottenere la lista degli IP del dispositivo
// (La funzione si trova su Internet, non è stata programmata da me)
fun getLocalIPAddresses(): List<String> {
    return try {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .filter { !it.isLoopbackAddress && it is InetAddress }
            .map { it.hostAddress } as List<String>
    } catch (e: Exception) {
        Log.e("UDPClient", "Error getting local IPs: ${e.message}")
        emptyList()
    }
}

fun receiveMessages(onMessageReceived: (String) -> Unit, onPeerReceived: (String) -> Unit) {
    try {
        val socket = DatagramSocket(SERVER_PORT)
        val buffer = ByteArray(1024)
        // Prendiamo la lista degli indirizzi
        val localAddress = getLocalIPAddresses()

        while (true) {
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)

            // Prendiamo un messaggio e lo decriptiamo
            val encryptedMessage = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
            val decryptedMessage = caesarCipher(encryptedMessage, -SHIFT)

            Log.d("UDPClient", "Test: ${encryptedMessage}" )
            // Se il messaggio non è arrivato da me stesso ed è una messaggio di nuova connessione
            if(decryptedMessage.startsWith("zlk", ignoreCase = false)){
                val s = packet.address.toString()
                // Si chiama la funzione di callback "onPeerReceived"
                onPeerReceived(s)
            // Se invece il messaggio non è un messaggio di nuova connessione
            } else if(!decryptedMessage.startsWith("zlk")){
                Log.d("UDPClient", "Received: $decryptedMessage")
                // Si chiama la funzinoe di callback "onMessageReceived"
                onMessageReceived(decryptedMessage)
            }


        }
    } catch (e: Exception) {
        Log.e("UDPClient", "Error receiving message: ${e.message}")
    }
}
