# PROGRESS

## Visao geral
Projeto Kotlin + Compose Multiplatform (Desktop/JVM) com foco em entrega funcional e validacao manual. Arquitetura em camadas (`data`, `domain`, `ui`, `pdf`, `di`) com SQLDelight, Koin, ViewModel + StateFlow + Coroutines, Voyager e exportacao PDF/PNG.

---

## Task 1 - Esqueleto do projeto
Status: **Concluida**

Implementado:
- Estrutura base de pacotes (`data`, `domain`, `ui`, `pdf`, `di`).
- Configuracao de dependencias principais no Gradle:
  - Compose MP
  - SQLDelight
  - Koin
  - Voyager
  - PDFBox
  - Coroutines
- Tela inicial minima com `Hello World`.
- Inicializacao de Koin com modulo inicial.
- Task raiz `run` apontando para `:composeApp:run`.

---

## Task 2 - Banco de dados e camada de dados
Status: **Concluida**

Implementado:
- Schema SQLDelight com tabelas:
  - `settings`
  - `meeting_days`
  - `members`
  - `cleaning_groups`
  - `meetings`
  - `weekend_programs`
  - `cleaning_assignments`
- Queries CRUD basicas para todas as entidades.
- Repositorios criados para todas as tabelas.
- Driver/Factory do banco para Desktop/JVM.
- Caminho do banco: `~/.salao-app/data.db`.
- Repositorios registrados no Koin.

---

## Task 3 - Setup inicial obrigatorio
Status: **Concluida**

Implementado:
- Verificacao de `settings` na inicializacao.
- Tela bloqueante de configuracao inicial quando nao existe configuracao.
- Campos:
  - nome da congregacao
  - endereco
  - telefone
  - dias/horarios de reuniao (dinamico)
- Validacao: minimo de 1 dia de reuniao.
- Persistencia ao salvar e encerramento do setup.

---

## Task 4 - Shell de navegacao
Status: **Concluida**

Implementado:
- Layout principal com menu lateral (`NavigationRail`).
- Itens:
  - Dashboard
  - Programacoes (Final de Semana)
  - Membros
  - Limpeza
  - Configuracoes
- Troca de conteudo por item no painel principal.
- Placeholder inicial por tela (depois evoluido nas tasks seguintes).

---

## Task 5 - Membros (CRUD)
Status: **Concluida**

Implementado:
- Tela de membros com:
  - lista/tabela
  - busca por nome/sobrenome (tempo real)
  - modal de novo/editar
  - confirmacao de exclusao
- Persistencia no banco.
- Ordenacao alfabetica por nome no SQL.
- `MembersRepository` com fluxo reativo.

---

## Task 6 - Grupos de limpeza (CRUD)
Status: **Concluida**

Implementado:
- CRUD de `cleaning_groups` dentro de `Configuracoes`.
- Estrutura equivalente a Membros:
  - lista
  - busca
  - modal de novo/editar
  - confirmacao de exclusao
- Persistencia com SQLDelight.

---

## Task 7 - Tela de Configuracoes
Status: **Concluida**

Implementado:
- Edicao dos dados do salao (nome, endereco, telefone).
- Edicao dos dias/horarios de reuniao.
- Salvamento com logica de regeneracao de reunioes futuras:
  - remove futuras sem programa
  - se so mudou horario, atualiza horario
  - se mudou dia, loga "programa a realocar" (placeholder para feature futura)
  - regenera reunioes para os proximos 12 meses
- Dependencia adicionada: `kotlinx-datetime`.

---

## Task 8 - Geracao de reunioes + lista de fim de semana
Status: **Concluida**

Implementado:
- Service `MeetingGenerator` (idempotente) para gerar reunioes dos proximos 12 meses.
- Chamada do gerador na inicializacao do app.
- Tela `Programacoes > Final de Semana` com:
  - coluna lateral de reunioes `WEEKEND`
  - separadores `Proximas` e `Historico`
  - area principal com placeholder de selecao.

---

## Task 9 - Editor de programacao de fim de semana
Status: **Concluida**

Implementado:
- Ao selecionar reuniao, formulario principal com:
  - titulo do discurso
  - orador
  - presidente
  - dirigente do estudo
  - leitor
- Dropdowns com busca de membros.
- Salvamento automatico com debounce (~500ms).
- Reuniao passada em modo somente leitura + badge `Realizada`.
- Persistencia de dados em `weekend_programs`.

---

## Task 10 - Aba de Limpeza
Status: **Concluida**

Implementado:
- Tela por semana ISO (`ano + semana`).
- Exibicao por linha:
  - periodo da semana
  - datas das reunioes na semana
  - grupo responsavel
- Secoes:
  - `Proximas`
  - `Historico`
- Cobertura aproximada de 12 meses a frente + historico abaixo.
- Salvamento automatico em `cleaning_assignments`.

---

## Task 11 - Geracao de PDF
Status: **Concluida**

Implementado:
- Exportacao PDF no editor de programacao de fim de semana:
  - PDF da reuniao selecionada
  - PDF semanal (inclui grupo de limpeza)
  - PDF mensal
- Geracao com PDFBox.
- Dialogo nativo "Salvar como".
- Abertura automatica no leitor padrao.

---

## Task 12 - Geracao de PNG
Status: **Concluida**

Implementado:
- Exportacao PNG com a mesma logica de dados do PDF:
  - PNG da reuniao selecionada
  - PNG semanal
  - PNG mensal
- Layout vertical otimizado para celular/WhatsApp (largura ~1080px).
- Renderizacao JVM via `BufferedImage`/AWT.
- Dialogo nativo "Salvar como" e abertura no app padrao.

---

## Estado atual do projeto
- Build compila no alvo JVM Desktop.
- Fluxo principal funcional:
  - Setup inicial
  - Navegacao
  - CRUD de membros
  - CRUD de grupos de limpeza
  - Configuracoes com regeneracao
  - Programacao fim de semana com autosave
  - Limpeza semanal com atribuicao por semana
  - Exportacao PDF/PNG

---

## Pendencias naturais (nao implementadas)
- Realocacao assistida de programas orfaos (hoje apenas log).
- Refino visual das telas e feedbacks de salvamento (toasts/status por linha).
- Testes automatizados (fase atual segue foco em testes manuais).

