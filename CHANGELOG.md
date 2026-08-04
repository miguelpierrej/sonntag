# Changelog

Todas as mudanças notáveis deste projeto são documentadas aqui.

O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o
versionamento segue o [Semantic Versioning](https://semver.org/lang/pt-BR/).

O workflow de release lê a seção da versão que está sendo fechada e usa o conteúdo
como corpo do GitHub Release — o cabeçalho precisa ser `## [x.y.z] - AAAA-MM-DD`.

## [1.0.1] - UNRELEASED

### Adicionado
- Sincronização manual em Configurações › Dados: exportar os blocos escolhidos para um
  arquivo `.sonntag` cifrado (com senha opcional) e importá-lo em outra instalação. A
  importação mostra um resumo antes de gravar e deixa o usuário decidir registro a
  registro o que fazer quando os dois lados mudaram.
- Base para sincronizar dados entre instalações: cada linha das tabelas
  compartilháveis passa a ter identidade global (`uuid`), carimbo de quando e por
  qual dispositivo foi alterada, e exclusão lógica. Ainda não sincroniza nada — é o
  alicerce que torna a fusão possível sem perder edições.

### Corrigido
- Programações que apontavam para reuniões já apagadas eram invisíveis no app e
  impediam a importação de dados. A atualização remove essas linhas órfãs.

## [1.0.0] - 2026-08-04

### Adicionado
- Fluxo de build do programa para gerar instalador .exe e .deb.
- Publicação automática de versões: o push em `master` com o commit `Fecha versão x.y.z`
  gera o `.msi` e o `.deb`, cria a tag e publica o Release usando a seção correspondente
  deste changelog.
- Painel com informação real: próxima reunião, grupo de limpeza da semana e programações
  pendentes dos próximos 28 dias. Clicar em um card abre a tela correspondente.
- Importação do S-34 (`.jwpub`) na tela de fim de semana. Os 194 esboços passam a aparecer
  numa lista de seleção no título do discurso, que continua aceitando texto livre.
- Idioma inicial seguindo o do sistema operacional; a escolha feita em Configurações
  prevalece sobre ele.
- Ícone próprio do aplicativo, na janela e nos instaladores. Na janela, ele destaca os dias
  de reunião configurados.
- Instalador do Windows com atalho no menu Iniciar, escolha da pasta de instalação e
  pergunta sobre o atalho na área de trabalho.
- Documentação (README) em espanhol, português e inglês.
- Licença MIT.

### Alterado
- A janela abre maximizada; ao ser restaurada, volta em tamanho grande e centralizada.
- Ícones dos documentos impressos: vassoura na escala de limpeza e auditório na programação
  de fim de semana, maiores e no canto direito do cabeçalho, para identificar o documento
  de relance.
- Nome do pacote passou de `com.example.sonntag` para `Sonntag`, mudando o nome do atalho e
  da pasta de instalação no Windows.
