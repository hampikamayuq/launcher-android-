package app.cascata.launcher.crash

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * Quem coleta os Flows do app é a composição da home (`collectAsStateWithLifecycle`),
 * e uma exceção que chega lá derruba o processo — isto é, o aparelho fica sem
 * tela inicial porque um card do topo não conseguiu ler um provedor.
 *
 * [catchQuietly] corta esse caminho: registra e encerra o Flow em silêncio. Quem
 * coleta fica com o último valor (ou com o inicial), que é exatamente "sem dado".
 */
internal fun <T> Flow<T>.catchQuietly(tag: String, what: String): Flow<T> =
    catch { error -> degraded(tag, what, error) }

/**
 * O mesmo, para quem precisa de um valor: um DataStore ilegível vira o padrão do
 * recurso, e não uma tela que nunca aparece.
 */
internal fun <T> Flow<T>.catchEmitting(tag: String, what: String, fallback: T): Flow<T> =
    catch { error ->
        degraded(tag, what, error)
        emit(fallback)
    }
