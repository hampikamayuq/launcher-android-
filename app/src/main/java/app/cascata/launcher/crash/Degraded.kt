package app.cascata.launcher.crash

import android.util.Log

/**
 * O princípio que rege o caminho de abertura do launcher: **falha de recurso
 * opcional vira recurso vazio, nunca processo morto**. Quando ele morre o
 * aparelho fica sem tela inicial, então nenhum widget, card do topo, fonte,
 * perfil de usuário ou arquivo de preferências vale derrubar a home.
 *
 * O que não se faz é engolir a falha em silêncio: tudo o que é degradado passa
 * por aqui e vira uma linha de `Log.w` com a tag da área. Quem tem `adb` à mão
 * encontra o motivo; quem não tem continua com o launcher aberto.
 */
fun degraded(tag: String, what: String, error: Throwable) {
    runCatching { Log.w(tag, what, error) }
}
