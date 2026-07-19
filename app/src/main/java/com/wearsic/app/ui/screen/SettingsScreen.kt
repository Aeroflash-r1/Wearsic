package com.wearsic.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.wearsic.app.WearsicApplication
import com.wearsic.app.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as WearsicApplication
    val viewModel: SettingsViewModel = viewModel(
        factory = remember {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(app.settingsDataStore) as T
                }
            }
        }
    )

    val storedUrl by viewModel.backendUrl.collectAsStateWithLifecycle()
    var editedUrl by remember(storedUrl) { mutableStateOf(storedUrl) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Backend URL",
            style = MaterialTheme.typography.titleSmall,
        )

        BasicTextField(
            value = editedUrl,
            onValueChange = { editedUrl = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            singleLine = true,
        )

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = {
                val normalized = if (editedUrl.endsWith("/")) editedUrl else "$editedUrl/"
                viewModel.setBackendUrl(normalized)
                app.updateRepository(normalized)
                onSaved()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save & Go Back")
        }

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cancel")
        }
    }
}
