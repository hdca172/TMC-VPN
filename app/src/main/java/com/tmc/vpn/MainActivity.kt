package com.tmc.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.tmc.vpn.data.ServerCatalog
import com.tmc.vpn.data.ServerStatus

private val Navy = Color(0xFF081426)
private val Panel = Color(0xFF162943)
private val Cyan = Color(0xFF21D9F5)
private val Green = Color(0xFF4CE4B2)
private val Red = Color(0xFFF04A64)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { UltimateApp(this) }
    }
}

private val pages = listOf("Inicio", "Servidores", "Diagnóstico", "Estadísticas", "Seguridad", "Ajustes", "Cuenta")

@Composable
private fun UltimateApp(context: Context) {
    var page by remember { mutableIntStateOf(0) }
    var showWelcome by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var favorites by remember { mutableStateOf(setOf<String>()) }
    var notifications by remember { mutableStateOf(true) }
    val network = remember(refresh) { networkType(context) }
    val servers = remember { ServerCatalog.initial() }

    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Navy, surface = Panel)) {
        Column(Modifier.fillMaxSize().background(Navy).padding(16.dp)) {
            if (showWelcome) {
                Spacer(Modifier.weight(1f))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.tmc_shield), contentDescription = "TMC VPN", modifier = Modifier.size(166.dp))
                    Text("TMC VPN ULTIMATE", fontWeight = FontWeight.Black, fontSize = 26.sp)
                    Text("Protección, control y transparencia", color = Cyan)
                }
                Spacer(Modifier.height(32.dp))
                Text("Bienvenido. Configura un servidor WireGuard autorizado para activar el túnel VPN.", textAlign = TextAlign.Center, color = Color.LightGray)
                Spacer(Modifier.weight(1f))
                Button(onClick = { showWelcome = false }, modifier = Modifier.fillMaxWidth()) { Text("Comenzar") }
                Spacer(Modifier.height(16.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.tmc_shield), contentDescription = "Logo", modifier = Modifier.size(62.dp))
                    Column {
                        Text("TMC VPN", fontSize = 25.sp, fontWeight = FontWeight.Black)
                        Text("ULTIMATE 3.0 • VPN sin configurar", fontSize = 11.sp, color = Color.LightGray)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(pages[page], fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Cyan)
                Spacer(Modifier.height(10.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (page) {
                        0 -> {
                            Spacer(Modifier.height(12.dp))
                            Image(painterResource(R.drawable.tmc_shield), contentDescription = null, modifier = Modifier.size(150.dp))
                            Text("DESCONECTADO", color = Red, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("No hay túnel VPN activo", color = Color.LightGray)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Conexión inteligente — no disponible") }
                            Info("Servidor seleccionado", "Ninguno. Principal y respaldo sin configurar.")
                            Info("Conexión de red", network)
                            Info("Protección VPN", "Inactiva. No se cifran datos mediante TMC VPN.")
                        }
                        1 -> {
                            Text("Infraestructura VPN", color = Color.LightGray)
                            servers.forEach { server ->
                                Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(server.name, fontWeight = FontWeight.Bold, color = Green)
                                        Text(if (server.status == ServerStatus.READY) "Configurado" else "Pendiente de configurar")
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (server.id in favorites) "★ Favorito" else "☆ Añadir favorito", modifier = Modifier.clickable {
                                                favorites = if (server.id in favorites) favorites - server.id else favorites + server.id
                                            }, color = Cyan)
                                        }
                                    }
                                }
                            }
                            Info("Servidores personalizados", "Sin perfiles importados. La importación WireGuard se implementará al integrar el backend.")
                            Info("Selección automática", "No disponible sin servidores configurados y comprobados.")
                        }
                        2 -> {
                            Info("Tipo de red", network)
                            Button(onClick = { refresh++ }) { Text("Actualizar diagnóstico") }
                            Info("Conectividad", "La detección de transporte no garantiza acceso real a Internet.")
                            Info("DNS / latencia", "Pendiente de pruebas reales. No se muestran resultados inventados.")
                        }
                        3 -> {
                            Info("Tiempo conectado", "00:00:00")
                            Info("Tráfico VPN", "No disponible — no existe túnel activo")
                            Info("Velocidad VPN", "No medida")
                        }
                        4 -> {
                            Info("Estado de seguridad", "VPN inactiva")
                            Info("Kill switch", "Utiliza las opciones VPN siempre activa y bloquear conexiones sin VPN de Android cuando exista un túnel funcional.")
                            Info("DNS", "Sin DNS del túnel configurado")
                            Info("WireGuard", "Backend pendiente de integrar y verificar")
                        }
                        5 -> {
                            Info("Aplicación", "TMC VPN Ultimate V3.0")
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Preferir notificaciones", modifier = Modifier.weight(1f))
                                Switch(checked = notifications, onCheckedChange = { notifications = it })
                            }
                            Info("Nota", "La preferencia de notificaciones aún no modifica permisos del sistema.")
                            Button(onClick = { showWelcome = true }) { Text("Ver bienvenida") }
                        }
                        else -> {
                            Info("Cuenta", "No se requiere registro para esta versión.")
                            Info("Plan gratuito", "Interfaz disponible. Servidores todavía no aprovisionados.")
                            Info("Plan Pro", "Concepto futuro; no hay suscripciones ni pagos habilitados.")
                            Info("Privacidad", "No se solicitan credenciales ni se recopilan perfiles privados.")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFF29425F))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(0, 1, 2, 3).forEach { index ->
                        Text(pages[index], modifier = Modifier.clickable { page = index }.padding(vertical = 12.dp, horizontal = 4.dp), fontSize = 11.sp, color = if (page == index) Cyan else Color.LightGray)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(4, 5, 6).forEach { index ->
                        Text(pages[index], modifier = Modifier.clickable { page = index }.padding(vertical = 8.dp, horizontal = 12.dp), fontSize = 11.sp, color = if (page == index) Cyan else Color.LightGray)
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
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "Otra conexión"
    }
}
