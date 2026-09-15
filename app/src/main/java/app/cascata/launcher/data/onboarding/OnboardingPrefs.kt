package app.cascata.launcher.data.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import app.cascata.launcher.crash.catchEmitting
import app.cascata.launcher.crash.degraded
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val DONE = booleanPreferencesKey("done")

/** Tag dos avisos deste arquivo. */
private const val TAG = "CascataPrefs"

/**
 * Arquivo próprio, como os demais: apagar favoritos ou restaurar um backup não
 * pode fazer o app achar que é a primeira abertura de novo.
 */
private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "cascata_onboarding")

/**
 * Se as telas de boas-vindas já passaram. Uma chave só — a apresentação em si
 * (três telas: virar padrão, densidade, índice lateral) é da UI.
 */
class OnboardingPrefs(private val context: Context) {

    // Este é o pior de todos para falhar: enquanto a preferência não chega, a
    // HomeActivity não desenha nem a home nem as boas-vindas, e uma exceção aqui
    // deixaria a tela em branco para sempre — ou derrubaria o app. Um arquivo
    // ilegível volta a valer como primeira abertura (as boas-vindas aparecem de
    // novo); se nem isso der certo, a home entra direto, que é o que o aparelho
    // precisa ter.
    val done: Flow<Boolean> = context.onboardingDataStore.data
        .catch { error ->
            degraded(TAG, "boas-vindas ilegíveis", error)
            emit(emptyPreferences())
        }
        .map { it[DONE] ?: false }
        .distinctUntilChanged()
        .catchEmitting(TAG, "boas-vindas ilegíveis", true)

    suspend fun markDone() {
        context.onboardingDataStore.edit { it[DONE] = true }
    }

    /** Mostra o onboarding de novo — serve ao "ver as boas-vindas" das configurações. */
    suspend fun reset() {
        context.onboardingDataStore.edit { it.clear() }
    }
}
