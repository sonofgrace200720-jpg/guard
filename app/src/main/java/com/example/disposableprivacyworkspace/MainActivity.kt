package com.example.disposableprivacyworkspace

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.example.disposableprivacyworkspace.browser.PrivacyBrowser
import com.example.disposableprivacyworkspace.network.LocalDohProxy
import com.example.disposableprivacyworkspace.sandbox.*
import com.example.disposableprivacyworkspace.tor.TorManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var manager: SandboxManager
    private lateinit var torManager: TorManager
    private lateinit var browser: PrivacyBrowser
    private lateinit var dohProxy: LocalDohProxy
    private val proxyExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = SandboxManager(this)
        torManager = TorManager(this)
        browser = PrivacyBrowser(proxyExecutor)
        dohProxy = LocalDohProxy(lifecycleScope)
        lifecycleScope.launch { manager.cleanupOrphaned() }
        setContent { App() }
    }

    override fun onDestroy() {
        browser.clearSessionCookies()
        browser.clearProxy()
        dohProxy.stop()
        proxyExecutor.shutdownNow()
        super.onDestroy()
    }

    @Composable
    fun App() {
        var screen by remember { mutableStateOf("home") }
        var mode by remember { mutableStateOf(NetworkMode.TOR) }
        var session by remember { mutableStateOf<SandboxSession?>(null) }
        var destroyDialog by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        MaterialTheme(colorScheme = darkColorScheme()) {
            when (screen) {
                "home" -> Home({ screen = "choose" }, { screen = "settings" })
                "choose" -> Choose(mode, { mode = it }, {
                    lifecycleScope.launch {
                        error = null
                        val s = manager.create(mode)
                        session = s
                        if (mode == NetworkMode.TOR) {
                            torManager.start()
                            repeat(90) { if (torManager.isOperational()) return@repeat; delay(500) }
                            if (!torManager.isOperational()) {
                                torManager.stop(); manager.destroy(s.id); session = null
                                error = "Tor could not establish an operational circuit. No direct-network fallback was used."
                            } else {
                                session = manager.markActive(s.id, mode)
                                screen = "workspace"
                            }
                        } else {
                            val port = dohProxy.start()
                            session = manager.markActive(s.id, mode)
                            screen = "workspace"
                            error = if (port > 0) null else "Cloudflare DNS proxy could not start."
                        }
                    }
                }, error)
                "workspace" -> Workspace(session!!, { destroyDialog = true }, { screen = "browser" })
                "browser" -> BrowserScreen(session!!.networkMode, { screen = "workspace" })
                "settings" -> Settings { screen = "home" }
            }
            if (destroyDialog && session != null) {
                AlertDialog(
                    onDismissRequest = { destroyDialog = false },
                    title = { Text("Destroy this session?") },
                    text = { Text("All application-managed data created during this session will be removed. This cannot guarantee physical flash-bit erasure.") },
                    confirmButton = { TextButton(onClick = {
                        destroyDialog = false
                        lifecycleScope.launch {
                            manager.destroy(session!!.id)
                            browser.clearSessionCookies(); browser.clearProxy(); dohProxy.stop()
                            torManager.stop()
                            session = null; screen = "home"
                        }
                    }) { Text("Destroy") } },
                    dismissButton = { TextButton(onClick = { destroyDialog = false }) { Text("Cancel") } }
                )
            }
        }
    }

    @Composable fun Home(onEnter: () -> Unit, onSettings: () -> Unit) = Page("Disposable Privacy Workspace") {
        Text("Temporary workspace • application-managed ephemeral storage")
        StatusCard("SANDBOX", "DESTROYED")
        StatusCard("NETWORK", "DISCONNECTED")
        Button(onClick = onEnter, Modifier.fillMaxWidth().height(56.dp)) { Text("ENTER SANDBOX") }
        OutlinedButton(onClick = onSettings, Modifier.fillMaxWidth()) { Text("Settings") }
        Text("Security boundary", style = MaterialTheme.typography.titleMedium)
        Text("This is not a virtual Android phone. Only application-managed data and supported in-app network traffic are controlled by this app.")
    }

    @Composable fun Choose(mode: NetworkMode, onMode: (NetworkMode) -> Unit, onStart: () -> Unit, error: String?) = Page("Choose Network Mode") {
        NetworkOption("🧅 Tor Mode", "The embedded Tor engine must establish a circuit before the session becomes active. The disposable browser uses the Tor SOCKS endpoint without direct fallback.", mode == NetworkMode.TOR) { onMode(NetworkMode.TOR) }
        NetworkOption("☁ Cloudflare Privacy Mode", "The disposable browser uses a local proxy whose target DNS lookups are resolved through Cloudflare DNS-over-HTTPS. This is DNS privacy, not a general-purpose VPN.", mode == NetworkMode.CLOUDFLARE) { onMode(NetworkMode.CLOUDFLARE) }
        Button(onClick = onStart, Modifier.fillMaxWidth()) { Text("START SANDBOX") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }

    @Composable fun Workspace(s: SandboxSession, onDestroy: () -> Unit, onBrowser: () -> Unit) = Page("PRIVATE WORKSPACE") {
        StatusCard(if (s.networkMode == NetworkMode.TOR) "🧅 TOR MODE" else "☁ CLOUDFLARE MODE", "CONNECTED")
        Text("Storage: EPHEMERAL")
        Text("Session: ${s.id}", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onBrowser, Modifier.fillMaxWidth()) { Text("BROWSER") }
        Button(onClick = onDestroy, Modifier.fillMaxWidth()) { Text("DESTROY SANDBOX") }
        Text("Only the in-app browser is covered by the supported network privacy boundary. Other Android apps are outside this application's sandbox.")
    }

    @Composable fun BrowserScreen(mode: NetworkMode, onBack: () -> Unit) {
        var ready by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = onBack) { Text("Back") }
                Text(if (mode == NetworkMode.TOR) "TOR BROWSER" else "CLOUDFLARE DNS BROWSER", modifier = Modifier.padding(12.dp))
            }
            AndroidView(factory = { ctx -> WebView(ctx).also { w -> browser.configure(w); if (mode == NetworkMode.TOR) { browser.configureTorProxy(torManager.socksPort()) { ready = it }; w.loadUrl("https://check.torproject.org/") } else { browser.configureHttpProxy(dohProxy.start()) { ready = it }; w.loadUrl("https://1.1.1.1/") } } }, modifier = Modifier.fillMaxSize())
            if (!ready) Text("Network proxy is not ready; direct fallback is disabled.")
        }
    }

    @Composable fun Settings(onBack: () -> Unit) = Page("Settings") {
        Text("Network", style = MaterialTheme.typography.titleLarge)
        Text("Tor: embedded engine + SOCKS browser routing. Cloudflare: browser DNS-over-HTTPS proxy.")
        Text("Sandbox", style = MaterialTheme.typography.titleLarge)
        Text("Automatic timeout • ephemeral storage • orphan cleanup")
        Text("Privacy", style = MaterialTheme.typography.titleLarge)
        Text("Analytics OFF • crash reporting OFF • persistent browsing history OFF")
        Button(onClick = onBack) { Text("Back") }
    }

    @Composable fun NetworkOption(title: String, desc: String, selected: Boolean, onClick: () -> Unit) = Card(onClick = onClick, Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Row { RadioButton(selected, onClick); Text(title, Modifier.padding(top = 12.dp)) }; Text(desc, Modifier.padding(start = 48.dp)) } }
    @Composable fun StatusCard(a: String, b: String) = Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Row(Modifier.padding(16.dp), Arrangement.SpaceBetween) { Text(a); Text(b) } }
    @Composable fun Page(title: String, content: @Composable ColumnScope.() -> Unit) = Surface(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = { Text(title, style = MaterialTheme.typography.headlineMedium); content() }) }
}
