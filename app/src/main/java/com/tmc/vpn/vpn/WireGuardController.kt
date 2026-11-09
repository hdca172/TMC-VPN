package com.tmc.vpn.vpn

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/**
 * Real WireGuard userspace backend. Profiles are kept only in RAM in this build.
 * Never log, publish or bundle a WireGuard private key.
 */
class WireGuardController(context: Context) {
    private val backend = GoBackend(context.applicationContext)
    private var config: Config? = null
    private val tunnel = object : Tunnel {
        override fun getName(): String = "tmc"
        override fun onStateChange(newState: Tunnel.State) = Unit
    }
    var status: TunnelStatus = TunnelStatus.DISCONNECTED
        private set

    fun importProfile(raw: String): Result<Unit> = runCatching {
        require(raw.length <= 65536) { "Perfil demasiado grande" }
        val parsed = Config.parse(ByteArrayInputStream(raw.toByteArray(StandardCharsets.UTF_8)))
        require(parsed.peers.isNotEmpty()) { "El perfil no contiene peers" }
        config = parsed
        status = TunnelStatus.DISCONNECTED
    }

    fun hasProfile(): Boolean = config != null

    fun connect(): Result<Unit> = runCatching {
        val profile = requireNotNull(config) { "Importa primero un perfil WireGuard válido" }
        status = TunnelStatus.CONNECTING
        try {
            val actual = backend.setState(tunnel, Tunnel.State.UP, profile)
            check(actual == Tunnel.State.UP) { "WireGuard no activó el túnel" }
            status = TunnelStatus.CONNECTED
        } catch (e: Exception) {
            status = TunnelStatus.ERROR
            throw e
        }
    }

    fun disconnect(): Result<Unit> = runCatching {
        try {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
            status = TunnelStatus.DISCONNECTED
        } catch (e: Exception) {
            status = TunnelStatus.ERROR
            throw e
        }
    }
}
