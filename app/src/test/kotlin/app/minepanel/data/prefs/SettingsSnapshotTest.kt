package app.minepanel.data.prefs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSnapshotTest {

    @Test
    fun defaultsAreClosedAndPrivate() {
        val snapshot = SettingsSnapshot()
        assertFalse(snapshot.onboardingCompleted)
        assertFalse(snapshot.recordingPaused)
        assertFalse(snapshot.exportEnabled)
        assertFalse(snapshot.clipboardCopyEnabled)
        assertTrue(snapshot.autoConfirmEnabled)
        assertFalse(snapshot.fallbackNoticePending)
        assertTrue(snapshot.plugins.values.all { !it })
        assertNull(snapshot.modelFilePath)
        assertNull(snapshot.modelHash)
        assertNull(snapshot.baselineStartEpochDay)
        assertFalse(snapshot.remoteModelEnabled)
        assertTrue(snapshot.remoteBaseUrl.isEmpty())
        assertTrue(snapshot.remoteModelName.isEmpty())
        assertNull(snapshot.remoteApiKeyCipher)
        assertTrue(snapshot.geofencePlaces.isEmpty())
        assertTrue(snapshot.geofenceState.isEmpty())
    }
}
