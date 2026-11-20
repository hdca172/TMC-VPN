package com.tmc.vpn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tmc.vpn.ui.theme.TmcColors

/** Presentational card; caller supplies real status and message. */
@Composable
fun TmcStatusCard(
    status: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TmcColors.Surface, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("ESTADO DE CONEXIÓN", style = MaterialTheme.typography.labelMedium, color = TmcColors.Accent)
        Text(status, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TmcColors.TextPrimary)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = TmcColors.TextSecondary)
    }
}
