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

/**
 * Responsabilidades do publicador como saem ao lado do nome: "SM | PR".
 *
 * As siglas nao passam pelo tradutor porque sao as mesmas nos dois idiomas
 * (Ancião/Anciano, Servo ministerial/Siervo ministerial, Pioneiro/Precursor).
 */
internal fun Members.siglas(): String = listOfNotNull(
    "AN".takeIf { anciao != 0L },
    "SM".takeIf { servo_ministerial != 0L },
    "PR".takeIf { pioneiro != 0L },
).joinToString(" | ")

/** "João Silva - SM | PR", ou so o nome quando nao ha responsabilidade marcada. */
internal fun Members.nomeComSiglas(): String =
    siglas().let { if (it.isBlank()) nomeCompleto() else "${nomeCompleto()} - $it" }

/**
 * Campo de publicador que se escreve: a lista filtra conforme o texto e, quando so
 * um nome sobra, ele ja fica escolhido — nao e preciso abrir a lista para confirmar.
 *
 * A comparacao ignora acentos: quem digita "joao" acha "João".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemberSearchField(
    label: String,
    options: List<Pair<Long, String>>,
    onSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var texto by remember { mutableStateOf("") }
    var aberto by remember { mutableStateOf(false) }

    fun combinam(termo: String) = options.filter { semAcento(it.second).contains(termo) }

    val filtradas = combinam(semAcento(texto))
    ExposedDropdownMenuBox(
        expanded = aberto && filtradas.isNotEmpty(),
        onExpandedChange = { aberto = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = texto,
            onValueChange = { novo ->
                texto = novo
                aberto = true
                val termo = semAcento(novo)
                // Um unico nome compativel ja vale como escolha; texto em branco ou
                // ambiguo desfaz a anterior, para nao salvar quem o usuario nao viu.
                onSelected(if (termo.isBlank()) null else combinam(termo).singleOrNull()?.first)
            },
            label = { Text(label) },
            placeholder = { Text(tr("Escreva o nome")) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aberto) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = aberto && filtradas.isNotEmpty(), onDismissRequest = { aberto = false }) {
            // A lista inteira nao cabe na tela; quem procura alguem especifico
            // continua digitando ate o nome aparecer.
            filtradas.take(30).forEach { (id, nome) ->
                DropdownMenuItem(
                    text = { Text(nome) },
                    onClick = {
                        texto = nome
                        onSelected(id)
                        aberto = false
                    },
                )
            }
        }
    }
}

/** Minusculas e sem acento, para comparar o que foi digitado com o nome cadastrado. */
private fun semAcento(texto: String): String {
    val de = "áàâãäéèêëíìîïóòôõöúùûüçñ"
    val para = "aaaaaeeeeiiiiooooouuuucn"
    return texto.lowercase().map { c -> de.indexOf(c).let { if (it >= 0) para[it] else c } }.joinToString("")
}
