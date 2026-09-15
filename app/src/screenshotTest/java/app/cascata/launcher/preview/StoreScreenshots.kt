package app.cascata.launcher.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

/**
 * As imagens da ficha da loja (F-Droid e Play), renderizadas por layoutlib na
 * JVM — sem emulador. Uma função por tela e por idioma: o nome do arquivo de
 * referência sai do nome da função, e é por ele que `scripts/screenshots.sh`
 * copia cada PNG para `fastlane/metadata/android/<locale>/images/phoneScreenshots/`.
 *
 * A ordem da loja é a dos números: 1 home, 2 índice em onda (a gaveta na letra
 * escolhida, com a bolha), 3 busca, 4 notificações, 5 uso, 6 aparência. Não
 * renomeie sem ajustar o script.
 *
 * Regerar: `./gradlew :app:updateLiteDebugScreenshotTest && scripts/screenshots.sh`
 * (ver `docs/publicacao.md`).
 */

/** Telefone comum: 1080x2400 em 440 dpi — a resolução que as duas lojas aceitam. */
private const val PHONE = "spec:width=1080px,height=2400px,dpi=440"

@PreviewTest
@Preview(name = "1-home", device = PHONE, locale = "pt-rBR", showBackground = true, showSystemUi = false)
@Composable
fun Home1PtBr() = HomePreview(dark = false)

@PreviewTest
@Preview(name = "1-home", device = PHONE, locale = "en", showBackground = true, showSystemUi = false)
@Composable
fun Home1En() = HomePreview(dark = false)

@PreviewTest
@Preview(name = "1-home", device = PHONE, locale = "es", showBackground = true, showSystemUi = false)
@Composable
fun Home1Es() = HomePreview(dark = false)

@PreviewTest
@Preview(name = "2-drawer", device = PHONE, locale = "pt-rBR", showBackground = true, showSystemUi = false)
@Composable
fun Drawer2PtBr() = DrawerPreview(dark = true)

@PreviewTest
@Preview(name = "2-drawer", device = PHONE, locale = "en", showBackground = true, showSystemUi = false)
@Composable
fun Drawer2En() = DrawerPreview(dark = true)

@PreviewTest
@Preview(name = "2-drawer", device = PHONE, locale = "es", showBackground = true, showSystemUi = false)
@Composable
fun Drawer2Es() = DrawerPreview(dark = true)

@PreviewTest
@Preview(name = "3-search", device = PHONE, locale = "pt-rBR", showBackground = true, showSystemUi = false)
@Composable
fun Search3PtBr() = SearchPreview(dark = false)

@PreviewTest
@Preview(name = "3-search", device = PHONE, locale = "en", showBackground = true, showSystemUi = false)
@Composable
fun Search3En() = SearchPreview(dark = false)

@PreviewTest
@Preview(name = "3-search", device = PHONE, locale = "es", showBackground = true, showSystemUi = false)
@Composable
fun Search3Es() = SearchPreview(dark = false)

@PreviewTest
@Preview(name = "4-notifications", device = PHONE, locale = "pt-rBR", showBackground = true, showSystemUi = false)
@Composable
fun Notifications4PtBr() = NotificationsPreview(dark = true)

@PreviewTest
@Preview(name = "4-notifications", device = PHONE, locale = "en", showBackground = true, showSystemUi = false)
@Composable
fun Notifications4En() = NotificationsPreview(dark = true)

@PreviewTest
@Preview(name = "4-notifications", device = PHONE, locale = "es", showBackground = true, showSystemUi = false)
@Composable
fun Notifications4Es() = NotificationsPreview(dark = true)

@PreviewTest
@Preview(name = "5-usage", device = PHONE, locale = "pt-rBR", showBackground = true, showSystemUi = false)
@Composable
fun Usage5PtBr() = UsagePreview(dark = false)

@PreviewTest
@Preview(name = "5-usage", device = PHONE, locale = "en", showBackground = true, showSystemUi = false)
@Composable
fun Usage5En() = UsagePreview(dark = false)

@PreviewTest
@Preview(name = "5-usage", device = PHONE, locale = "es", showBackground = true, showSystemUi = false)
@Composable
fun Usage5Es() = UsagePreview(dark = false)

@PreviewTest
@Preview(name = "6-appearance", device = PHONE, locale = "pt-rBR", showBackground = true, showSystemUi = false)
@Composable
fun Appearance6PtBr() = AppearancePreview(dark = true)

@PreviewTest
@Preview(name = "6-appearance", device = PHONE, locale = "en", showBackground = true, showSystemUi = false)
@Composable
fun Appearance6En() = AppearancePreview(dark = true)

@PreviewTest
@Preview(name = "6-appearance", device = PHONE, locale = "es", showBackground = true, showSystemUi = false)
@Composable
fun Appearance6Es() = AppearancePreview(dark = true)
