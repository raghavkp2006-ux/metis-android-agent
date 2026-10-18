package dev.metis.agent.testing

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import dev.metis.agent.BuildConfig

/** Runs before Application.onCreate and before any test fixtures or @Before callbacks. */
class IsolatedAndroidJUnitRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application {
        TestSafety.requireIsolated(context)
        return super.newApplication(cl, className, context)
    }

    override fun onCreate(arguments: Bundle) {
        TestSafety.requireIsolated(targetContext)
        check(context.packageName == "${TestSafety.TARGET}.test") { "REFUSED: unexpected test APK" }
        super.onCreate(arguments)
    }
}

object TestSafety {
    const val TARGET = "dev.metis.agent.capabilitytest"

    fun requireIsolated(context: Context) {
        check(BuildConfig.IS_CAPABILITY_TEST && context.packageName == TARGET &&
            context.applicationInfo.uid == android.os.Process.myUid()) {
            "REFUSED: instrumentation/destructive fixtures require the isolated capability-test UID"
        }
    }
}
