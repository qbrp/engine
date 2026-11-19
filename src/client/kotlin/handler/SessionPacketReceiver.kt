package org.lain.engine.client.handler

import org.lain.engine.client.GameSession
import org.lain.engine.client.transport.ClientTransport
import org.lain.engine.client.transport.registerClientReceiver
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet

class SessionPacketReceiver(private val transport: ClientTransport) {
    private val lock = Any()
    private val packets = BufferedTaskQueue()
    @Volatile
    private var session: GameSession? = null
    private var closed = false

    fun <P : Packet> registerGameSessionReceiver(endpoint: Endpoint<P>, executor: P.(GameSession) -> Unit) {
        synchronized(lock) {
            check(!closed) { "Приёмник пакетов сессии закрыт" }
            transport.registerClientReceiver(endpoint) {
                val packet = this
                packets.add(Task(endpoint.identifier) {
                    val session = session ?: return@Task
                    packet.executor(session)
                })
            }
        }
    }

    fun bind(session: GameSession) {
        synchronized(lock) {
            check(!closed) { "Приёмник пакетов сессии закрыт" }
            check(this.session == null) { "Игровая сессия уже привязана" }
            this.session = session
            packets.bind(session.dataPrepareTaskExecutor)
        }
    }

    fun close() {
        synchronized(lock) {
            closed = true
            session = null
            packets.close()
        }
    }
}
