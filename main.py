import flet as ft
# Certifique-se que database.py e scraper.py estão na mesma pasta e com as funções corretas
from database import init_db, salvar_tarefa, listar_tarefas, get_semanas
from sonntag.scrapper.web_scrapper import DataScrapper


def main(page: ft.Page):
    page.title = "Gerenciador de Escala"
    page.vertical_alignment = ft.MainAxisAlignment.START
    page.theme_mode = ft.ThemeMode.LIGHT
    page.window_width = 900
    page.window_height = 700
    page.padding = 20

    
    scrapper = DataScrapper()
    # OS DADOS DO SITE ESTÃO NESSA VARIAVEL AQUI, É SO USAR -- VAI VIR EM FORMATO DE LISTA
    data = scrapper.open_browser_and_scrappe_data()

    def update_tasks_table(tasks_list: list):
        """Limpa e repopula a tabela de tarefas."""
        tasks_table.rows.clear()
        if not tasks_list:
            tasks_table.rows.append(
                ft.DataRow(cells=[
                    ft.DataCell(ft.Text("Nenhuma tarefa encontrada.", italic=True, color="grey")),
                    ft.DataCell(ft.Text("")),
                    ft.DataCell(ft.Text("")),
                ])
            )
        else:
            for tarefa in tasks_list:
                tipo = tarefa.get('tipo', 'Outros')
                titulo = tarefa.get('titulo', 'Sem Título')
                tempo = tarefa.get('tempo')
                
                tasks_table.rows.append(
                    ft.DataRow(cells=[
                        ft.DataCell(ft.Text(tipo, size=12, weight=ft.FontWeight.BOLD, color="blue")),
                        ft.DataCell(ft.Text(titulo, size=12)),
                        ft.DataCell(ft.Text(tempo if tempo else "—", size=12)),
                    ])
                )
        page.update()

    def update_week_dropdown(selected_week=None):
        """Atualiza o dropdown com as semanas disponíveis no banco."""
        try:
            semanas = get_semanas()
        except Exception as e:
            print(f"Erro ao buscar semanas: {e}")
            semanas = []

        week_dropdown.options.clear()
        
        if not semanas:
            week_dropdown.disabled = True
            week_dropdown.value = None
            week_dropdown.hint_text = "Nenhuma semana salva"
        else:
            week_dropdown.disabled = False
            week_dropdown.hint_text = "Selecione uma semana"
            for s in semanas:
                week_dropdown.options.append(ft.dropdown.Option(s))
            
            if selected_week and selected_week in semanas:
                week_dropdown.value = selected_week
            elif semanas and not week_dropdown.value:
                week_dropdown.value = semanas[0]
                week_changed(None)
        
        page.update()
        
    def week_changed(e):
        """Handler para quando troca a semana no dropdown."""
        selected_week = week_dropdown.value
        if selected_week:
            tasks = listar_tarefas(semana=selected_week)
            update_tasks_table(tasks)

    def import_button_clicked(e):
        """Ação do botão Importar."""
        url = url_field.value.strip()
        if not url:
            page.snack_bar = ft.SnackBar(content=ft.Text("Por favor, insira uma URL."), bgcolor="red")
            page.snack_bar.open = True
            page.update()
            return

        import_button.disabled = True
        progress_ring.visible = True
        status_text.value = "Conectando ao site..."
        page.update()

        try:
            # Executa o scraping
            tarefas_extraidas = scraper.extrair_semana(url)
            
            if not tarefas_extraidas:
                page.snack_bar = ft.SnackBar(content=ft.Text("Nenhuma tarefa encontrada."), bgcolor="orange")
                page.snack_bar.open = True
                status_text.value = "Falha na importação."
            else:
                imported_week = tarefas_extraidas[0]['semana']
                for tarefa in tarefas_extraidas:
                    salvar_tarefa(tarefa)
                
                page.snack_bar = ft.SnackBar(content=ft.Text(f"Semana '{imported_week}' importada com sucesso!"), bgcolor="green")
                page.snack_bar.open = True
                status_text.value = "Importação concluída."
                
                update_week_dropdown(selected_week=imported_week)
                tasks = listar_tarefas(semana=imported_week)
                update_tasks_table(tasks)

        except Exception as ex:
            page.snack_bar = ft.SnackBar(content=ft.Text(f"Erro: {ex}"), bgcolor="red")
            page.snack_bar.open = True
            status_text.value = "Erro fatal."
            print(f"Erro detalhado: {ex}")
        finally:
            import_button.disabled = False
            progress_ring.visible = False
            page.update()

    # --- Definição dos Componentes ---

    url_field = ft.TextField(
        label="URL da Semana (JW.org)",
        hint_text="Cole a URL aqui...",
        expand=True,
        autofocus=True,
        prefix_icon="link"  # String simples
    )

    # Usando FilledButton que é mais moderno e evita avisos de deprecated
    import_button = ft.FilledButton(
        content=ft.Row([
            ft.Icon("download"), 
            ft.Text("Importar")
        ], tight=True, spacing=8),
        on_click=import_button_clicked,
        tooltip="Baixar e salvar dados da URL"
    )
    
    progress_ring = ft.ProgressRing(visible=False, width=20, height=20, stroke_width=2)
    status_text = ft.Text("", size=12, color="grey")

    week_dropdown = ft.Dropdown(
        label="Semanas Salvas",
        expand=True,
        disabled=True,
    )
    week_dropdown.on_change = week_changed
    
    tasks_table = ft.DataTable(
        columns=[
            ft.DataColumn(ft.Text("Seção", weight=ft.FontWeight.BOLD)),
            ft.DataColumn(ft.Text("Título da Tarefa", weight=ft.FontWeight.BOLD)),
            ft.DataColumn(ft.Text("Tempo", weight=ft.FontWeight.BOLD)),
        ],
        rows=[],
        expand=True,
        column_spacing=20,
        # CORREÇÃO: Usando string "grey200" em vez de enum complexo
        heading_row_color="grey200", 
        border=ft.Border.all(1, "grey400"),
        border_radius=ft.BorderRadius.all(8),
    )
    
    # --- Montagem do Layout ---

    page.add(
        ft.Row([
            ft.Text("Importação", size=20, weight="bold"),
        ]),
        ft.Row(controls=[
            url_field, 
            ft.Container(content=import_button, padding=ft.Padding.only(top=0)), 
            ft.Container(content=progress_ring, padding=ft.Padding.only(top=10, left=10))
        ], alignment=ft.MainAxisAlignment.START, vertical_alignment=ft.CrossAxisAlignment.CENTER),
        
        status_text,
        ft.Divider(height=20),
        
        ft.Row([
            ft.Text("Visualização", size=20, weight="bold"),
            ft.Container(width=20),
            week_dropdown
        ]),
        
        ft.Column(controls=[tasks_table], expand=True, scroll=ft.ScrollMode.ADAPTIVE)
    )

    # --- Carga Inicial ---
    init_db()
    
    try:
        semanas = get_semanas()
        if semanas:
            update_week_dropdown(semanas[0])
        else:
            update_week_dropdown()
    except:
        pass

if __name__ == "__main__":
    # A mensagem pedia "Use run() instead", então vamos usar a forma nova
    ft.app(target=main)