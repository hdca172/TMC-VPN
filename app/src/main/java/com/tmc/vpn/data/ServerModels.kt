package com.tmc.vpn.data

enum class ServerRole { PRIMARY, BACKUP, CUSTOM }
enum class ServerStatus { NOT_CONFIGURED, READY, UNREACHABLE }

data class VpnServer(
    val id: String,
    val name: String,
    val role: ServerRole,
    val status: ServerStatus,
    val configUri: String? = null
)

/** No fabricated endpoints or bundled private keys. */
object ServerCatalog {
    fun initial(): List<VpnServer> = listOf(
        VpnServer("primary", "Servidor principal", ServerRole.PRIMARY, ServerStatus.NOT_CONFIGURED),
        VpnServer("backup", "Servidor de respaldo", ServerRole.BACKUP, ServerStatus.NOT_CONFIGURED)
    )
}
