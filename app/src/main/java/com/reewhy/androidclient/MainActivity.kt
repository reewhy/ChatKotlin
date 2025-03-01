package com.reewhy.androidclient

// Imports
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.security.MessageDigest.*

// Classe dei messaggi
data class Message(val msg: String, val me: Boolean)

// Porta e chiave
const val SERVER_PORT = 8080
const val SHIFT = 3

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Rimuovere la status bar
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        actionBar?.hide()

        setContent {
            UDPClientApp(this)
        }
    }
}

// Fuzniona per calcolare l'hash
fun sha256(input: String): String {
    val bytes = getInstance("SHA-256").digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
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
fun UDPClientApp(context: Context) {
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
    newPeer("con0")


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
                    if(peerStr !in peers) {
                        peers.add(peerStr)
                    }
                },
                con = context
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
                    newPeer("con0")
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

            // Forziamo l'errore quando cliccato
            Button(
                onClick = {
                    forzaErrore(serverIp)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Text("Forza")
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
                label = { Text("Messaggio") },
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
                Text("Invia")
            }
        }
    }
}

// UI di un messaggio
@Composable
fun MessageView(msg: Message) {
    val bubbleColor = if (msg.me) MaterialTheme.colorScheme.tertiaryContainer
    else MaterialTheme.colorScheme.primaryContainer

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        // Il messaggio viene messo sulla destra se fatto da me e sulla sinistra se fatto da altri
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
                .padding(12.dp) // Padding all'interno del messaggio
        ) {
            Text(
                text = msg.msg,
                fontSize = 18.sp,
                // Il colore cambia in base all'autore del messaggio come la posizione
                color = if (msg.me) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// Funziona per avvisare in broadcast che c'è un nuovo peer nella LAN
fun newPeer(message: String){
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val socket = DatagramSocket()

            // Opzioni per mandare un messaggio in broadcast
            socket.broadcast = true
            val address = InetAddress.getByName("255.255.255.255")

            // Generariamo l'hash del messaggio
            val hash = sha256(message)

            // Creiamo il messaggio da inviare
            val messageWithHash = "$message::$hash"

            // Invio del messaggio
            val buffer = messageWithHash.toByteArray()
            val packet = DatagramPacket(buffer, buffer.size, address, SERVER_PORT)
            socket.send(packet)

            socket.close() // Chiusura del socket
        } catch (e: Exception) {
            Log.e("UDPClient", "Error sending message new peer: ${e.message}")
            e.printStackTrace()
        }
    }
}

// Funzione per forzare un errore con gli hash
fun forzaErrore(serverIp: String) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            Log.d("UDPClient", "Forcing error to $serverIp:$SERVER_PORT")

            val socket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress("0.0.0.0", 0)) // Binds to an available port
            }

            val address = InetAddress.getByName(serverIp)

            // Creiamo un messaggio con un errore con l'hash appositamente
            val messageWithHash = "ciao::err"
            val buffer = messageWithHash.toByteArray()

            val packet = DatagramPacket(buffer, buffer.size, address, SERVER_PORT)
            socket.send(packet)

            Log.d("UDPClient", "Sent: $messageWithHash")
            socket.close()

        } catch (e: Exception) {
            Log.e("UDPClient", "Error sending message: ${e.message}", e) // Logs full stack trace
        }
    }
}

// Function to send an encrypted message
fun sendMessage(message: String, serverIp: String) {
    Log.d("UDPClient", "Sending: $message")
    try {
        // Creiamo il socket
        val socket = DatagramSocket()
        // Prendiamo l'indirizzo del destinatario
        val address = InetAddress.getByName(serverIp)

        // Generiamo il messaggio da inviare
        val encryptedMessage = caesarCipher(message, SHIFT)
        val messageHash = sha256(encryptedMessage)
        val messageWithHash = "$encryptedMessage::$messageHash"

        // Invio del messaggio
        val buffer = messageWithHash.toByteArray()
        val packet = DatagramPacket(buffer, buffer.size, address, SERVER_PORT)
        socket.send(packet)

        // Chiusura del socket
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
            .map { it.hostAddress }
    } catch (e: Exception) {
        Log.e("UDPClient", "Error getting local IPs: ${e.message}")
        emptyList()
    }
}

// Funzione per mostrare i Toast all'interno di una Coroutine
fun showToast(con: Context){
    CoroutineScope(Dispatchers.IO).launch {
        withContext(Dispatchers.Main) {
            Toast.makeText(con, "Malformed message received", Toast.LENGTH_LONG).show()
        }
    }
}

// Funzione per ricevere i messaggi
fun receiveMessages(
    // Funzioni di callback
    onMessageReceived: (String) -> Unit,    // Callback quando si riceve un messaggio normale
    onPeerReceived: (String) -> Unit,       // Callback quando si riceve un nuovo peer
    con: Context,
) {
    try {
        // Creazione di un socket
        val socket = DatagramSocket(SERVER_PORT)
        val buffer = ByteArray(1024)
        val localAddress = getLocalIPAddresses()

        while (true) {
            // Prendiamo il pacchetto ricevuto
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)

            // Trasformiamo i dati in una stringa
            val receivedData = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()

            // Saltiamo il passo del ciclo se il mittente è il localhost
            if (localAddress.contains(packet.address?.hostAddress)) {
                continue
            }

            // Saltiamo il passo del ciclo se il messaggio non ha il separatore dell'hash
            // (Si prende per scontato che se il messaggio non ha il separatore, la stringa è deformata)
            if(!receivedData.contains("::")){
                showToast(con)
                Log.e("UDPClient", "Hash error")
                continue
            }

            // Dividiamo il messaggio e l'hash ricevuti
            val (encryptedMessage, receivedHash) = receivedData.split("::", limit = 2)

            val computedHash = sha256(encryptedMessage) // Calcolo dell'hash
            // Controlliamo che l'hash ricevuto e quello calcolato siano uguali
            if(computedHash != receivedHash){
                showToast(con)
                Log.e("UDPClient", "Hash error")
                continue
            }
            // Decriptiamo il messaggio
            val decryptedMessage = caesarCipher(encryptedMessage, -SHIFT)

            // Se il messaggio è "con0" ci troviamo nel caso in cui un nuovo host si è unito alla rete
            // quindi salviamo il suo IP nella lista dei peers e inviamo indietro un messaggio "con0"
            if (encryptedMessage.startsWith("con0", ignoreCase = false)) {
                val peerAddress = packet.address.hostAddress
                onPeerReceived(peerAddress!!)
                newPeer("con1")

            }
            // Se il messaggio è "con1" ci troviamo nel caso in cui un host ha risposto alla nostra notifica
            // quindi salviamo l'IP nella nostra lista
            else if (encryptedMessage.startsWith("con1")) {
                val peerAddress = packet.address.hostAddress
                onPeerReceived(peerAddress!!)

            // Nel caso non ci sia "con" nel messaggio ci troviamo nel caso base
            // quindi mettiamo il messaggio nella lista dei messaggi ricevuti
            } else if (!decryptedMessage.startsWith("con")) {
                Log.d("UDPClient", "Received: $decryptedMessage::$receivedHash")
                onMessageReceived(decryptedMessage)
            }
        }
    } catch (e: Exception) {
        Log.e("UDPClient", "Error receiving message: ${e.message}")
    }
}