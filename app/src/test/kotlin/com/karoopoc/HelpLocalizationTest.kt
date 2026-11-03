package com.karoopoc

import android.content.res.Configuration
import android.os.LocaleList
import android.widget.ImageView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class HelpLocalizationTest {
    private val languages = listOf("en", "de", "es", "fr", "it", "pt")

    @Test fun `all active articles and setup guide resolve to each supported language`() {
        val app = RuntimeEnvironment.getApplication()
        val files = HelpContent.articles.map { it.fileName } + "recommended-camera-setup.md"
        for (language in languages + "pt-BR") {
            val lang = language.substringBefore('-')
            val config = Configuration(app.resources.configuration).apply {
                setLocales(LocaleList(Locale.forLanguageTag(language)))
            }
            val context = app.createConfigurationContext(config)
            val folder = "help/$lang"
            assertTrue(context.assets.list(folder)!!.toSet().containsAll(files))
            for (file in files) {
                val expected = context.assets.open("$folder/$file").bufferedReader().use { it.readText() }
                val actual = HelpContent.load(context, file)
                assertEquals("$language/$file must not silently fall back to English", expected, actual)
                assertFalse(actual.contains("{{"))
                if (lang != "en") assertNotEquals(HelpContent.load(app, file), actual)
                val view = HelpContent.render(context, actual, DjiTheme.palette(context)) { it * 2 }
                assertTrue(view.childCount > 0)
                if (file == "get-ready-for-a-ride.md") {
                    val images = (0 until view.childCount).map { view.getChildAt(it) }.filterIsInstance<ImageView>()
                    assertEquals(1, images.size)
                    assertEquals(context.getString(R.string.lock_menu_caption), images.single().contentDescription)
                }
            }
        }
    }

    @Test fun `UI translations cover all string keys and preserve format arguments`() {
        fun read(file: File): Map<String, String> {
            val list = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            return (0 until list.length).associate { i ->
                val n = list.item(i)
                n.attributes.getNamedItem("name").nodeValue to n.textContent
            }
        }
        val base = read(File("src/main/res/values/strings.xml"))
        val arguments = Regex("%[0-9]+\\$[0-9.]*[a-zA-Z]")
        for (lang in languages - "en") {
            val translated = read(File("src/main/res/values-$lang/strings.xml"))
            assertEquals("$lang string-key coverage", base.keys, translated.keys)
            base.forEach { (key, value) ->
                assertEquals("$lang/$key arguments", arguments.findAll(value).map { it.value }.sorted().toList(),
                    arguments.findAll(translated.getValue(key)).map { it.value }.sorted().toList())
            }
        }
    }

    @Test fun `unsupported language still has the English fallback`() {
        val app = RuntimeEnvironment.getApplication()
        val config = Configuration(app.resources.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag("nl"))) }
        val context = app.createConfigurationContext(config)
        assertEquals(HelpContent.load(app, "prevent-standby.md"), HelpContent.load(context, "prevent-standby.md"))
    }
}
