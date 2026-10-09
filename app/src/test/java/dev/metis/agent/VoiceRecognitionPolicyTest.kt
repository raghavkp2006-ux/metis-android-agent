package dev.metis.agent

import dev.metis.agent.domain.agent.VoiceRecognitionMode
import dev.metis.agent.domain.agent.VoiceRecognitionPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceRecognitionPolicyTest {
    @Test
    fun prefersOnDeviceAndNeverFallsBackOnlineWithoutOptIn() {
        assertEquals(VoiceRecognitionMode.ON_DEVICE, VoiceRecognitionPolicy.select(34, true, false))
        assertEquals(VoiceRecognitionMode.UNAVAILABLE, VoiceRecognitionPolicy.select(34, false, false))
        assertEquals(VoiceRecognitionMode.UNAVAILABLE, VoiceRecognitionPolicy.select(26, false, false))
        assertEquals(VoiceRecognitionMode.ONLINE_FALLBACK, VoiceRecognitionPolicy.select(34, false, true))
        assertEquals(VoiceRecognitionMode.ONLINE_FALLBACK, VoiceRecognitionPolicy.select(26, false, true))
    }
}
