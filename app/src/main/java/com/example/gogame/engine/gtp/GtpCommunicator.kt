package com.example.gogame.engine.gtp

interface GtpCommunicator {
    fun sendCommand(command: GtpCommand): GtpResponse
    fun sendCommandAsync(command: GtpCommand, callback: (GtpResponse) -> Unit)
    fun close()
}
