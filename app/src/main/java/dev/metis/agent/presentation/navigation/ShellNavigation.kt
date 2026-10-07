package dev.metis.agent.presentation.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private val railWidth = 112.dp

@Composable
internal fun ShellNavigation(
    destination: ShellDestination, useRail: Boolean, onSelect: (ShellDestination) -> Unit,
) {
    if (useRail) {
        NavigationRail(Modifier.fillMaxHeight().width(railWidth)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ShellDestination.entries.forEach { item ->
                    NavigationRailItem(
                        selected = destination == item, onClick = { onSelect(item) },
                        icon = { Icon(item.icon(), contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                        modifier = Modifier.testTag("nav_${item.name}"),
                    )
                }
            }
        }
    } else {
        NavigationBar {
            ShellDestination.entries.forEach { item ->
                NavigationBarItem(
                    selected = destination == item, onClick = { onSelect(item) },
                    icon = { Icon(item.icon(), contentDescription = null) },
                    label = { Text(stringResource(item.label), style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.testTag("nav_${item.name}"),
                )
            }
        }
    }
}

private fun ShellDestination.icon(): ImageVector = when (this) {
    ShellDestination.TODAY -> Icons.Default.Home
    ShellDestination.PLAN -> Icons.Default.DateRange
    ShellDestination.AGENT -> Icons.AutoMirrored.Filled.Send
    ShellDestination.TIMELINE -> Icons.AutoMirrored.Filled.List
    ShellDestination.YOU -> Icons.Default.Person
}
