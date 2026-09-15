package app.cascata.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A varredura de perfis é o trecho mais arriscado da abertura num aparelho de
 * verdade: num Samsung convivem o perfil pessoal, o de trabalho, o do Secure
 * Folder e — no Android 15+ — o privado, e consultar um deles pode devolver
 * `SecurityException`. Nada disso acontece num emulador nem num teste, então a
 * regra ("perdeu um perfil, não perdeu a lista") é testada aqui com fontes que
 * lançam de propósito.
 */
class ProfileScanTest {

    private class Falha(perfil: String) : IllegalStateException("perfil $perfil recusou")

    @Test
    fun `junta o que todos os perfis devolvem`() {
        val apps = flatMapProfiles(
            profiles = listOf("pessoal", "trabalho"),
            onFailure = { _, _ -> throw AssertionError("não deveria falhar") },
        ) { perfil -> listOf("$perfil:a", "$perfil:b") }

        assertEquals(listOf("pessoal:a", "pessoal:b", "trabalho:a", "trabalho:b"), apps)
    }

    @Test
    fun `um perfil que lanca some sem levar os outros`() {
        val recusados = mutableListOf<String>()
        val apps = flatMapProfiles(
            profiles = listOf("pessoal", "secure_folder", "trabalho"),
            onFailure = { perfil, _ -> recusados += perfil },
        ) { perfil ->
            if (perfil == "secure_folder") throw Falha(perfil)
            listOf("$perfil:a")
        }

        assertEquals(listOf("pessoal:a", "trabalho:a"), apps)
        assertEquals(listOf("secure_folder"), recusados)
    }

    @Test
    fun `todos os perfis lancando devolve lista vazia, nao excecao`() {
        val recusados = mutableListOf<String>()
        val apps = flatMapProfiles<String, String>(
            profiles = listOf("pessoal", "trabalho"),
            onFailure = { perfil, _ -> recusados += perfil },
        ) { perfil -> throw Falha(perfil) }

        assertTrue(apps.isEmpty())
        assertEquals(listOf("pessoal", "trabalho"), recusados)
    }

    @Test
    fun `sem perfil nenhum devolve vazio sem consultar nada`() {
        var consultas = 0
        val apps = flatMapProfiles(
            profiles = emptyList<String>(),
            onFailure = { _, _ -> throw AssertionError("não deveria falhar") },
        ) { _ ->
            consultas++
            listOf("x")
        }

        assertTrue(apps.isEmpty())
        assertEquals(0, consultas)
    }

    /** O `Error` também: um `NoClassDefFoundError` de API nova mata o processo igual. */
    @Test
    fun `erro fora de Exception tambem e contido`() {
        val apps = flatMapProfiles<String, String>(
            profiles = listOf("privado"),
            onFailure = { _, _ -> },
        ) { _ -> throw NoClassDefFoundError("android.os.UserManager\$Api35") }

        assertTrue(apps.isEmpty())
    }
}
