package com.example.sonntag.ui.screens.preaching

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.sonntag.data.sqldelight.Members
import com.example.sonntag.i18n.tr

/** Campo de escolha: mostra o texto atual e abre a lista de opcoes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> PickerField(
    label: String,
    value: String,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(tr("Selecione")) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (valor, texto) ->
                DropdownMenuItem(
                    text = { Text(texto) },
                    onClick = {
                        onSelected(valor)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Opcoes de publicador para um [PickerField], com a primeira limpando a escolha. */
internal fun List<Members>.comoOpcoes(): List<Pair<Long?, String>> =
    listOf<Pair<Long?, String>>(null to "—") + map { it.id as Long? to it.nomeCompleto() }

internal fun Members.nomeCompleto(): String = "$nome $sobrenome".trim()

internal fun List<Members>.nomeDe(id: Long?): String =
    id?.let { alvo -> firstOrNull { it.id == alvo } }?.nomeCompleto().orEmpty()
