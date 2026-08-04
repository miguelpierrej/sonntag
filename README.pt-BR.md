# Sonntag

[Español](README.md) · **Português** · [English](README.en.md)

Aplicativo de desktop para organizar as programações de reuniões de uma congregação:
discursos de fim de semana, programa de meio de semana, membros, áudio/vídeo,
acomodadores e a escala de limpeza. Tudo fica salvo no seu computador e cada programação
pode ser exportada em PDF ou PNG para imprimir ou compartilhar.

> Projeto independente, sem vínculo nem endosso oficial de nenhuma organização.

## Recursos

- **Painel** — próxima reunião, grupo de limpeza da semana e as reuniões com programação
  ainda incompleta nos próximos 28 dias.
- **Programa de fim de semana** — título do discurso, orador, presidente, dirigente do
  estudo e leitor. Exporta uma reunião ou o mês inteiro (PDF/PNG).
- **Programa de meio de semana (S-140)** — formulário completo: tesouros, ministério, vida
  cristã, cânticos e orações. Exporta o programa e imprime as designações (S-89).
- **Membros** — cadastro de nomes usados em todas as designações.
- **Áudio/vídeo e acomodadores** — áudio, vídeo, plataforma, microfones e acomodadores por
  reunião, com exportação em PDF.
- **Limpeza** — grupo responsável por semana, com exportação mensal em PDF/PNG.
- **Importações** — a apostila (`mwb`, em PDF) preenche o programa de meio de semana; o
  **S-34** (`.jwpub`) traz os 194 esboços de discursos públicos para uma lista de seleção
  no título do discurso.
- **Offline** — não exige internet nem conta; os dados nunca saem da máquina.

## Instalação

Baixe o instalador da versão mais recente na página de [Releases](../../releases):

| Sistema | Arquivo |
| ------- | ------- |
| Windows | `Sonntag-<versão>.msi` |
| Linux (Debian/Ubuntu) | `sonntag_<versão>_amd64.deb` |
| Android (celular e tablet) | `Sonntag-<versão>-debug.apk` |

No Windows o instalador cria o atalho no menu Iniciar e pergunta se você quer um na área
de trabalho. No Linux: `sudo dpkg -i sonntag_<versão>_amd64.deb`. No Android, abra o APK
no aparelho e permita a instalação de fontes desconhecidas.

## Primeiros passos

1. Ao abrir o programa pela primeira vez aparece a **configuração inicial**: nome da
   congregação (obrigatório), endereço, telefone e os **dias e horários de reunião**.
2. Com os dias definidos, o aplicativo gera as reuniões dos próximos 12 meses.
3. Cadastre os **membros** e, em Configurações, os **grupos de limpeza**.
4. Opcional: use **Importar S-34** na tela de fim de semana para ter a lista de esboços, e
   **Importar apostila** na de meio de semana para preencher as semanas do mês.

## Idioma

O aplicativo fala **espanhol** e **português (BR)**. Na primeira execução adota o idioma do
sistema operacional; se for outro, usa espanhol. Você pode trocar quando quiser em
**Configurações › Geral › Idioma**, e essa escolha prevalece sobre o sistema. Os documentos
exportados saem no idioma selecionado.

## Seus dados

Tudo fica em um único arquivo SQLite:

```
Linux/macOS   ~/.salao-app/data.db
Windows       C:\Users\<usuário>\.salao-app\data.db
```

Para fazer backup ou mudar de computador, copie esse arquivo. Desinstalar o aplicativo não
o apaga.

## Desenvolvimento

Requer **JDK 17**. Kotlin Multiplatform com Compose Multiplatform (somente desktop/JVM).

```shell
./gradlew :composeApp:run            # executar
./gradlew :composeApp:packageDeb     # pacote .deb (no Linux)
./gradlew :composeApp:packageMsi     # instalador .msi (no Windows)
./gradlew :composeApp:packageMsiWithPrompt  # .msi que pergunta pelos atalhos (o do Release)
./gradlew :composeApp:exportAppIcon  # regerar o ícone a partir de AppIcon.kt
./gradlew assembleDebug              # APK do Android (requer o SDK do Android)
```

Os instaladores só são gerados no sistema de destino: o jpackage não compila para outra
plataforma.

| Componente | Uso |
| ---------- | --- |
| Compose Multiplatform | interface |
| SQLDelight + SQLite (JDBC/HikariCP) | banco de dados local |
| Koin | injeção de dependências |
| Voyager | telas |
| Apache PDFBox | exportação em PDF e leitura da apostila `mwb` |

Estrutura: `composeApp/src/commonMain` tem a interface, os ViewModels, os repositórios e as
traduções; `composeApp/src/jvmMain` tem o que é específico do desktop (banco, PDF,
importações, ícone, janela).

### Publicar uma versão

O fluxo é disparado pela mensagem do commit. Adicione a seção da versão no
[`CHANGELOG.md`](CHANGELOG.md), faça um commit **`Fecha versão <x.y.z>`** e envie para a
`master`: o workflow lê a versão da mensagem, valida o changelog, gera o `.msi` e o `.deb`,
cria a tag `v<x.y.z>` e publica o Release com essas notas.

## Licença

Distribuído sob a licença [MIT](LICENSE).
