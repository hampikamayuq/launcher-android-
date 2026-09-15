package app.cascata.launcher.data.backup

import app.cascata.launcher.data.glance.GlanceSettings
import app.cascata.launcher.data.glance.TemperatureUnit
import app.cascata.launcher.data.notifications.BadgeStyle
import app.cascata.launcher.data.notifications.NotificationSettings
import app.cascata.launcher.data.search.SearchEngine
import app.cascata.launcher.data.search.SearchSettings
import app.cascata.launcher.data.theme.ClockStyle
import app.cascata.launcher.data.theme.ColorSource
import app.cascata.launcher.data.theme.DarkMode
import app.cascata.launcher.data.theme.Density
import app.cascata.launcher.data.theme.MAX_FONT_SCALE
import app.cascata.launcher.data.theme.ThemeSettings
import app.cascata.launcher.data.usage.UsageSettings
import app.cascata.launcher.data.widgets.PlacedWidget
import app.cascata.launcher.data.widgets.WidgetLayout
import app.cascata.launcher.data.widgets.WidgetSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFileTest {

    /** Nenhum campo no padrão: assim o roundtrip prova cada um deles. */
    private val sample = BackupPayload(
        favorites = listOf("com.a/.Main#0", "com.b/.Main#10"),
        hidden = setOf("com.c/.Main#0"),
        aliases = mapOf("com.a/.Main#0" to "Alfa", "com.b/.Main#10" to "Beta"),
        theme = ThemeSettings(
            darkMode = DarkMode.DARK,
            colorSource = ColorSource.ACCENT,
            accentArgb = 0xFFCC0044.toInt(),
            backgroundOpacity = 0.3f,
            density = Density.COMFORTABLE,
            fontScale = 1.2f,
            fontId = "outfit",
            iconPack = "com.exemplo.icones",
            clockStyle = ClockStyle.ANALOG,
        ),
        glance = GlanceSettings(
            showAlarm = true,
            showBattery = true,
            showCalendar = true,
            showWeather = true,
            temperatureUnit = TemperatureUnit.FAHRENHEIT,
        ),
        notifications = NotificationSettings(
            enabled = true,
            badgeStyle = BadgeStyle.DOT,
            expandInline = false,
            mutedPackages = setOf("com.barulhento"),
            showMedia = true,
        ),
        search = SearchSettings(
            engine = SearchEngine.ECOSIA,
            showShortcuts = false,
            showCalculator = false,
            showContacts = true,
            showSettings = false,
            showWeb = false,
        ),
        usage = UsageSettings(
            enabled = true,
            showCard = false,
            pauseSeconds = 12,
            limitsMinutes = mapOf("com.rede.social" to 30),
        ),
        widgets = WidgetLayout(
            slots = listOf(
                WidgetSlot(
                    id = 1,
                    heightCells = 3,
                    widgets = listOf(
                        PlacedWidget(42, "com.exemplo/.Relogio"),
                        PlacedWidget(43, "com.exemplo/.Agenda", userHash = 10),
                    ),
                    activeIndex = 1,
                ),
            ),
            nextSlotId = 2,
        ),
    )

    @Test
    fun `vai e volta sem perder nada`() {
        val decoded = BackupFile.decode(BackupFile.encode(sample))
        assertEquals(sample, decoded.getOrNull())
    }

    @Test
    fun `o envelope tem formato, versao e a versao do app`() {
        val text = BackupFile.encode(sample, appVersion = "0.9.0", createdAtMillis = 1_700_000_000_000L)
        assertTrue(text, text.contains("\"format\": \"cascata-backup\""))
        assertTrue(text, text.contains("\"version\": 1"))
        assertTrue(text, text.contains("\"appVersion\": \"0.9.0\""))
        assertTrue(text, text.contains("\"createdAtMillis\": 1700000000000"))
        // Pretty-print: o arquivo é para ser lido e editado à mão se preciso.
        assertTrue(text, text.contains("\n"))
    }

    @Test
    fun `outro formato e recusado`() {
        val text = """{"format":"cascata-theme","version":1,"payload":{}}"""
        val failure = BackupFile.decode(text)
        assertTrue(failure.isFailure)
        assertTrue(
            failure.exceptionOrNull()?.message.orEmpty(),
            failure.exceptionOrNull()?.message.orEmpty().contains("cascata-theme"),
        )
    }

    @Test
    fun `versao futura e recusada com mensagem clara`() {
        val text = """{"format":"cascata-backup","version":2,"payload":{}}"""
        val failure = BackupFile.decode(text)
        assertTrue(failure.isFailure)
        val message = failure.exceptionOrNull()?.message.orEmpty()
        assertTrue(message, message.contains("version=2"))
        assertTrue(message, message.contains("atualize"))
    }

    @Test
    fun `json invalido e recusado sem lancar`() {
        assertTrue(BackupFile.decode("não é json").isFailure)
        assertTrue(BackupFile.decode("").isFailure)
    }

    @Test
    fun `campo desconhecido e ignorado`() {
        val text = """
            {"format":"cascata-backup","version":1,"futuro":42,
             "payload":{"favorites":["com.a/.Main#0"],"brilhoDaLua":7,
                        "theme":{"fontId":"sora","aindaNaoExiste":true}}}
        """.trimIndent()
        val decoded = BackupFile.decode(text).getOrThrow()
        assertEquals(listOf("com.a/.Main#0"), decoded.favorites)
        assertEquals("sora", decoded.theme.fontId)
    }

    @Test
    fun `campos ausentes viram o padrao`() {
        val text = """{"format":"cascata-backup","version":1,"payload":{}}"""
        assertEquals(BackupPayload(), BackupFile.decode(text).getOrThrow())
    }

    @Test
    fun `payload ausente vira o padrao`() {
        val text = """{"format":"cascata-backup","version":1}"""
        val decoded = BackupFile.decode(text).getOrThrow()
        assertEquals(ThemeSettings.DEFAULT, decoded.theme)
        assertEquals(GlanceSettings.DEFAULT, decoded.glance)
        assertEquals(NotificationSettings.DEFAULT, decoded.notifications)
        assertEquals(SearchSettings.DEFAULT, decoded.search)
        assertEquals(UsageSettings.DEFAULT, decoded.usage)
        assertEquals(WidgetLayout.EMPTY, decoded.widgets)
        assertTrue(decoded.favorites.isEmpty())
    }

    /**
     * Compatibilidade: um `.cascata-backup` escrito antes da Fase 10 não traz
     * os campos novos do tema. Continua na versão 1 e o tema entra com os
     * padrões de agora, sem que o arquivo inteiro se perca.
     */
    @Test
    fun `backup anterior a fase 10 carrega o tema com os padroes novos`() {
        val text = """
            {"format":"cascata-backup","version":1,
             "payload":{"favorites":["com.a/.Main#0"],
                        "theme":{"darkMode":"DARK","fontId":"sora","clockStyle":"BIG"}}}
        """.trimIndent()
        val decoded = BackupFile.decode(text).getOrThrow()
        assertEquals(ThemeSettings.DEFAULT.favoritesStyle, decoded.theme.favoritesStyle)
        assertEquals(ThemeSettings.DEFAULT.indexStyle, decoded.theme.indexStyle)
        assertEquals(ThemeSettings.DEFAULT.wallpaperText, decoded.theme.wallpaperText)
        assertEquals(ThemeSettings.DEFAULT.textShadow, decoded.theme.textShadow)
        assertEquals(ThemeSettings.DEFAULT.searchBarVisible, decoded.theme.searchBarVisible)
        assertEquals("sora", decoded.theme.fontId)
        assertEquals(listOf("com.a/.Main#0"), decoded.favorites)
    }

    @Test
    fun `valores fora da faixa chegam corrigidos`() {
        val text = """
            {"format":"cascata-backup","version":1,
             "payload":{"theme":{"fontScale":9.0,"backgroundOpacity":-1.0,"iconPack":""},
                        "usage":{"pauseSeconds":900}}}
        """.trimIndent()
        val decoded = BackupFile.decode(text).getOrThrow()
        assertEquals(MAX_FONT_SCALE, decoded.theme.fontScale, 0f)
        assertEquals(0f, decoded.theme.backgroundOpacity, 0f)
        assertEquals(null, decoded.theme.iconPack)
        assertEquals(UsageSettings.MAX_PAUSE_SECONDS, decoded.usage.pauseSeconds)
    }
}

class BackupPayloadTest {

    private val known = setOf(0, 10)

    @Test
    fun `hash conhecido fica como esta`() {
        val payload = BackupPayload(
            favorites = listOf("com.a/.Main#0", "com.b/.Main#10"),
            hidden = setOf("com.c/.Main#10"),
            aliases = mapOf("com.a/.Main#0" to "Alfa"),
        )
        val remapped = payload.remapUserHashes(known, primaryHash = 0)
        assertEquals(payload, remapped)
    }

    @Test
    fun `hash desconhecido vai para o perfil principal`() {
        val payload = BackupPayload(
            favorites = listOf("com.trabalho/.Main#77"),
            hidden = setOf("com.outro/.Main#-5"),
            aliases = mapOf("com.trabalho/.Main#77" to "Do trabalho"),
        )
        val remapped = payload.remapUserHashes(known, primaryHash = 0)
        assertEquals(listOf("com.trabalho/.Main#0"), remapped.favorites)
        assertEquals(setOf("com.outro/.Main#0"), remapped.hidden)
        assertEquals(mapOf("com.trabalho/.Main#0" to "Do trabalho"), remapped.aliases)
    }

    @Test
    fun `chave malformada e mantida`() {
        val payload = BackupPayload(
            favorites = listOf("sem-hash", "com.a/.Main#abc", "#0", "com.b/.Main#"),
            hidden = setOf("sem-hash"),
            aliases = mapOf("sem-hash" to "Assim mesmo"),
        )
        val remapped = payload.remapUserHashes(known, primaryHash = 0)
        assertEquals(payload.favorites, remapped.favorites)
        assertEquals(payload.hidden, remapped.hidden)
        assertEquals(payload.aliases, remapped.aliases)
    }

    @Test
    fun `colisao depois do remapeamento nao duplica`() {
        // A mesma atividade em dois perfis que sumiram cai na mesma chave.
        val payload = BackupPayload(
            favorites = listOf("com.a/.Main#77", "com.a/.Main#88"),
            hidden = setOf("com.a/.Main#77", "com.a/.Main#88"),
            aliases = mapOf("com.a/.Main#77" to "Primeiro", "com.a/.Main#88" to "Segundo"),
        )
        val remapped = payload.remapUserHashes(known, primaryHash = 0)
        assertEquals(listOf("com.a/.Main#0"), remapped.favorites)
        assertEquals(setOf("com.a/.Main#0"), remapped.hidden)
        assertEquals(mapOf("com.a/.Main#0" to "Primeiro"), remapped.aliases)
    }

    @Test
    fun `remapeamento nao encosta no resto`() {
        val payload = BackupPayload(
            favorites = listOf("com.a/.Main#77"),
            theme = ThemeSettings.DEFAULT.copy(fontId = "sora"),
            widgets = WidgetLayout(
                slots = listOf(WidgetSlot(1, widgets = listOf(PlacedWidget(9, "com.e/.W", userHash = 77)))),
                nextSlotId = 2,
            ),
        )
        val remapped = payload.remapUserHashes(known, primaryHash = 0)
        assertEquals(payload.theme, remapped.theme)
        // O userHash do widget anda junto com o appWidgetId, que também não
        // sobrevive à troca de aparelho — não se remapeia um sem o outro.
        assertEquals(payload.widgets, remapped.widgets)
    }

    @Test
    fun `sem widgets deixa o resto intacto`() {
        val payload = BackupPayload(
            favorites = listOf("com.a/.Main#0"),
            widgets = WidgetLayout(
                slots = listOf(WidgetSlot(1, widgets = listOf(PlacedWidget(9, "com.e/.W")))),
                nextSlotId = 2,
            ),
        )
        val sem = payload.withoutWidgets()
        assertSame(WidgetLayout.EMPTY, sem.widgets)
        assertNotEquals(payload.widgets, sem.widgets)
        assertEquals(payload.copy(widgets = WidgetLayout.EMPTY), sem)
    }
}
