@file:Suppress("MagicNumber") // Material-style vector coordinates.

package dev.metis.agent.presentation.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object MetisIcons {
    val Microphone: ImageVector = ImageVector.Builder("Microphone", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 14f)
            curveTo(13.66f, 14f, 15f, 12.66f, 15f, 11f)
            verticalLineTo(5f)
            curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
            reflectiveCurveTo(9f, 3.34f, 9f, 5f)
            verticalLineTo(11f)
            curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f)
            close()
            moveTo(17f, 11f)
            curveTo(17f, 13.76f, 14.76f, 16f, 12f, 16f)
            reflectiveCurveTo(7f, 13.76f, 7f, 11f)
            horizontalLineTo(5f)
            curveTo(5f, 14.53f, 7.61f, 17.43f, 11f, 17.92f)
            verticalLineTo(21f)
            horizontalLineTo(13f)
            verticalLineTo(17.92f)
            curveTo(16.39f, 17.43f, 19f, 14.53f, 19f, 11f)
            close()
        }
    }.build()
    val Stop: ImageVector = ImageVector.Builder("Stop", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 6f); horizontalLineTo(18f); verticalLineTo(18f); horizontalLineTo(6f); close()
        }
    }.build()
    val Send: ImageVector = ImageVector.Builder("Send", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(11f, 20f); verticalLineTo(7.83f); lineTo(5.41f, 13.41f)
            lineTo(4f, 12f); lineTo(12f, 4f); lineTo(20f, 12f); lineTo(18.59f, 13.41f)
            lineTo(13f, 7.83f); verticalLineTo(20f); close()
        }
    }.build()
}
