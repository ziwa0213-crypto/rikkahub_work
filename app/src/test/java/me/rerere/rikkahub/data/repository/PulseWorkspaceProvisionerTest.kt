package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PulseWorkspaceProvisionerTest {
    @Test
    fun provisionsMissingTemplatesWithoutReplacingExistingFiles() = runTest {
        val files = mutableMapOf("USER.md" to "custom user preferences")

        val first = PulseWorkspaceProvisioner.provision { path, content ->
            if (path in files) false else { files[path] = content; true }
        }

        assertEquals(5, first.size)
        assertEquals("custom user preferences", files["USER.md"])
        assertTrue(files.keys.containsAll(PulseWorkspaceProvisioner.templates.keys))

        val second = PulseWorkspaceProvisioner.provision { path, content ->
            if (path in files) false else { files[path] = content; true }
        }
        assertTrue(second.isEmpty())
        assertFalse(files["README.md"].isNullOrBlank())
    }
}
