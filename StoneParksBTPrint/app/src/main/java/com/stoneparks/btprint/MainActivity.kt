package com.stoneparks.btprint

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.work.*
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var btAdapter: BluetoothAdapter? = null
    private var device: BluetoothDevice? = null
    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null

    private lateinit var etPlate: EditText
    private lateinit var etMake: EditText
    private lateinit var etModel: EditText
    private lateinit var etDetails: EditText
    private lateinit var btnSelect: Button
    private lateinit var btnPrint: Button
    private lateinit var etWebhook: EditText
    private lateinit var btnSaveWebhook: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etPlate = findViewById(R.id.etPlate)
        etMake = findViewById(R.id.etMake)
        etModel = findViewById(R.id.etModel)
        etDetails = findViewById(R.id.etDetails)
        btnSelect = findViewById(R.id.btnSelect)
        btnPrint = findViewById(R.id.btnPrint)
        etWebhook = findViewById(R.id.etWebhook)
        btnSaveWebhook = findViewById(R.id.btnSaveWebhook)

        btAdapter = BluetoothAdapter.getDefaultAdapter()
        ensureBtPermissions()

        // load saved webhook
        etWebhook.setText(getSharedPreferences("sp", Context.MODE_PRIVATE).getString("webhook", ""))

        btnSaveWebhook.setOnClickListener {
            val url = etWebhook.text.toString().trim()
            getSharedPreferences("sp", Context.MODE_PRIVATE).edit().putString("webhook", url).apply()
            toast("Webhook saved")
        }

        btnSelect.setOnClickListener {
            val bonded = btAdapter?.bondedDevices?.toList().orEmpty()
            device = bonded.firstOrNull { it.name?.contains("ZQ511", ignoreCase = true) == true } ?: bonded.firstOrNull()
            if (device == null) toast("Pair the ZQ511 in Android settings first.")
            else { toast("Selected: ${device!!.name}"); connect() }
        }

        btnPrint.setOnClickListener {
            val ticket = Ticket(
                id = System.currentTimeMillis().toString().takeLast(6),
                type = "ticket",
                plate = etPlate.text.toString(),
                make = etMake.text.toString(),
                model = etModel.text.toString(),
                details = etDetails.text.toString(),
                createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            )
            val label = buildCpcl(ticket)
            send(label)

            // enqueue webhook if configured
            val url = etWebhook.text.toString().trim()
            if (url.isNotEmpty()) {
                enqueueWebhook(url, ticket)
            }
        }
    }

    private fun ensureBtPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            val needed = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            val missing = needed.any {
                ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing) ActivityCompat.requestPermissions(this, needed, 101)
        }
    }

    private fun connect() {
        val d = device ?: return
        try { socket?.close() } catch (_: Exception) {}
        try {
            val s = d.createRfcommSocketToServiceRecord(SPP_UUID)
            s.connect()
            socket = s
            out = s.outputStream
            toast("BT connected to ${d.name}")
            send("! U1 setvar \"power.sleep.enable\" \"off\"\n")
            send("! U1 setvar \"power.time.sleep\" \"300\"\n")
            send("~HS\n")
        } catch (e: Exception) {
            toast("Connect failed: ${e.message}")
            socket = null
            out = null
        }
    }

    private fun send(data: String) {
        try {
            if (out == null || socket == null || (socket?.isConnected != true)) connect()
            out?.write(data.toByteArray(Charsets.UTF_8))
            out?.flush()
            toast("Printed label")
        } catch (e: Exception) {
            toast("Send failed: ${e.message}")
            try { socket?.close() } catch (_: Exception) {}
            socket = null
            out = null
        }
    }

    private fun enqueueWebhook(url: String, ticket: Ticket) {
        val work = OneTimeWorkRequestBuilder<WebhookWorker>()
            .setInputData(workDataOf(
                "url" to url,
                "id" to ticket.id,
                "type" to ticket.type,
                "plate" to ticket.plate,
                "make" to ticket.make,
                "model" to ticket.model,
                "details" to ticket.details,
                "createdAt" to ticket.createdAt
            ))
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(this).enqueue(work)
        toast("Webhook queued")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun buildCpcl(t: Ticket): String {
        val safeDetails = (t.details ?: "-").replace(Regex("[\r\n]+"), " ")
        return listOf(
            "! 0 200 200 720 1",
            "PW 600",
            "LINE 20 120 580 120 2",
            "T 0 4 20 135 School Parking",
            "T 0 3 20 175 Ticket #" + t.id + " (" + t.type.uppercase(Locale.getDefault()) + ")",
            "T 0 2 20 210 Date: " + t.createdAt,
            "T 0 2 20 245 Plate: " + (t.plate.ifBlank { "-" }),
            "T 0 2 20 280 Make/Model: " + (t.make.ifBlank { "-" }) + " / " + (t.model.ifBlank { "-" }),
            "T 0 2 20 315 Details:",
            "T 0 2 20 350 " + safeDetails,
            "LINE 20 390 580 390 2",
            "T 0 2 20 680 Keep this ticket for your records.",
            "PRINT\n"
        ).joinToString("\n")
    }
}

data class Ticket(
    val id: String,
    val type: String,
    val plate: String,
    val make: String,
    val model: String,
    val details: String,
    val createdAt: String
)
