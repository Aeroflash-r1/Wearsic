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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import androidx.compose.ui.tooling.preview.Preview
import com.example.BuildConfig
import com.example.network.model.ConnectionTestState
import com.example.ui.components.WearsicScreenHeader
import com.example.ui.theme.WearsicBlack
import com.example.ui.theme.WearsicError
import com.example.ui.theme.WearsicLavenderContainer
import com.example.ui.theme.WearsicSuccess
import com.example.ui.theme.WearsicSurface
import com.example.ui.theme.WearsicSurfaceActive
import com.example.ui.theme.WearsicSurfaceBorderSubtle
import com.example.ui.theme.WearsicTextMuted
import com.example.ui.theme.WearsicTextPrimary
import com.example.ui.theme.WearsicTextPrimaryDark
import com.example.ui.theme.WearsicTextSecondary
import com.example.ui.theme.WearsicTheme
import com.example.ui.theme.WearsicVibrantLavender

import com.example.ui.util.wearsicRotaryScroll

@Composable
fun SettingsScreen(
    serverUrl: String = "",
    connectionTestState: ConnectionTestState = ConnectionTestState.Idle,
    onServerUrlChanged: (String) -> Unit = {},
    onTestConnection: (String) -> Unit = {},
    cacheLimitMb: Int = 32,
    onCacheLimitChanged: (Int) -> Unit = {},
    onCleanCache: (onResult: (Long) -> Unit) -> Unit = { onResult -> onResult(0L) },
    onClearDownloads: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val serverPresets = listOf(
        "http://10.0.2.2:8080",
        "http://192.168.1.100:8080",
        "https://tailscale-termux.tail702ad8.ts.net"
    )

    val cacheLimits = listOf(16, 32, 64, 128)
    var currentCacheLimitIndex by remember {
        val idx = cacheLimits.indexOf(cacheLimitMb)
        mutableIntStateOf(if (idx >= 0) idx else 1)
    }

    var cacheCleanedMessage by remember { mutableStateOf<String?>(null) }
    var isCleaningCache by remember { mutableStateOf(false) }
    var showClearDownloadsConfirm by remember { mutableStateOf(false) }
    var downloadsClearedMessage by remember { mutableStateOf<String?>(null) }

    val listState = rememberScalingLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    ScreenScaffold(
        scrollState = listState,
        modifier = modifier
            .fillMaxSize()
            .background(WearsicBlack)
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .wearsicRotaryScroll(listState),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            item {
                WearsicScreenHeader(
                    title = "Settings",
                    subtitle = "Server & Storage"
                )
            }

            // Server URL Input Field (Fully Keyboard-enabled)
            item {
                var isFocused by remember { mutableStateOf(false) }
                var typedUrl by remember(serverUrl) { mutableStateOf(serverUrl) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(if (isFocused) WearsicSurfaceActive else WearsicSurface)
                        .border(
                            1.dp,
                            if (isFocused) WearsicVibrantLavender else WearsicSurfaceBorderSubtle,
                            CircleShape
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("settings_server_url_container"),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Dns,
                            contentDescription = "Server URL Icon",
                            tint = if (isFocused) WearsicVibrantLavender else WearsicTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Server URL",
                                color = WearsicTextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (typedUrl.isEmpty()) {
                                    Text(
                                        text = "Enter URL...",
                                        color = WearsicTextMuted,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }

                                BasicTextField(
                                    value = typedUrl,
                                    onValueChange = {
                                        typedUrl = it
                                        onServerUrlChanged(it)
                                    },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = WearsicTextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    cursorBrush = SolidColor(WearsicVibrantLavender),
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            keyboardController?.hide()
                                            onServerUrlChanged(typedUrl)
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { focusState ->
                                            isFocused = focusState.isFocused
                                        }
                                        .testTag("settings_server_url")
                                )
                            }
                        }

                        if (typedUrl.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear URL",
                                tint = WearsicTextSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        typedUrl = ""
                                        onServerUrlChanged("")
                                    }
                                    .testTag("settings_server_url_clear")
                            )
                        }
                    }
                }
            }

            // Quick-fill Preset Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(WearsicSurface)
                            .border(1.dp, WearsicSurfaceBorderSubtle, CircleShape)
                            .clickable {
                                onServerUrlChanged(serverPresets[0])
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Emulator", color = WearsicTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(WearsicSurface)
                            .border(1.dp, WearsicSurfaceBorderSubtle, CircleShape)
                            .clickable {
                                onServerUrlChanged(serverPresets[1])
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Local Lan", color = WearsicTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(WearsicSurface)
                            .border(1.dp, WearsicSurfaceBorderSubtle, CircleShape)
                            .clickable {
                                onServerUrlChanged(serverPresets[2])
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cloud", color = WearsicTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Test Connection Button
            item {
                when (connectionTestState) {
                    is ConnectionTestState.Idle -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CircleShape)
                                .background(WearsicVibrantLavender)
                                .clickable { onTestConnection(serverUrl) }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("settings_test_connection"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.NetworkCheck,
                                    contentDescription = "Test Connection",
                                    tint = WearsicTextPrimaryDark,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Test Connection",
                                    color = WearsicTextPrimaryDark,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    is ConnectionTestState.Testing -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CircleShape)
                                .background(WearsicSurface)
                                .border(1.dp, WearsicVibrantLavender, CircleShape)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("settings_test_connection_testing"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.HourglassEmpty,
                                    contentDescription = "Testing",
                                    tint = WearsicVibrantLavender,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Connecting...",
                                    color = WearsicTextPrimary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    is ConnectionTestState.Success -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CircleShape)
                                .background(WearsicSurface)
                                .border(1.dp, WearsicSuccess, CircleShape)
                                .clickable { onTestConnection(serverUrl) }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("settings_test_connection_success"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Success",
                                    tint = WearsicSuccess,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Connected (${connectionTestState.version})",
                                    color = WearsicSuccess,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    is ConnectionTestState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CircleShape)
                                .background(WearsicSurface)
                                .border(1.dp, WearsicError, CircleShape)
                                .clickable { onTestConnection(serverUrl) }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("settings_test_connection_error"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = "Failed",
                                    tint = WearsicError,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Failed: ${connectionTestState.message}",
                                    color = WearsicError,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Cache Limit Pill
            item {
                val currentLimit = cacheLimits[currentCacheLimitIndex]
                SettingsPillItem(
                    title = "Cache Limit: ${currentLimit}MB",
                    subtitle = "Tap to cycle limit",
                    icon = Icons.Rounded.SdStorage,
                    onClick = {
                        val nextIdx = (currentCacheLimitIndex + 1) % cacheLimits.size
                        currentCacheLimitIndex = nextIdx
                        onCacheLimitChanged(cacheLimits[nextIdx])
                    },
                    testTag = "settings_cache_limit"
                )
            }

            // Clean Music Cache Pill
            item {
                SettingsPillItem(
                    title = when {
                        isCleaningCache -> "Cleaning Cache..."
                        cacheCleanedMessage != null -> cacheCleanedMessage!!
                        else -> "Clean Music Cache"
                    },
                    subtitle = when {
                        isCleaningCache -> "Deleting temp streaming files"
                        cacheCleanedMessage != null -> "Cache cleared"
                        else -> "Wipe temp streaming cache"
                    },
                    icon = Icons.Rounded.CleaningServices,
                    iconTint = if (cacheCleanedMessage != null) WearsicSuccess else WearsicVibrantLavender,
                    onClick = {
                        if (!isCleaningCache) {
                            isCleaningCache = true
                            cacheCleanedMessage = null
                            onCleanCache { freedBytes ->
                                val freedMb = freedBytes / (1024.0 * 1024.0)
                                cacheCleanedMessage = if (freedMb > 0) {
                                    String.format("Cache Cleaned (Freed %.1fMB)", freedMb)
                                } else {
                                    "Cache Cleaned (0 MB)"
                                }
                                isCleaningCache = false
                            }
                        }
                    },
                    testTag = "settings_clean_cache"
                )
            }

            // Clear Downloads Pill
            item {
                if (showClearDownloadsConfirm) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(WearsicError.copy(alpha = 0.2f))
                            .border(1.dp, WearsicError, CircleShape)
                            .clickable {
                                onClearDownloads()
                                showClearDownloadsConfirm = false
                                downloadsClearedMessage = "Downloads Cleared"
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("confirm_clear_downloads"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Confirm Delete All Downloads",
                            color = WearsicError,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    SettingsPillItem(
                        title = if (downloadsClearedMessage != null) downloadsClearedMessage!! else "Clear Downloads",
                        subtitle = if (downloadsClearedMessage != null) "All offline files deleted" else "Remove offline files",
                        icon = Icons.Rounded.Delete,
                        iconTint = if (downloadsClearedMessage != null) WearsicError else WearsicVibrantLavender,
                        onClick = {
                            showClearDownloadsConfirm = true
                        },
                        testTag = "settings_clear_downloads"
                    )
                }
            }

            // Build Info Footer
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Wearsic v${BuildConfig.VERSION_NAME}",
                        color = WearsicTextMuted,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Wear OS 6 • Galaxy Watch7",
                        color = WearsicTextMuted,
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Bottom Spacing
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SettingsPillItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: androidx.compose.ui.graphics.Color = WearsicVibrantLavender,
    testTag: String = "settings_pill"
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(WearsicSurface)
            .border(1.dp, WearsicSurfaceBorderSubtle, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(WearsicLavenderContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = WearsicTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = WearsicTextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
fun SettingsScreenPreview() {
    WearsicTheme {
        SettingsScreen()
    }
}
