package com.example.telemetria_poente.service

import io.socket.client.IO
import io.socket.client.Socket

object SocketService {
    private var socket: Socket? = null

    fun connect(url: String) {
        socket = IO.socket(url)
        socket?.connect()
    }

    fun isConnected(): Boolean {
        return socket?.connected() ?: false
    }

    fun send(event: String, data: Any) {
        if (isConnected()) {
            socket?.emit(event, data)
        }
    }

    fun disconnect() {
        socket?.disconnect()
    }
}
