package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Delete
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.WearsicScreenHeader
import com.example.ui.theme.WearsicGlassBorder
import com.example.ui.theme.WearsicGlassFill

@Composable
fun StorageStatsContent(
    autoCount: Int,
    autoMb: Double,
    manualCount: Int,
    manualMb: Double,
    streamCacheMb: Double,
    onPurgeStreamCache: () -> Unit,
    onClearAutoCached: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maxBar = maxOf(autoMb, manualMb, streamCacheMb, 1.0)
    val lavender = Color(0xFFD0BCFF)
    val purple = Color(0xFF8A5CF6)
    val deepPurple = Color(0xFF6C5CE7)

    ScalingLazyColumn(
        state = rememberScalingLazyListState(),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            WearsicScreenHeader(
                title = "Storage",
                subtitle = "%.1f MB total".format(autoMb + manualMb + streamCacheMb)
            )
        }

        items(listOf(
            Triple("Auto-saved songs", "$autoCount songs • %.1f MB".format(autoMb), autoMb to lavender),
            Triple("Manual downloads", "$manualCount songs • %.1f MB".format(manualMb), manualMb to purple),
            Triple("Stream cache", "temporary • %.1f MB".format(streamCacheMb), streamCacheMb to deepPurple)
        ), key = { it.first }) { (label, countText, pair) ->
            val (mb, color) = pair
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(label, color = Color(0xFFE6E1E5), fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(countText, color = Color(0xFFCAC4D0), fontSize = 10.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((mb / maxBar).toFloat().coerceIn(0.02f, 1f))
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }

        item {
            ActionRow(icon = androidx.compose.material.icons.Icons.Rounded.CleaningServices,
                label = "Purge stream cache", onClick = onPurgeStreamCache)
        }
        item {
            ActionRow(icon = androidx.compose.material.icons.Icons.Rounded.Delete,
                label = "Clear auto-saved ($autoCount)", onClick = onClearAutoCached)
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, WearsicGlassBorder, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFFD0BCFF), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.size(10.dp))
        Text(label, color = Color(0xFFE6E1E5), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}