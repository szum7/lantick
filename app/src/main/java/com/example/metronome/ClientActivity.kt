package com.example.metronome

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONObject
import android.os.SystemClock

private const val SERVICE_ID = "com.example.metronome"
private val STRATEGY = Strategy.P2P_STAR

class ClientActivity : AppCompatActivity() {

    private lateinit var connectionsClient: ConnectionsClient
    private var metronome: Metronome? = null
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client)

        statusText = findViewById(R.id.statusText)
        connectionsClient = Nearby.getConnectionsClient(this)

        startDiscovery()
    }

    private fun startDiscovery() {
        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        ).addOnSuccessListener {
            Log.i("Client", "Discovery started")
            statusText.text = "Discovering hosts..."
        }.addOnFailureListener {
            Log.e("Client", "Discovery failed: $it")
            statusText.text = "Discovery failed: $it"
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.i("Client", "Endpoint found: $endpointId")
            statusText.text = "Found host, requesting connection..."

            connectionsClient.requestConnection("ClientDevice", endpointId, connectionLifecycleCallback)
                .addOnFailureListener {
                    Log.e("Client", "Connection request failed: $it")
                    statusText.text = "Connection request failed: $it"
                }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.i("Client", "Endpoint lost: $endpointId")
            statusText.text = "Lost host connection"
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.i("Client", "Connection initiated with $endpointId")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                Log.i("Client", "Connected to host $endpointId")
                statusText.text = "Connected to host, waiting for start signal..."
            } else {
                Log.e("Client", "Connection failed with host $endpointId")
                statusText.text = "Connection failed"
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.i("Client", "Disconnected from host $endpointId")
            statusText.text = "Disconnected from host"
            metronome?.stop()
            metronome = null
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                val jsonStr = String(bytes, Charsets.UTF_8)
                try {
                    val json = JSONObject(jsonStr)
                    val startAt = json.getLong("startAt")
                    val bpm = json.getInt("bpm")
                    val receivedAt = SystemClock.elapsedRealtime()

                    Log.i("Client", "Received startAt=$startAt bpm=$bpm")

                    runOnUiThread {
                        statusText.text = "Starting metronome at $bpm BPM"
                        if (metronome == null) metronome = Metronome()
                        metronome?.startMetronomeAtHostTime(startAt, receivedAt, bpm)
                    }
                } catch (e: Exception) {
                    Log.e("Client", "Failed to parse start signal JSON", e)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }
}
