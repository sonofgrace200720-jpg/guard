package com.example.disposableprivacyworkspace.network

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import java.net.URLEncoder

/**
 * Session-local HTTP CONNECT proxy. DNS lookups for CONNECT targets are performed through
 * Cloudflare's DNS-over-HTTPS JSON endpoint. It is deliberately limited to the sandbox browser.
 */
class LocalDohProxy(private val scope: CoroutineScope) {
    private var server: ServerSocket? = null
    private var job: Job? = null
    @Volatile private var running = AtomicBoolean(false)

    fun start(): Int {
        if (running.get()) return server!!.localPort
        server = ServerSocket(0, 32, java.net.InetAddress.getByName("127.0.0.1"))
        running.set(true)
        job = scope.launch(Dispatchers.IO) {
            while (isActive && running.get()) {
                val client = runCatching { server?.accept() }.getOrNull() ?: break
                launch { handle(client) }
            }
        }
        return server!!.localPort
    }

    fun stop() {
        running.set(false)
        runCatching { server?.close() }
        job?.cancel()
        server = null
    }

    private suspend fun handle(client: Socket) {
        client.use { c ->
            c.soTimeout = 30_000
            val reader = BufferedReader(InputStreamReader(c.getInputStream(), StandardCharsets.ISO_8859_1))
            val requestLine = reader.readLine() ?: return
            // Consume headers.
            while (true) { if (reader.readLine().isNullOrEmpty()) break }
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            if (!parts[0].equals("CONNECT", true)) {
                c.getOutputStream().write("HTTP/1.1 501 Not Implemented\r\nConnection: close\r\n\r\n".toByteArray())
                return
            }
            val hostPort = parts[1].split(':', limit = 2)
            val host = hostPort[0]
            val port = hostPort.getOrNull(1)?.toIntOrNull() ?: 443
            val address = resolveViaDoh(host) ?: run {
                c.getOutputStream().write("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n".toByteArray())
                return
            }
            val upstream = runCatching { Socket().apply { connect(InetSocketAddress(address, port), 15_000); soTimeout = 30_000 } }.getOrNull() ?: run {
                c.getOutputStream().write("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n".toByteArray())
                return
            }
            upstream.use { u ->
                c.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                coroutineScope {
                    val a = launch(Dispatchers.IO) { c.getInputStream().copyTo(u.getOutputStream()); runCatching { u.shutdownOutput() } }
                    val b = launch(Dispatchers.IO) { u.getInputStream().copyTo(c.getOutputStream()); runCatching { c.shutdownOutput() } }
                    joinAll(a, b)
                }
            }
        }
    }

    private fun resolveViaDoh(host: String): String? {
        if (host.length > 253 || host.any { it.code < 32 }) return null
        val query = URLEncoder.encode(host, "UTF-8")
        val socket = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket("1.1.1.1", 443) as SSLSocket
        socket.use { s ->
            s.soTimeout = 10_000
            s.sslParameters = s.sslParameters.apply { serverNames = listOf(SNIHostName("cloudflare-dns.com")) }
            s.startHandshake()
            val request = "GET /dns-query?name=$query&type=A HTTP/1.1\r\nHost: cloudflare-dns.com\r\nAccept: application/dns-json\r\nConnection: close\r\n\r\n"
            s.outputStream.write(request.toByteArray(StandardCharsets.ISO_8859_1))
            val reader = BufferedReader(InputStreamReader(s.inputStream, StandardCharsets.ISO_8859_1))
            var line = reader.readLine() ?: return null
            while (line.isNotEmpty()) line = reader.readLine() ?: ""
            val body = reader.readText()
            return Regex("\\\"data\\\"\\s*:\\s*\\\"([0-9.]+)\\\"").find(body)?.groupValues?.get(1)
        }
    }
}
