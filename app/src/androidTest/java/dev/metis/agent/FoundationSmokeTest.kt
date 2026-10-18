package dev.metis.agent

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class FoundationSmokeTest {
    @get:Rule(order = 0) val isolatedState = IsolatedAppStateRule()
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @org.junit.Before fun enterRedesignedFrontend() { composeRule.enterFrontend() }


    @Test
    fun shellLaunchesAndSurvivesRecreation() {
        var stage = "initial launch"
        try {
            assertFoundationVisible()
            composeRule.activityRule.scenario.recreate()
            stage = "after recreation"
            assertFoundationVisible()
        } catch (error: Throwable) {
            android.util.Log.e("NAV_DIAGNOSTIC", "stage=$stage records=" + runBlocking {
                PersonalStorage.repository(composeRule.activity).database.records().recordCount()
            })
            android.util.Log.e("NAV_DIAGNOSTIC", "ui=" + composeRule.onRoot().printToString())
            throw error
        }
    }

    @Test
    fun installedAppHasNoInternetAndOnlyFeatureRuntimePermissions() {
        val activity = composeRule.activity
        val packageManager = activity.packageManager
        @Suppress("DEPRECATION")
        val permissions = packageManager.getPackageInfo(
            activity.packageName,
            PackageManager.GET_PERMISSIONS,
        ).requestedPermissions.orEmpty()
        assertFalse(permissions.contains(Manifest.permission.INTERNET))
        permissions.filter { it !in setOf(Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.RECORD_AUDIO) }.forEach { permission ->
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
        composeRule.onNodeWithText("METIS").assertIsDisplayed()
        composeRule.onNodeWithText("Temporary chat").assertIsDisplayed()
    }
}
