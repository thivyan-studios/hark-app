package com.thivyanstudios.hark.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.os.Build
import android.provider.Settings

object HarkLog {
    private const val TAG = "HarkLog"
    private const val LOG_FILE_NAME = "hark_session_log.txt"
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE_NAME)
        if (logFile?.exists() == true) {
            logFile?.delete()
        }
        logFile?.createNewFile()
        i(TAG, "Logger initialized. New session started.")
        logSystemInfo(context)
    }

    private fun logSystemInfo(context: Context) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val bluetoothStatus = when {
            bluetoothAdapter == null -> "Not Supported"
            bluetoothAdapter.isEnabled -> "Enabled"
            else -> "Disabled"
        }

        val info = """
            --- System Info ---
            Device: ${Build.MANUFACTURER} ${Build.MODEL}
            Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            Bluetooth Status: $bluetoothStatus
            Airplane Mode: ${if (isAirplaneModeOn(context)) "On" else "Off"}
            -------------------
        """.trimIndent()
        i(TAG, info)
    }

    private fun isAirplaneModeOn(context: Context): Boolean {
        return Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        writeToLogFile("DEBUG", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        writeToLogFile("INFO", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        val fullMessage = if (throwable != null) {
            "$message\n${Log.getStackTraceString(throwable)}"
        } else {
            message
        }
        writeToLogFile("ERROR", tag, fullMessage)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        writeToLogFile("WARN", tag, message)
    }

    @Synchronized
    private fun writeToLogFile(level: String, tag: String, message: String) {
        val file = logFile ?: return
        try {
            val timestamp = dateFormat.format(Date())
            val logEntry = "[$timestamp] $level/$tag: $message\n"
            FileOutputStream(file, true).use {
                it.write(logEntry.toByteArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }

    fun getLogFile(): File? {
        return if (logFile?.exists() == true) logFile else null
    }
}
