package dev.metis.agent.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.metis.agent.R
import dev.metis.agent.domain.storage.MemoryOrigin

@Composable
internal fun MemoryOriginFilter(origin: MemoryOrigin?, onFilter: (MemoryOrigin?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(R.string.memory_filter, stringResource(originLabel(origin))))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            listOf(null, MemoryOrigin.EXPLICIT, MemoryOrigin.DERIVED).forEach { choice ->
                DropdownMenuItem(text = { Text(stringResource(originLabel(choice))) },
                    onClick = { expanded = false; onFilter(choice) })
            }
        }
    }
}

private fun originLabel(origin: MemoryOrigin?) = when (origin) {
    null -> R.string.memory_filter_all
    MemoryOrigin.EXPLICIT -> R.string.memory_filter_explicit
    MemoryOrigin.DERIVED -> R.string.memory_filter_derived
}
