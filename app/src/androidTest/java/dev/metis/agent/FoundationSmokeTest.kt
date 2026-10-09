package dev.metis.agent

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun shellLaunchesAndSurvivesRecreation() {
        assertFoundationVisible()
        composeRule.activityRule.scenario.recreate()
        assertFoundationVisible()
    }

    @Test
    fun installedAppHasNoInternetAndOnlyNotificationRuntimePermission() {
        val activity = composeRule.activity
        val packageManager = activity.packageManager
        @Suppress("DEPRECATION")
        val permissions = packageManager.getPackageInfo(
            activity.packageName,
            PackageManager.GET_PERMISSIONS,
        ).requestedPermissions.orEmpty()
        assertFalse(permissions.contains(Manifest.permission.INTERNET))
        permissions.filter { it != Manifest.permission.POST_NOTIFICATIONS }.forEach { permission ->
            val info = runCatching { packageManager.getPermissionInfo(permission, 0) }.getOrNull() ?: return@forEach
            @Suppress("DEPRECATION")
            val protection = info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
            assertFalse(protection == PermissionInfo.PROTECTION_DANGEROUS)
        }
    }

    @Test
    fun installedAppDisablesBackupAndCleartext() {
        val flags = composeRule.activity.applicationInfo.flags
        assertEquals(0, flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertEquals(0, flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC)
    }

    private fun assertFoundationVisible() {
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.app_name))
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.scaffold_status))
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.privacy_status))
            .performScrollTo().assertIsDisplayed()
    }
}
