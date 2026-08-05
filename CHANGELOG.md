# Changelog

Todas as mudanças notáveis deste projeto são documentadas aqui.

O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o
versionamento segue o [Semantic Versioning](https://semver.org/lang/pt-BR/).

O workflow de release lê a seção da versão que está sendo fechada e usa o conteúdo
como corpo do GitHub Release — o cabeçalho precisa ser `## [x.y.z] - AAAA-MM-DD`.

## [1.0.4] - 2026-08-05

### Adicionado
- Sincronização pela rede local, em Configurações › Dados. Com os dois aparelhos
  visíveis na mesma rede, um encontra o outro sozinho e a troca é nos dois sentidos:
  cada lado envia o que tem de novo e confere o próprio resumo antes de gravar. Quem
  inicia precisa do código de quatro dígitos exibido pelo outro aparelho.
- A troca envia apenas o que mudou desde a última sincronização com aquele aparelho.
  Numa segunda troca com uma única alteração, o pacote cai de cerca de 26 KB para 1 KB.

### Corrigido
- Mudar o horário de uma reunião num aparelho criava uma agenda inteira em paralelo no
  outro — uma semana preenchida e uma vazia, lado a lado. A hora fazia parte da chave
  que identifica reunião e dia de reunião, e ela é justamente o que o usuário edita.
  Agora reunião é identificada por data e tipo, e dia de reunião pelo dia da semana,
  como o próprio app já fazia ao regenerar a agenda.
- Ao abrir, o app funde as agendas duplicadas que ficaram desse problema: sobrevive a
  reunião que carrega o programa, o horário passa a ser o da última alteração e as
  reuniões passadas ficam com o horário em que de fato aconteceram.
- Importar um pacote de dados falhava com "NOT NULL constraint failed:
  weekend_programs.meeting_id" ao aplicar as mudanças. Os programas se referem à sua
  reunião, e quando a mesma reunião existia dos dois lados com identificadores
  diferentes, o programa não encontrava onde se ligar. O arquivo exportado estava
  correto o tempo todo — o defeito era na importação, então os pacotes já gerados
  continuam válidos.
- A tela de importação listava a agenda inteira como divergência a decidir (uma linha
  por reunião), quando na verdade eram as mesmas reuniões com identificadores
  diferentes. Agora só aparece o que realmente mudou dos dois lados.
- Importar o mesmo arquivo duas vezes voltava a propor alterações; agora a segunda
  importação não encontra nada a fazer.

### Alterado
- O APK passa a ser assinado sempre com a mesma chave, guardada no repositório. Antes
  cada máquina (e cada execução do CI) assinava com uma chave própria, e o Android
  recusava instalar a versão nova por cima — era preciso desinstalar, o que apaga os
  dados do aparelho. **Só desta vez** será necessário desinstalar antes de atualizar,
  porque a chave mudou; exporte seus dados antes.

## [1.0.3] - 2026-08-04

### Corrigido
- Importar dados em outro aparelho duplicava a agenda: cada instalação gera as próprias
  reuniões a partir dos dias configurados, com identificadores diferentes, e a fusão as
  tratava como registros distintos — aparecia uma semana com programa e outra vazia.
  Agora reuniões, dias de reunião, grupos e semanas de limpeza são reconhecidos por
  aquilo que os define (data, horário e tipo, por exemplo). A atualização também junta
  as duplicatas que já tenham sido criadas, preservando os programas.
- No tablet em pé o menu lateral fixo ocupava um terço da tela e comprimia o conteúdo.
  Nessa largura ele passa a ser uma faixa de ícones.
- As telas de programação e de áudio/vídeo decidiam o espaço pela largura da janela e
  não pela que sobrava depois do menu, ficando espremidas no tablet e no celular:
  os nomes apareciam cortados e o formulário sem espaço. Agora medem o espaço real e
  reorganizam as colunas conforme a largura disponível.

### Alterado
- As telas de programação de fim de semana e de meio de semana passaram a usar o mesmo
  formato da tela de áudio/vídeo: uma reunião por cartão, com a programação dentro e
  edição direta, sem precisar escolher a reunião numa lista à parte. Os campos se
  reorganizam conforme a largura. Como o formulário do S-140 é longo, os cartões de meio
  de semana abrem ao toque, mostrando recolhidos a leitura da semana e o presidente.
  A próxima reunião do mês já vem aberta.
- Passar o mouse sobre um cartão de reunião não o escurece mais: a área clicável ocupa
  o cartão inteiro e o realce padrão pintava uma faixa cinza sobre ele.

## [1.0.2] - 2026-08-04

### Adicionado
- Aplicativo Android para celular e tablet, com a mesma base de código do desktop.
  A interface se adapta ao tamanho da tela, os PDFs são gerados no aparelho (dá para
  imprimir ou compartilhar pelo próprio sistema) e a troca de dados por arquivo
  `.sonntag` funciona entre celular e computador. O APK sai no mesmo Release.

### Alterado
- A interface se adapta à largura disponível: em janelas estreitas o menu vira gaveta
  sobreposta, os cards do painel empilham e as telas de programação passam de duas
  colunas para lista e detalhe separados.

## [1.0.1] - 2026-08-04

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
