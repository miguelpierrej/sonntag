# Changelog

Todas as mudanças notáveis deste projeto são documentadas aqui.

O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o
versionamento segue o [Semantic Versioning](https://semver.org/lang/pt-BR/).

O workflow de release lê a seção da versão que está sendo fechada e usa o conteúdo
como corpo do GitHub Release — o cabeçalho precisa ser `## [x.y.z] - AAAA-MM-DD`.

## [1.0.6] - 2026-08-17

### Adicionado
- Programa de pregação, em Programações › Pregação. Uma tela só, com os dois
  calendários — carrinhos e pregação de campo — e os cadastros de pontos e de grupos.
  Cada dia recebe turnos com horário, ponto e até quatro designados, mais um destaque
  em vermelho para avisos como "Todos os grupos no Salão". O padrão semanal descreve o
  que se repete toda semana e "Gerar mês" cria o mês inteiro a partir dele, sem tocar
  no que já foi ajustado à mão nem duplicar o que já existe.
- Os dois programas de pregação saem em PDF no formato de calendário mensal, com o
  rodapé trazendo os grupos (dirigente e ponto de encontro) e a observação do mês.
- Os dados de pregação entram na exportação por arquivo e na sincronização pela rede,
  como mais um bloco a escolher.
- No Android, tocar num arquivo `.sonntag` passa a oferecer o Sonntag na lista de
  "abrir com", e o app abre direto no resumo da importação. Vale também para
  compartilhar o arquivo com o app a partir de outro aplicativo.

### Corrigido
- No celular, importar a apostila trazia metade de cada semana: nenhum cântico e a
  seção Nossa Vida Cristã vazia, com as partes dela caindo no Ministério. A biblioteca
  de PDF do Android devolve os acentos separados da letra ("Cancio" + acento), e o
  texto deixava de casar com o que o app procura; o enfeite do título da seção também
  chegava diferente do desktop. Agora a leitura junta os acentos e compara só as
  letras do título, e o resultado no celular é idêntico ao do computador.
- Uma parte da apostila era perdida quando o título começava com aspas, porque elas
  vinham coladas no número ("7.“Te salvarás..."). Acontecia nos dois sistemas.
- A sincronização gravava sozinha os registros novos e as atualizações; só as
  divergências passavam por você. Agora o resumo agrupa por bloco e por tipo de
  mudança — novos, atualizações, exclusões e divergências — e só os novos vêm
  marcados: nada que sobrescreva ou apague é aplicado sem a sua marcação. Cada grupo
  abre para decidir registro a registro.
- Uma exclusão vinda do outro aparelho podia apagar aqui uma reunião com programa
  preenchido. Quando os dois lados criaram a mesma reunião com identidades diferentes
  e um deles apagou a duplicata, essa exclusão viajava e casava com a linha viva do
  outro lado. Exclusões agora só valem para a mesma identidade.
- A agenda voltava a duplicar quando os dois aparelhos tinham horários diferentes para
  o mesmo dia: o gerador não reconhecia a reunião que chegou às 19:30 e criava outra às
  19:00 no mesmo dia. Ele passa a identificar a reunião por data e tipo, como o resto
  do app já fazia.
- Ao abrir, o app apaga de vez as duplicatas que a fusão de agendas já tinha marcado
  como excluídas. Elas continuavam viajando em cada troca e ameaçando o outro aparelho.
- Em computadores com Docker, VPN ou máquina virtual, os aparelhos não se enxergavam na
  rede local: o anúncio saía por uma ponte virtual que não leva a lugar nenhum. O app
  passa a usar a interface por onde o sistema realmente alcança a rede.

### Alterado
- Todos os documentos exportados passam a ter o mesmo cabeçalho: um cartão com o nome
  do documento e o período à esquerda e um bloco azul com a congregação à direita.
  Vale para fim de semana (mensal e por reunião), áudio/vídeo e limpeza. Nos documentos
  de mais de uma página o cabeçalho se repete em todas, para cada folha se explicar
  sozinha.
- O programa de meio de semana (S-140) foi repintado nas cores do modelo impresso, com
  os pictogramas das três seções (tesouros, ministério e vida cristã), a leitura da
  semana alinhada à direita na faixa e cada seção na sua cor.
- Nos programas de fim de semana e de áudio/vídeo, cada reunião ganhou uma faixa azul
  atrás da data, que separa um bloco do outro de longe.
- Os dias e horários de reunião não viajam mais pela rede local: cada instalação mantém
  os seus, e aceitar os do vizinho regerava a agenda inteira no horário errado. No
  arquivo exportado eles continuam, porque ali servem para montar uma instalação nova.
- Na tabela Dirigente/Leitor do S-140, os nomes eram desenhados por cima da faixa do
  cabeçalho em vez de dentro da célula.

## [1.0.5] - 2026-08-05

### Corrigido
- O app instalado não abria: no Windows o instalador saía com "Failed to launch JVM" e
  no Linux o pacote fechava calado. O runtime embutido é enxugado pelo jlink e vinha sem
  `java.sql`, `java.naming` e `java.management`, de que o banco e o pool de conexões
  precisam — e abrir o banco é a primeira coisa que o app faz. Só rodando pelo Gradle
  escapava, porque ali o JDK está inteiro.
- Mudar o horário de uma reunião num aparelho criava uma agenda inteira em paralelo no
  outro — uma semana preenchida e uma vazia, lado a lado. A hora fazia parte da chave
  que identifica reunião e dia de reunião, e ela é justamente o que o usuário edita.
  Agora reunião é identificada por data e tipo, e dia de reunião pelo dia da semana,
  como o próprio app já fazia ao regenerar a agenda.
- Ao abrir, o app funde as agendas duplicadas que ficaram desse problema: sobrevive a
  reunião que carrega o programa, o horário passa a ser o da última alteração e as
  reuniões passadas ficam com o horário em que de fato aconteceram.

## [1.0.4] - 2026-08-05

### Adicionado
- Sincronização pela rede local, em Configurações › Dados. Com os dois aparelhos
  visíveis na mesma rede, um encontra o outro sozinho e a troca é nos dois sentidos:
  cada lado envia o que tem de novo e confere o próprio resumo antes de gravar. Quem
  inicia precisa do código de quatro dígitos exibido pelo outro aparelho.
- A troca envia apenas o que mudou desde a última sincronização com aquele aparelho.
  Numa segunda troca com uma única alteração, o pacote cai de cerca de 26 KB para 1 KB.

### Corrigido
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
