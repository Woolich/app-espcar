package com.example.esp32control

import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class TcpClient(private val listener: Listener?) {
    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onMessage(msg: String)
        fun onError(msg: String)
    }

    @Volatile private var socket: Socket? = null
    @Volatile private var writer: PrintWriter? = null
    private var readerThread: Thread? = null
    private val running = AtomicBoolean(false)

    fun connect(host: String, port: Int) {
        Thread {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 4000)
                s.tcpNoDelay = true
                socket = s
                writer = PrintWriter(BufferedWriter(OutputStreamWriter(s.getOutputStream())), true)
                running.set(true)
                listener?.onConnected()

                readerThread = Thread {
                    try {
                        val br = BufferedReader(InputStreamReader(s.getInputStream()))
                        while (running.get() && !s.isClosed) {
                            val line = br.readLine() ?: break
                            listener?.onMessage(line)
                        }
                    } catch (e: Exception) {
                        if (running.get()) listener?.onError("Read error: ${e.message}")
                    } finally {
                        disconnectInternal()
                    }
                }.apply { start() }

            } catch (e: Exception) {
                listener?.onError("Connect error: ${e.message}")
                disconnectInternal()
            }
        }.start()
    }

    fun sendLine(cmd: String) {
        Thread {
            try {
                writer?.let {
                    it.print(cmd)
                    if (!cmd.endsWith("\n")) it.print("\n")
                    it.flush()
                } ?: listener?.onError("No conectado")
            } catch (e: Exception) {
                listener?.onError("Send error: ${e.message}")
            }
        }.start()
    }

    fun disconnect() {
        running.set(false)
        Thread { disconnectInternal() }.start()
    }

    private fun disconnectInternal() {
        try { writer?.flush() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        writer = null
        socket = null
        listener?.onDisconnected()
    }
}
