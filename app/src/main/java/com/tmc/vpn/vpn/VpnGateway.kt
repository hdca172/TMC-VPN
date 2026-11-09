package com.tmc.vpn.vpn

/**
 * Boundary for the WireGuard tunnel implementation.
 * No VPN is claimed active until a backend reports an actual tunnel state.
 */
enum class TunnelStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

interface VpnGateway {
    val status: TunnelStatus
    suspend fun connect(profileId: String): Result<Unit>
    suspend fun disconnect(): Result<Unit>
}

class UnconfiguredVpnGateway : VpnGateway {
    override val status = TunnelStatus.DISCONNECTED
    override suspend fun connect(profileId: String): Result<Unit> =
        Result.failure(IllegalStateException("WireGuard backend and server not configured"))
    override suspend fun disconnect(): Result<Unit> = Result.success(Unit)
}
