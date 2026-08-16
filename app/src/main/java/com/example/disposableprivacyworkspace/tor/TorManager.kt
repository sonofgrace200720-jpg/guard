package com.example.disposableprivacyworkspace.tor

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.torproject.jni.TorService

/**
 * Owns the embedded Tor Android engine for one disposable session.
 * Tor reports ON only after its first circuit has been established.
 */
class TorManager(private val context: Context) {
    enum class State { STOPPED, STARTING, CONNECTED, STOPPING, ERROR }

    private val _state = MutableStateFlow(State.STOPPED)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var service: TorService? = null
    private var bound = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != TorService.ACTION_STATUS) return
            when (intent.getStringExtra(TorService.EXTRA_STATUS)) {
                TorService.STATUS_STARTING -> _state.value = State.STARTING
                TorService.STATUS_ON -> _state.value = State.CONNECTED
                TorService.STATUS_STOPPING -> _state.value = State.STOPPING
                TorService.STATUS_OFF -> _state.value = State.STOPPED
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as TorService.LocalBinder).service
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            bound = false
            _state.value = State.ERROR
        }
    }

    fun start() {
        if (_state.value == State.CONNECTED || _state.value == State.STARTING) return
        _state.value = State.STARTING
        val filter = IntentFilter(TorService.ACTION_STATUS)
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context)
            .registerReceiver(receiver, filter)

        val intent = Intent(context, TorService::class.java)
        intent.action = TorService.ACTION_START
        intent.putExtra(TorService.EXTRA_PACKAGE_NAME, context.packageName)
        context.startService(intent)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun socksPort(): Int = service?.socksPort ?: 0

    fun isOperational(): Boolean = _state.value == State.CONNECTED && socksPort() > 0

    fun stop() {
        _state.value = State.STOPPING
        runCatching {
            context.stopService(Intent(context, TorService::class.java))
            if (bound) context.unbindService(connection)
        }.also {
            bound = false
            service = null
            _state.value = State.STOPPED
            runCatching {
                androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context)
                    .unregisterReceiver(receiver)
            }
            // tor-android stores DataDirectory and cache under this private service directory.
            // Remove it after the daemon has stopped so Tor state does not survive the session.
            runCatching { context.getDir("TorService", Context.MODE_PRIVATE).deleteRecursively() }
        }
    }
}
