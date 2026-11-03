package me.rerere.workspace.rootfs

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RootfsModelTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `rolling ids do not gain a version or trailing hyphen`() {
        for (name in listOf("archlinux", "void", "kalinethunter")) {
            assertEquals(name, RootfsId(name).distro)
            assertEquals("", RootfsId(name).version)
        }
        assertEquals("ubuntu", RootfsId("ubuntu-26.04").distro)
        assertEquals("26.04", RootfsId("ubuntu-26.04").version)
        assertEquals("tumbleweed", RootfsId("opensuse-tumbleweed").version)
    }

    @Test fun `architecture mapping never assumes arm64 for unknown abi`() {
        assertEquals(RootfsArch.ARM64, RootfsArch.current(arrayOf("arm64-v8a", "armeabi-v7a")))
        assertEquals(RootfsArch.AMD64, RootfsArch.current(arrayOf("x86_64", "x86")))
        assertNull(RootfsArch.current(arrayOf("armeabi-v7a")))
    }

    @Test fun `stamp absent malformed and unknown remain compatible`() {
        val dir = temp.newFolder()
        assertNull(RootfsStamp.readFrom(dir))
        File(dir, "etc").mkdir()
        val marker = File(dir, "etc/android-proot-release")
        marker.writeText("broken")
        assertNull(RootfsStamp.readFrom(dir))
        marker.writeText("distro=unknown\narch=arm64\ntier=min\nbuilt=2026-11-01T10:00:00Z\n")
        assertEquals("unknown", RootfsStamp.readFrom(dir)?.distroId)
        val before = marker.readText()
        GenericPosixSpec(RootfsArch.ARM64).configure(dir)
        assertEquals(before, marker.readText())
    }

    @Test fun `configurations use selected distro version and preserve stamp`() {
        val ubuntu = temp.newFolder().apply { File(this, "etc").mkdir() }
        configureFor("ubuntu", "26.04", ubuntu)
        assertEquals("force-unsafe-io\n", File(ubuntu, "etc/dpkg/dpkg.cfg.d/force-unsafe-io").readText())
        assertEquals("APT::Sandbox::User \"root\";\n", File(ubuntu, "etc/apt/apt.conf.d/99rikkahub-nosandbox").readText())
        val alpine = temp.newFolder().apply { File(this, "etc").mkdir() }
        configureFor("alpine", "3.24", alpine)
        assertTrue(File(alpine, "etc/apk/repositories").readText().contains("/v3.24/community"))
    }
}
