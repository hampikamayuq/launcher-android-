package app.cascata.launcher.data.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val DONE = booleanPreferencesKey("done")

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

    val done: Flow<Boolean> = context.onboardingDataStore.data
        .map { it[DONE] ?: false }
        .distinctUntilChanged()

    suspend fun markDone() {
        context.onboardingDataStore.edit { it[DONE] = true }
    }

    /** Mostra o onboarding de novo — serve ao "ver as boas-vindas" das configurações. */
    suspend fun reset() {
        context.onboardingDataStore.edit { it.clear() }
    }
}
