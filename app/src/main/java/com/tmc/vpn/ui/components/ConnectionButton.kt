package com.tmc.vpn.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tmc.vpn.ui.theme.TmcColors

/** UI-only control. The existing controller owns all connect/disconnect actions. */
@Composable
fun TmcConnectionButton(
    connected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val action = if (connected) "Desconectar VPN" else "Conectar VPN"
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(156.dp).semantics { contentDescription = action },
        shape = CircleShape,
        border = BorderStroke(3.dp, TmcColors.Accent),
        colors = ButtonDefaults.buttonColors(
            containerColor = TmcColors.SurfaceElevated,
            contentColor = Color.White,
            disabledContainerColor = TmcColors.Surface,
            disabledContentColor = TmcColors.TextSecondary
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(if (connected) "DESCONECTAR" else "CONECTAR")
        }
    }
}
