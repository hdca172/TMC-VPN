package com.tmc.vpn

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tmc.vpn.vpn.TunnelStatus
import com.tmc.vpn.vpn.WireGuardController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Navy = Color(0xFF081426)
private val Panel = Color(0xFF162943)
private val Cyan = Color(0xFF21D9F5)
private val Green = Color(0xFF4CE4B2)
private val Red = Color(0xFFF04A64)
private val pages = listOf("Inicio", "Servidores", "Diagnóstico", "Estadísticas", "Seguridad", "Ajustes", "Cuenta")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = WireGuardController(this)
        setContent { UltimateApp(this, controller) }
    }
}

@Composable
private fun UltimateApp(context: Context, controller: WireGuardController) {
    var page by remember { mutableIntStateOf(0) }
    var welcome by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var hasProfile by remember { mutableStateOf(false) }
    var tunnelStatus by remember { mutableStateOf(TunnelStatus.DISCONNECTED) }
    var message by remember { mutableStateOf("Sin perfil WireGuard") }
    var busy by remember { mutableStateOf(false) }
    var notificationPreference by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val network = remember(refresh) { networkType(context) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("No se pudo leer el archivo")
                controller.importProfile(text).getOrThrow()
            }.onSuccess {
                hasProfile = true
                tunnelStatus = TunnelStatus.DISCONNECTED
                message = "Perfil importado en memoria. Conecta solo a servidores de confianza."
            }.onFailure { message = "Error al importar: ${it.localizedMessage ?: "perfil inválido"}" }
        }
    }
    fun toggleConnection() {
        if (busy || !hasProfile) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (tunnelStatus == TunnelStatus.CONNECTED) controller.disconnect() else controller.connect()
            }
            tunnelStatus = controller.status
            message = result.fold(
                onSuccess = { if (tunnelStatus == TunnelStatus.CONNECTED) "Túnel WireGuard activado" else "Túnel desconectado" },
                onFailure = { "Error VPN: ${it.localizedMessage ?: "desconocido"}" }
            )
            busy = false
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Navy, surface = Panel)) {
        Column(Modifier.fillMaxSize().background(Navy).padding(16.dp)) {
            if (welcome) {
                Spacer(Modifier.weight(1f))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.tmc_shield), contentDescription = "TMC VPN", modifier = Modifier.size(166.dp))
                    Text("TMC VPN ULTIMATE", fontWeight = FontWeight.Black, fontSize = 26.sp)
                    Text("WireGuard • Conexión privada", color = Cyan)
                }
                Spacer(Modifier.height(28.dp))
                Text("Bienvenido. Importa un perfil WireGuard autorizado. Una VPN requiere conexión a Internet y no genera datos móviles gratuitos.", textAlign = TextAlign.Center, color = Color.LightGray)
                Spacer(Modifier.weight(1f))
                Button(onClick = { welcome = false }, modifier = Modifier.fillMaxWidth()) { Text("Comenzar") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.tmc_shield), contentDescription = "Logo", modifier = Modifier.size(62.dp))
                    Column {
                        Text("TMC VPN", fontSize = 25.sp, fontWeight = FontWeight.Black)
                        Text("ULTIMATE 3.1 • WireGuard", fontSize = 11.sp, color = Color.LightGray)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(pages[page], fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Cyan)
                Spacer(Modifier.height(10.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (page) {
                        0 -> {
                            Spacer(Modifier.height(14.dp))
                            Image(painterResource(R.drawable.tmc_shield), contentDescription = null, modifier = Modifier.size(140.dp))
                            val active = tunnelStatus == TunnelStatus.CONNECTED
                            Text(if (active) "TÚNEL ACTIVO" else "DESCONECTADO", color = if (active) Green else Red, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text(message, textAlign = TextAlign.Center, color = Color.LightGray)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { toggleConnection() }, enabled = hasProfile && !busy, modifier = Modifier.fillMaxWidth()) {
                                Text(if (busy) "Procesando…" else if (active) "Desconectar" else "Conectar WireGuard")
                            }
                            Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Importar perfil .conf") }
                            Info("Red", network)
                            Info("Estado", if (active) "WireGuard reporta túnel UP. Comprueba conectividad y DNS." else "No hay túnel activo.")
                        }
                        1 -> {
                            Info("Servidor principal", "No aprovisionado. Importa un perfil autorizado.")
                            Info("Servidor de respaldo", "No aprovisionado.")
                            Info("Servidores personalizados", if (hasProfile) "1 perfil importado en memoria" else "Ningún perfil importado")
                            Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Importar configuración WireGuard") }
                            Info("Favoritos y selección automática", "Pendientes de catálogo real y comprobaciones de disponibilidad.")
                        }
                        2 -> {
                            Info("Tipo de red", network)
                            Button(onClick = { refresh++ }) { Text("Actualizar") }
                            Info("Prueba de Internet", "La detección de transporte no garantiza conectividad.")
                            Info("DNS y latencia", "Pendiente de mediciones reales.")
                        }
                        3 -> {
                            Info("Estado del túnel", tunnelStatus.name)
                            Info("Tráfico", "Contadores y velocidades pendientes de integración.")
                        }
                        4 -> {
                            Info("WireGuard", if (hasProfile) "Perfil válido importado en memoria" else "Sin perfil")
                            Info("VPN siempre activa", "Configúrala en Ajustes de Android > VPN después de conectar correctamente.")
                            Info("Bloquear sin VPN", "Activa esta protección en Android si necesitas evitar tráfico fuera del túnel.")
                            Info("Privacidad", "Las claves del perfil no se guardan en disco por TMC VPN.")
                        }
                        5 -> {
                            Info("Versión", "3.1.0")
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Preferencia de notificaciones", modifier = Modifier.weight(1f))
                                Switch(checked = notificationPreference, onCheckedChange = { notificationPreference = it })
                            }
                            Info("Aviso", "Esta preferencia todavía no cambia los permisos de Android.")
                            Button(onClick = { welcome = true }) { Text("Pantalla de bienvenida") }
                        }
                        else -> {
                            Info("Cuenta", "Sin registro obligatorio.")
                            Info("Gratis", "Importación de un perfil WireGuard personal.")
                            Info("Pro", "Concepto futuro, sin suscripciones ni pagos.")
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFF29425F))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(0, 1, 2, 3).forEach { i ->
                        Text(pages[i], modifier = Modifier.clickable { page = i }.padding(8.dp), fontSize = 11.sp, color = if (page == i) Cyan else Color.LightGray)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(4, 5, 6).forEach { i ->
                        Text(pages[i], modifier = Modifier.clickable { page = i }.padding(8.dp), fontSize = 11.sp, color = if (page == i) Cyan else Color.LightGray)
                    }
                }
            }
        }
    }
}

@Composable private fun Info(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).background(Panel, RoundedCornerShape(15.dp)).padding(16.dp)) {
        Text(title, color = Green, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(detail, color = Color.White, fontSize = 14.sp)
    }
}

private fun networkType(context: Context): String {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Sin red activa detectada"
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN externa activa"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos móviles"
        else -> "Otra conexión"
    }
}
