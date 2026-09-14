package app.cascata.launcher.ui.usage

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.cascata.launcher.R

/** Mais que isso não é limite diário: 24 h são 1440 minutos. */
private const val MAX_DIGITS = 4

/**
 * O limite diário de um app, em minutos. É o mesmo diálogo na folha de uso e na
 * seção das configurações — quem grava é quem chama, com o pacote em mãos.
 *
 * "Remover limite" fica no lugar de "Cancelar" porque tirar o limite é a outra
 * decisão possível aqui; fechar sem mexer continua sendo tocar fora ou voltar.
 */
@Composable
fun LimitDialog(
    appLabel: String,
    minutes: Int?,
    onConfirm: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Campo vazio quando não há limite: zero seria um limite que não existe.
    var text by remember(appLabel) { mutableStateOf(minutes?.toString().orEmpty()) }
    val parsed = text.toIntOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appLabel) },
        text = {
            OutlinedTextField(
                value = text,
                // Teclado numérico não impede colar letras: o filtro é aqui.
                onValueChange = { novo -> text = novo.filter { it.isDigit() }.take(MAX_DIGITS) },
                label = { Text(stringResource(R.string.usage_limit_field)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(parsed) },
                enabled = parsed != null,
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(null) }) {
                Text(stringResource(R.string.usage_limit_remove))
            }
        },
    )
}
