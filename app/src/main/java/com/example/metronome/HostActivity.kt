package com.example.metronome

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONObject
import android.os.SystemClock

private const val SERVICE_ID = "com.example.metronome"
private val STRATEGY = Strategy.P2P_STAR

class HostActivity : AppCompatActivity() {

    private lateinit var connectionsClient: ConnectionsClient
    private val connectedEndpoints = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_host)

        connectionsClient = Nearby.getConnectionsClient(this)
        startAdvertising()

        val btnStart = findViewById<Button>(R.id.btnStart)
        val inputBpm = findViewById<EditText>(R.id.inputBpm)

        btnStart.setOnClickListener {
            val bpmStr = inputBpm.text.toString()
            val bpm = bpmStr.toIntOrNull() ?: 120
            sendStartSignal(bpm)
        }
    }

    private fun startAdvertising() {
        connectionsClient.startAdvertising(
            "HostDevice",
            SERVICE_ID,
            connectionLifecycleCallback,
            AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        ).addOnSuccessListener {
            Log.i("Host", "Advertising started")
        }.addOnFailureListener {
            Log.e("Host", "Advertising failed: $it")
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpoints.add(endpointId)
                Log.i("Host", "Connected to $endpointId")
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            Log.i("Host", "Disconnected from $endpointId")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            // Host does not expect to receive payloads
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    fun sendStartSignal(bpm: Int, delayMs: Long = 5000L) {
        val startAt = SystemClock.elapsedRealtime() + delayMs
        val json = JSONObject()
        json.put("startAt", startAt)
        json.put("bpm", bpm)
        val bytes = json.toString().toByteArray(Charsets.UTF_8)

        connectedEndpoints.forEach { endpointId ->
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
        }

        Log.i("Host", "Sent start signal: bpm=$bpm, startAt=$startAt")
    }
}
