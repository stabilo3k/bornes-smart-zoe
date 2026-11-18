package org.borneneutre.app

import org.junit.Assert.*
import org.junit.Test

class AppUpdateTest {
    private val ok = """{"versionCode":21,"versionName":"1.2.1","apk":"https://github.com/stabilo3k/bornes-smart-zoe/releases/download/v1.2.1/BorneNeutre-1.2.1-versionCode21.apk","page":"https://github.com/stabilo3k/bornes-smart-zoe/releases/tag/v1.2.1","notes":"Test"}"""

    @Test fun parseValide() { val i = AppUpdate.parse(ok)!!; assertEquals(21, i.versionCode); assertEquals("1.2.1", i.versionName) }
    @Test fun parseRefuseAutreHote() = assertNull(AppUpdate.parse(ok.replace("github.com/stabilo3k", "evil.example/x")))
    @Test fun parseRefuseHttp() = assertNull(AppUpdate.parse(ok.replace("https://github", "http://github")))
    @Test fun parseRefuseSansCode() = assertNull(AppUpdate.parse(ok.replace("\"versionCode\":21", "\"versionCode\":0")))
    @Test fun parseRefuseIllisible() = assertNull(AppUpdate.parse("pas du json"))
    @Test fun parseRefuseRemontee() = assertNull(AppUpdate.parse(ok.replace("releases/download", "releases/../x")))
    @Test fun plusRecent() { assertTrue(AppUpdate.isNewer(21, 20)); assertFalse(AppUpdate.isNewer(20, 20)); assertFalse(AppUpdate.isNewer(19, 20)) }
    @Test fun magasins() {
        assertTrue(AppUpdate.isStoreInstall("org.fdroid.fdroid")); assertTrue(AppUpdate.isStoreInstall("com.android.vending")); assertTrue(AppUpdate.isStoreInstall("eu.droidify"))
        assertFalse(AppUpdate.isStoreInstall(null)); assertFalse(AppUpdate.isStoreInstall("com.google.android.packageinstaller"))
    }
}
