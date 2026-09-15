package app.cascata.launcher.data.theme

/**
 * Que tinta usar quando o texto fica direto sobre o papel de parede: `true` é
 * texto escuro, `false` é texto claro.
 *
 * Função pura de propósito — a regra vale tanto para o tema quanto para
 * qualquer teste, e nenhuma das duas coisas precisa de Compose para decidir.
 *
 * [WallpaperText.LIGHT] e [WallpaperText.DARK] são a palavra final do usuário.
 * [WallpaperText.AUTO] pergunta ao sistema ([wallpaperDarkText], vindo de
 * `WallpaperColorsSource.supportsDarkText`): só escurece o texto quando o
 * sistema afirma que o papel de parede aguenta. Sem resposta (`null`: aparelho
 * antigo, perfil sem papel de parede próprio, leitura que falhou) fica o texto
 * claro, que é o que costuma sobreviver a uma foto qualquer de fundo — ainda
 * mais com a sombra que o tema põe atrás dele.
 */
fun wallpaperInk(text: WallpaperText, wallpaperDarkText: Boolean?): Boolean = when (text) {
    WallpaperText.LIGHT -> false
    WallpaperText.DARK -> true
    WallpaperText.AUTO -> wallpaperDarkText == true
}
