# Gerenciador de Escala de Reunião

Este é um aplicativo de desktop simples para gerenciar escalas de reuniões a partir de URLs do site JW.ORG. O aplicativo permite que os usuários importem a programação de uma semana específica, a salvem em um banco de dados local e a visualizem em uma interface gráfica.

## Funcionalidades

-   **Importação de Programação:** Cole uma URL do site JW.ORG contendo a programação de uma reunião para importar os dados.
-   **Armazenamento Local:** As programações importadas são salvas em um banco de dados SQLite local, permitindo o acesso offline.
-   **Visualização de Tarefas:** As tarefas da semana são exibidas em uma tabela clara e organizada.
-   **Navegação por Semanas:** Alterne facilmente entre as semanas salvas usando um menu dropdown.

## Como Funciona

O aplicativo é construído em Python e utiliza as seguintes bibliotecas:

-   **Flet:** Para a criação da interface gráfica do usuário (GUI).
-   **Requests:** Para fazer o download do conteúdo da página da web.
-   **Beautiful Soup:** Para analisar o HTML da página e extrair os dados da programação.
-   **SQLite:** Para armazenar os dados da programação em um banco de dados local.

### Fluxo de Trabalho

1.  O usuário cola a URL da programação da semana do JW.ORG no campo de entrada e clica em "Importar".
2.  O `scraper.py` faz uma requisição HTTP para a URL, baixa o conteúdo HTML e o analisa.
3.  As tarefas, incluindo o tipo, título e duração, são extraídas do HTML.
4.  Cada tarefa é salva no banco de dados `escala.db` pelo `database.py`.
5.  A interface do usuário, gerenciada pelo `main.py`, exibe as tarefas da semana importada.
6.  O usuário pode selecionar outras semanas salvas para visualizar suas respectivas tarefas.

## Estrutura do Projeto

```
.
├── database.py     # Gerencia o banco de dados SQLite
├── escala.db       # Arquivo do banco de dados SQLite
├── main.py         # Ponto de entrada principal e código da GUI com Flet
├── scraper.py      # Contém a lógica de web scraping
└── README.md       # Este arquivo
```

## Como Executar

1.  **Instale as dependências:**

    ```bash
    pip install flet requests beautifulsoup4
    ```

2.  **Execute o aplicativo:**

    ```bash
    python main.py
    ```
