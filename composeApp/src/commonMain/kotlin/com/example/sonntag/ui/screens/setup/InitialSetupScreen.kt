package com.example.sonntag.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject

val DIAS_SEMANA = listOf(
    1L to "Segunda",
    2L to "Terça",
    3L to "Quarta",
    4L to "Quinta",
    5L to "Sexta",
    6L to "Sábado",
    7L to "Domingo"
)

@Composable
fun InitialSetupScreen(onComplete: () -> Unit) {
    val viewModel = koinInject<InitialSetupViewModel>()
    val formState by viewModel.formState.collectAsState()

    if (formState.completed) {
        onComplete()
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    "Configuração Inicial",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = formState.nomesCongregacao,
                    onValueChange = { viewModel.updateNome(it) },
                    label = { Text("Nome da Congregação *") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = formState.endereco,
                    onValueChange = { viewModel.updateEndereco(it) },
                    label = { Text("Endereço") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = formState.telefone,
                    onValueChange = { viewModel.updateTelefone(it) },
                    label = { Text("Telefone") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                )
            }

            item {
                Text(
                    "Dias de Reunião *",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            items(formState.meetingDays) { day ->
                MeetingDayInputRow(
                    day = day,
                    onDiaChange = { newDia ->
                        viewModel.updateMeetingDay(day.id, newDia, day.hora)
                    },
                    onHoraChange = { newHora ->
                        viewModel.updateMeetingDay(day.id, day.diaSemana, newHora)
                    },
                    onRemove = { viewModel.removeMeetingDay(day.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            }

            item {
                OutlinedButton(
                    onClick = { viewModel.addMeetingDay() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Text("+ Adicionar dia")
                }
            }

            if (formState.errorMessage != null) {
                item {
                    Text(
                        formState.errorMessage ?: "",
                        color = Color.Red,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }

            item {
                Button(
                    onClick = { viewModel.saveSetup() },
                    enabled = !formState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(if (formState.isLoading) "Salvando..." else "Salvar Configuração")
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun MeetingDayInputRow(
    day: MeetingDayInput,
    onDiaChange: (Long) -> Unit,
    onHoraChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val diaExpanded = remember { mutableStateOf(false) }
    val diaLabel = DIAS_SEMANA.find { it.first == day.diaSemana }?.second ?: "Selecione"

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Button(
                onClick = { diaExpanded.value = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(diaLabel)
            }
            DropdownMenu(
                expanded = diaExpanded.value,
                onDismissRequest = { diaExpanded.value = false }
            ) {
                DIAS_SEMANA.forEach { (dia, nome) ->
                    DropdownMenuItem(
                        text = { Text(nome) },
                        onClick = {
                            onDiaChange(dia)
                            diaExpanded.value = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        OutlinedTextField(
            value = day.hora,
            onValueChange = { if (it.length <= 5) onHoraChange(it) },
            label = { Text("HH:mm") },
            modifier = Modifier.width(100.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        OutlinedButton(
            onClick = onRemove,
            modifier = Modifier.width(100.dp)
        ) {
            Text("Remover")
        }
    }
}


