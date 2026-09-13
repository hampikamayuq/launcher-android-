package app.cascata.launcher.data.search

import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/**
 * Os motores oferecidos na busca. Só o endereço de *busca* — nenhum tem URL de
 * sugestões, de propósito: o app não abre socket, quem vai à rede é o navegador
 * que atende o `ACTION_VIEW`.
 *
 * Os rótulos são nomes de marca; não entram em strings.xml porque não se traduzem.
 */
enum class SearchEngine(val label: String, val template: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query=%s"),
    BRAVE("Brave", "https://search.brave.com/search?q=%s"),
    ECOSIA("Ecosia", "https://www.ecosia.org/search?q=%s"),
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
}

/** Monta o endereço com o termo escapado. Espaço vira "+", acento e "&" viram %XX. */
fun buildSearchUrl(engine: SearchEngine, query: String): String =
    engine.template.replace("%s", URLEncoder.encode(query, "UTF-8"))

/**
 * Quem dispara é a home, que não é uma Activity comum na pilha — daí o
 * `NEW_TASK`: o navegador abre na própria tarefa dele.
 */
fun webSearchIntent(engine: SearchEngine, query: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(buildSearchUrl(engine, query)))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
