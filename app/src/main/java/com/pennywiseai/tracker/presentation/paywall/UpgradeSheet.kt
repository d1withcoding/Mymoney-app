package com.pennywiseai.tracker.presentation.paywall

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Online purchases and license checks are disabled in the offline fork. */
@Composable
fun UpgradeSheet(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Offline build") },
        text = { Text("Pro features are available here. Purchases and online license checks are disabled.") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}
