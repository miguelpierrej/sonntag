import flet as ft
import json
import os
import threading
from scrapper.web_scrapper import DataScrapper

# Bibliotecas para PDF
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Paragraph, Spacer
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle

class ProgramApp:
    def __init__(self, page: ft.Page):
        self.page = page
        self.page.title = "Schedule Manager"
        self.page.window_width = 900
        self.page.window_height = 700
        self.page.padding = 0
        self.page.theme_mode = ft.ThemeMode.DARK
        self.page.bgcolor = "#0a0e1a"
        self.scrapper = DataScrapper()
        self.json_history = os.path.join("json", "saved_schedules.json")
        
        os.makedirs("json", exist_ok=True)
        os.makedirs("pdf", exist_ok=True)
        
        self.show_main_menu()

    def show_main_menu(self):
        self.page.controls.clear()
        
        # Hero Section - Cabeçalho de boas-vindas
        hero_section = ft.Container(
            content=ft.Column([
                ft.Icon(
                    ft.Icons.CALENDAR_MONTH,
                    size=60,
                    color="#6366f1"
                ),
                ft.Text(
                    "Bem-vindo ao Schedule Manager",
                    size=32,
                    weight=ft.FontWeight.BOLD,
                    color="#ffffff",
                    text_align=ft.TextAlign.CENTER
                ),
                ft.Text(
                    "Organize designações de forma simples, clara e sem conflitos.",
                    size=16,
                    color="#94a3b8",
                    text_align=ft.TextAlign.CENTER
                ),
            ],
            horizontal_alignment=ft.CrossAxisAlignment.CENTER,
            spacing=12
            ),
            padding=ft.Padding(top=40, bottom=40),
            alignment=ft.Alignment.CENTER
        )
        
        # Função para criar cards clicáveis
        def create_card(title, description, icon, on_click, is_primary=False):
            card_bg = "#1e293b" if not is_primary else "#312e81"
            hover_bg = "#334155" if not is_primary else "#4338ca"
            
            card = ft.Container(
                content=ft.Column([
                    ft.Icon(
                        icon,
                        size=40,
                        color="#6366f1" if not is_primary else "#818cf8"
                    ),
                    ft.Text(
                        title,
                        size=16,
                        weight=ft.FontWeight.BOLD,
                        color="#f1f5f9",
                        text_align=ft.TextAlign.CENTER
                    ),
                    ft.Text(
                        description,
                        size=12,
                        color="#94a3b8",
                        text_align=ft.TextAlign.CENTER
                    ),
                ],
                horizontal_alignment=ft.CrossAxisAlignment.CENTER,
                spacing=8
                ),
                bgcolor=card_bg,
                border_radius=16,
                padding=24,
                width=250,
                height=180,
                ink=True,
                on_click=on_click,
                animate=ft.Animation(200, "easeOut"),
                shadow=ft.BoxShadow(
                    spread_radius=1,
                    blur_radius=15,
                    color="#00000050",
                    offset=ft.Offset(0, 4),
                )
            )
            
            # Adicionar efeito hover
            def on_hover(e):
                card.bgcolor = hover_bg if e.data == "true" else card_bg
                card.elevation = 8 if e.data == "true" else 0
                card.update()
            
            card.on_hover = on_hover
            return card
        
        # Grid de cards principais
        cards_grid = ft.Column([
            ft.Row([
                create_card(
                    "Designações de Vida e Ministério",
                    "Planejamento e organização das reuniões",
                    ft.Icons.BOOK,
                    self.show_vida_ministerio,
                    is_primary=True
                ),
                create_card(
                    "Áudio, Vídeo e Indicadores",
                    "Controle de equipamentos e responsáveis",
                    ft.Icons.VIDEOCAM,
                    self.show_audio_video
                ),
                create_card(
                    "Designações de Limpeza",
                    "Escalas organizadas por período",
                    ft.Icons.CLEANING_SERVICES,
                    self.show_limpeza
                ),
            ],
            alignment=ft.MainAxisAlignment.CENTER,
            spacing=20
            ),
            ft.Row([
                create_card(
                    "Saída de Campo",
                    "Organização de grupos e responsáveis",
                    ft.Icons.DIRECTIONS_WALK,
                    self.show_saida_campo
                ),
                create_card(
                    "Saída de Carrinho",
                    "Controle específico de designações externas",
                    ft.Icons.LOCAL_SHIPPING,
                    self.show_saida_carrinho
                ),
            ],
            alignment=ft.MainAxisAlignment.CENTER,
            spacing=20
            ),
        ],
        spacing=20,
        horizontal_alignment=ft.CrossAxisAlignment.CENTER
        )
        
        # Container principal com scroll
        main_container = ft.Column([
            hero_section,
            cards_grid,
        ],
        horizontal_alignment=ft.CrossAxisAlignment.CENTER,
        spacing=20,
        scroll=ft.ScrollMode.AUTO
        )
        
        self.page.add(main_container)
        self.page.update()

    def show_vida_ministerio(self, e):
        self.page.controls.clear()
        
        # Botão de voltar estilizado
        back_button = ft.Container(
            content=ft.Row([
                ft.Icon(ft.Icons.ARROW_BACK, size=20, color="#6366f1"),
                ft.Text("Voltar ao Menu", size=14, color="#6366f1", weight=ft.FontWeight.BOLD)
            ], spacing=8),
            bgcolor="#1e293b",
            border_radius=8,
            padding=ft.Padding(left=16, right=16, top=10, bottom=10),
            ink=True,
            on_click=lambda _: self.show_main_menu()
        )
        
        # Cabeçalho da seção
        header = ft.Container(
            content=ft.Column([
                ft.Icon(ft.Icons.BOOK, size=50, color="#6366f1"),
                ft.Text(
                    "Designações de Vida e Ministério",
                    size=24,
                    weight=ft.FontWeight.BOLD,
                    color="#ffffff"
                ),
                ft.Text(
                    "Extraia e visualize designações das reuniões",
                    size=14,
                    color="#94a3b8"
                ),
            ],
            horizontal_alignment=ft.CrossAxisAlignment.CENTER,
            spacing=8
            ),
            padding=ft.Padding(top=20, bottom=30),
            alignment=ft.Alignment.CENTER
        )
        
        # Botões de ação estilizados
        def create_action_button(text, icon, on_click):
            btn_content = ft.Row([
                ft.Icon(icon, size=24, color="#6366f1"),
                ft.Text(text, size=15, color="#f1f5f9", weight=ft.FontWeight.W_500)
            ], spacing=12)
            
            btn = ft.Container(
                content=btn_content,
                bgcolor="#1e293b",
                border_radius=12,
                padding=16,
                width=400,
                ink=True,
                on_click=on_click,
                shadow=ft.BoxShadow(
                    spread_radius=0,
                    blur_radius=10,
                    color="#00000033",
                    offset=ft.Offset(0, 2),
                )
            )
            
            def on_hover(e):
                btn.bgcolor = "#334155" if e.data == "true" else "#1e293b"
                btn.update()
            
            btn.on_hover = on_hover
            return btn
        
        buttons_column = ft.Column([
            create_action_button("Extract This Week", ft.Icons.CALENDAR_TODAY, self.extract_week),
            create_action_button("Extract Month", ft.Icons.CALENDAR_MONTH, self.extract_month),
            create_action_button("Extract All Available", ft.Icons.DOWNLOAD, self.extract_all),
            create_action_button("View Saved Schedules", ft.Icons.FOLDER_OPEN, self.view_saved),
        ], spacing=15, horizontal_alignment=ft.CrossAxisAlignment.CENTER)
        
        main_content = ft.Column([
            ft.Container(back_button, padding=ft.Padding(left=20, top=20)),
            header,
            buttons_column,
        ],
        horizontal_alignment=ft.CrossAxisAlignment.CENTER,
        spacing=10,
        scroll=ft.ScrollMode.AUTO
        )
        
        self.page.add(main_content)
        self.page.update()

    def show_audio_video(self, e):
        self.page.controls.clear()
        
        back_button = ft.Container(
            content=ft.Row([
                ft.Icon(ft.Icons.ARROW_BACK, size=20, color="#6366f1"),
                ft.Text("Voltar ao Menu", size=14, color="#6366f1", weight=ft.FontWeight.BOLD)
            ], spacing=8),
            bgcolor="#1e293b",
            border_radius=8,
            padding=ft.Padding(left=16, right=16, top=10, bottom=10),
            ink=True,
            on_click=lambda _: self.show_main_menu()
        )
        
        content = ft.Column([
            ft.Container(back_button, padding=ft.Padding(left=20, top=20)),
            ft.Container(
                content=ft.Column([
                    ft.Icon(ft.Icons.VIDEOCAM, size=50, color="#6366f1"),
                    ft.Text("Designações de Áudio, Vídeo e Indicadores", size=24, weight=ft.FontWeight.BOLD, color="#ffffff", text_align=ft.TextAlign.CENTER),
                    ft.Text("Controle de equipamentos e responsáveis", size=14, color="#94a3b8"),
                    ft.Container(height=20),
                    ft.Text("Em desenvolvimento...", size=16, color="#818cf8", italic=True)
                ], horizontal_alignment=ft.CrossAxisAlignment.CENTER, spacing=8),
                padding=ft.Padding(top=40),
                alignment=ft.Alignment.CENTER
            )
        ], horizontal_alignment=ft.CrossAxisAlignment.CENTER)
        
        self.page.add(content)
        self.page.update()

    def show_limpeza(self, e):
        self.page.controls.clear()
        
        back_button = ft.Container(
            content=ft.Row([
                ft.Icon(ft.Icons.ARROW_BACK, size=20, color="#6366f1"),
                ft.Text("Voltar ao Menu", size=14, color="#6366f1", weight=ft.FontWeight.BOLD)
            ], spacing=8),
            bgcolor="#1e293b",
            border_radius=8,
            padding=ft.Padding(left=16, right=16, top=10, bottom=10),
            ink=True,
            on_click=lambda _: self.show_main_menu()
        )
        
        content = ft.Column([
            ft.Container(back_button, padding=ft.Padding(left=20, top=20)),
            ft.Container(
                content=ft.Column([
                    ft.Icon(ft.Icons.CLEANING_SERVICES, size=50, color="#6366f1"),
                    ft.Text("Designações de Limpeza", size=24, weight=ft.FontWeight.BOLD, color="#ffffff"),
                    ft.Text("Escalas organizadas por período", size=14, color="#94a3b8"),
                    ft.Container(height=20),
                    ft.Text("Em desenvolvimento...", size=16, color="#818cf8", italic=True)
                ], horizontal_alignment=ft.CrossAxisAlignment.CENTER, spacing=8),
                padding=ft.Padding(top=40),
                alignment=ft.Alignment.CENTER
            )
        ], horizontal_alignment=ft.CrossAxisAlignment.CENTER)
        
        self.page.add(content)
        self.page.update()

    def show_saida_campo(self, e):
        self.page.controls.clear()
        
        back_button = ft.Container(
            content=ft.Row([
                ft.Icon(ft.Icons.ARROW_BACK, size=20, color="#6366f1"),
                ft.Text("Voltar ao Menu", size=14, color="#6366f1", weight=ft.FontWeight.BOLD)
            ], spacing=8),
            bgcolor="#1e293b",
            border_radius=8,
            padding=ft.Padding(left=16, right=16, top=10, bottom=10),
            ink=True,
            on_click=lambda _: self.show_main_menu()
        )
        
        content = ft.Column([
            ft.Container(back_button, padding=ft.Padding(left=20, top=20)),
            ft.Container(
                content=ft.Column([
                    ft.Icon(ft.Icons.DIRECTIONS_WALK, size=50, color="#6366f1"),
                    ft.Text("Saída de Campo", size=24, weight=ft.FontWeight.BOLD, color="#ffffff"),
                    ft.Text("Organização de grupos e responsáveis", size=14, color="#94a3b8"),
                    ft.Container(height=20),
                    ft.Text("Em desenvolvimento...", size=16, color="#818cf8", italic=True)
                ], horizontal_alignment=ft.CrossAxisAlignment.CENTER, spacing=8),
                padding=ft.Padding(top=40),
                alignment=ft.Alignment.CENTER
            )
        ], horizontal_alignment=ft.CrossAxisAlignment.CENTER)
        
        self.page.add(content)
        self.page.update()

    def show_saida_carrinho(self, e):
        self.page.controls.clear()
        
        back_button = ft.Container(
            content=ft.Row([
                ft.Icon(ft.Icons.ARROW_BACK, size=20, color="#6366f1"),
                ft.Text("Voltar ao Menu", size=14, color="#6366f1", weight=ft.FontWeight.BOLD)
            ], spacing=8),
            bgcolor="#1e293b",
            border_radius=8,
            padding=ft.Padding(left=16, right=16, top=10, bottom=10),
            ink=True,
            on_click=lambda _: self.show_main_menu()
        )
        
        content = ft.Column([
            ft.Container(back_button, padding=ft.Padding(left=20, top=20)),
            ft.Container(
                content=ft.Column([
                    ft.Icon(ft.Icons.LOCAL_SHIPPING, size=50, color="#6366f1"),
                    ft.Text("Saída de Carrinho", size=24, weight=ft.FontWeight.BOLD, color="#ffffff"),
                    ft.Text("Controle específico de designações externas", size=14, color="#94a3b8"),
                    ft.Container(height=20),
                    ft.Text("Em desenvolvimento...", size=16, color="#818cf8", italic=True)
                ], horizontal_alignment=ft.CrossAxisAlignment.CENTER, spacing=8),
                padding=ft.Padding(top=40),
                alignment=ft.Alignment.CENTER
            )
        ], horizontal_alignment=ft.CrossAxisAlignment.CENTER)
        
        self.page.add(content)
        self.page.update()


    def save_to_history(self, new_data):
        if not isinstance(new_data, list): new_data = [new_data]
        total = []
        if os.path.exists(self.json_history):
            with open(self.json_history, 'r', encoding='utf-8') as f:
                try: total = json.load(f)
                except: total = []
        
        dates = {d['metadata']['data'] for d in total}
        for d in new_data:
            if d['metadata']['data'] not in dates: total.append(d)
        
        with open(self.json_history, 'w', encoding='utf-8') as f:
            json.dump(total, f, indent=4, ensure_ascii=False)

    def extract_week(self, e):
        threading.Thread(target=self._run_task, args=(self.scrapper.extract_this_week,), daemon=True).start()

    def extract_month(self, e):
        threading.Thread(target=self._run_task, args=(self.scrapper.extract_this_month,), daemon=True).start()

    def extract_all(self, e):
        threading.Thread(target=self._run_task, args=(self.scrapper.extract_all_available_weeks,), daemon=True).start()

    def _run_task(self, task_func):
        try:
            data = task_func()
            if data:
                self.save_to_history(data)
                # Mostrar notificação de sucesso
                self.page.snack_bar = ft.SnackBar(
                    ft.Text("✓ Dados extraídos e salvos com sucesso!", color="#22c55e"),
                    bgcolor="#0f172a"
                )
                self.page.snack_bar.open = True
                self.page.update()
            else:
                # Mostrar notificação de nenhum dado
                self.page.snack_bar = ft.SnackBar(
                    ft.Text("⚠ Nenhum dado foi encontrado", color="#f59e0b"),
                    bgcolor="#0f172a"
                )
                self.page.snack_bar.open = True
                self.page.update()
        except Exception as e:
            # Mostrar erro
            print(f"Erro ao extrair dados: {str(e)}")
            self.page.snack_bar = ft.SnackBar(
                ft.Text(f"✗ Erro: {str(e)}", color="#ef4444"),
                bgcolor="#0f172a"
            )
            self.page.snack_bar.open = True
            self.page.update()

    def view_saved(self, e):
        if os.path.exists(self.json_history):
            with open(self.json_history, 'r', encoding='utf-8') as f:
                data = json.load(f)
            # self.show_selector(data)


    def show_selector(self, data_list):
        self.page.controls.clear()
        
        # Dicionário para guardar as referências dos inputs (TextFields)
        self.input_controls = {} 

        # Ordenar dados por data
        try:
            data_list.sort(key=lambda x: x['metadata'].get('data', ''), reverse=True)
        except:
            pass 

        # --- Elementos de UI ---
        self.detail_container = ft.Column(scroll=ft.ScrollMode.AUTO, expand=True, spacing=10)
        
        # Placeholder inicial
        self.detail_container.controls = [
            ft.Container(
                content=ft.Column([
                    ft.Icon(ft.Icons.TOUCH_APP, size=50, color="#6366f1"),
                    ft.Text("Selecione uma semana ao lado para editar.", color="#94a3b8")
                ], horizontal_alignment=ft.CrossAxisAlignment.CENTER),
                alignment=ft.Alignment(0, 0),
                expand=True,
                padding=50
            )
        ]

        # Função para carregar os detalhes e criar os INPUTS
        def load_details(e, item_data):
            self.detail_container.controls.clear()
            self.input_controls = {} # Limpa inputs anteriores
            
            meta = item_data.get('metadata', {})
            secoes = item_data.get('secoes', [])
            
            # Guardamos os dados brutos atuais para usar no PDF
            self.current_data_context = item_data 

            # 1. Cabeçalho
            header = ft.Container(
                content=ft.Row([
                    ft.Column([
                        ft.Text(f"Semana de {meta.get('data', 'Data N/D')}", size=22, weight=ft.FontWeight.BOLD, color="white"),
                        ft.Text(f"Leitura: {meta.get('texto_biblico', '')}", size=14, color="#94a3b8"),
                    ], spacing=2),
                    ft.ElevatedButton(
                        "Gerar PDF",
                        icon=ft.Icons.PICTURE_AS_PDF,
                        bgcolor="#ef4444",
                        color="white",
                        on_click=self.generate_pdf_action
                    )
                ], alignment=ft.MainAxisAlignment.SPACE_BETWEEN),
                bgcolor="#1e293b", padding=20, border_radius=10
            )
            self.detail_container.controls.append(header)

            # 2. Renderizar Seções com INPUTS
            for secao in secoes:
                titulo = secao.get('titulo', '').upper()
                itens = secao.get('itens', [])
                
                # Inicializa lista de controles para esta seção
                self.input_controls[titulo] = []

                # Lógica de cores/ícones
                icon = ft.Icons.CIRCLE
                color_theme = "#94a3b8"
                if "TESOROS" in titulo or "TESOUROS" in titulo:
                    icon, color_theme = ft.Icons.DIAMOND, "#a5b4fc"
                elif "MAESTROS" in titulo or "MINISTÉRIO" in titulo:
                    icon, color_theme = ft.Icons.WORK, "#fbbf24"
                elif "VIDA" in titulo or "CRISTIANA" in titulo:
                    icon, color_theme = ft.Icons.FAVORITE, "#f87171"

                rows_content = []
                for idx, item_texto in enumerate(itens):
                    # Criar TextFields para editar
                    txt_nome = ft.TextField(label="Designado", height=40, text_size=12, expand=True, bgcolor="#0f172a", border_color="#334155")
                    txt_ajudante = ft.TextField(label="Ajudante/Sala", height=40, text_size=12, width=150, bgcolor="#0f172a", border_color="#334155")
                    
                    # Guarda a referência para pegarmos o valor depois
                    self.input_controls[titulo].append({'nome': txt_nome, 'ajudante': txt_ajudante})

                    # Layout do Item
                    rows_content.append(
                        ft.Container(
                            content=ft.Column([
                                ft.Text(item_texto, color="#e2e8f0", size=14, weight=ft.FontWeight.BOLD),
                                ft.Row([txt_nome, txt_ajudante])
                            ], spacing=5),
                            padding=15,
                            bgcolor="#1e293b", 
                            border=ft.Border(left=ft.BorderSide(4, color_theme)),
                            border_radius=4
                        )
                    )

                self.detail_container.controls.append(
                    ft.Container(
                        content=ft.Column([
                            ft.Row([ft.Icon(icon, color=color_theme), ft.Text(titulo, size=16, weight=ft.FontWeight.BOLD, color=color_theme)]),
                            ft.Column(rows_content, spacing=10)
                        ], spacing=10),
                        # CORREÇÃO AQUI: Substituído ft.margin.only por ft.Margin
                        padding=10, 
                        margin=ft.Margin(0, 0, 0, 10) 
                    )
                )

            self.detail_container.update()

        # --- Lista Lateral ---
        date_list_view = ft.ListView(expand=True, spacing=5, padding=10)
        for item in data_list:
            data_str = item.get('metadata', {}).get('data', 'Sem data')
            btn = ft.Container(
                content=ft.Row([ft.Icon(ft.Icons.CALENDAR_TODAY, size=16, color="#94a3b8"), ft.Text(data_str, color="white", size=13)]),
                padding=15, border_radius=8, bgcolor="#1e293b", ink=True,
                on_click=lambda e, i=item: load_details(e, i)
            )
            date_list_view.controls.append(btn)

        # Layout Principal
        layout = ft.Column([
            ft.Container(content=ft.Row([ft.IconButton(ft.Icons.ARROW_BACK, on_click=lambda _: self.show_vida_ministerio(None)), ft.Text("Voltar", size=16, weight=ft.FontWeight.BOLD)]), padding=10),
            ft.Container(expand=True, content=ft.Row([
                ft.Container(content=date_list_view, width=250, bgcolor="#0f172a", border=ft.Border(right=ft.BorderSide(1, "#334155"))),
                ft.Container(content=self.detail_container, expand=True, padding=20)
            ], expand=True))
        ], expand=True)

        self.page.add(layout)
        self.page.update()

    def generate_pdf_action(self, e):
        """Coleta os dados dos inputs e chama o gerador de PDF"""
        if not hasattr(self, 'current_data_context') or not self.current_data_context:
            return

        pdf_data = self.current_data_context.copy()
        
        # Preenche os dados com o que o usuário digitou nos TextFields
        for secao in pdf_data['secoes']:
            titulo = secao.get('titulo', '').upper()
            if titulo in self.input_controls:
                for idx, item in enumerate(secao['itens']):
                    controls = self.input_controls[titulo][idx]
                    
                    secao['itens'][idx] = {
                        'parte': item,
                        'nome': controls['nome'].value,
                        'ajudante': controls['ajudante'].value
                    }

        filename = f"Designacao_{pdf_data['metadata']['data'].replace(' ', '_')}.pdf"
        filepath = os.path.join("pdf", filename)
        self.create_pdf_file(filepath, pdf_data)
        
        self.page.snack_bar = ft.SnackBar(ft.Text(f"PDF salvo em: {filepath}", color="white"), bgcolor="#22c55e")
        self.page.snack_bar.open = True
        self.page.update()

    def create_pdf_file(self, filename, data):
        """Usa ReportLab para desenhar o PDF"""
        doc = SimpleDocTemplate(filename, pagesize=A4)
        elements = []
        styles = getSampleStyleSheet()
        
        # CORREÇÃO: Substituído colors.hexval por colors.HexColor
        title_style = ParagraphStyle('TitleCustom', parent=styles['Title'], fontSize=18, textColor=colors.HexColor("#2c3e50"), spaceAfter=10)
        subtitle_style = ParagraphStyle('SubtitleCustom', parent=styles['Normal'], fontSize=12, textColor=colors.gray, spaceAfter=20)
        # Header style não estava sendo usado explicitamente, mas corrigido igual
        
        # Cabeçalho do PDF
        meta = data['metadata']
        elements.append(Paragraph(f"Designações: Semana de {meta.get('data', '')}", title_style))
        elements.append(Paragraph(f"Leitura: {meta.get('texto_biblico', '')} | {meta.get('introducao', '')}", subtitle_style))
        elements.append(Spacer(1, 10))

        # Loop pelas seções
        for secao in data['secoes']:
            titulo = secao.get('titulo', '')
            
            # CORREÇÃO: Cores com HexColor
            bg_color = colors.HexColor("#7f8c8d")
            if "TESOROS" in titulo.upper(): bg_color = colors.HexColor("#6c5ce7")
            elif "MAESTROS" in titulo.upper(): bg_color = colors.HexColor("#f1c40f")
            elif "VIDA" in titulo.upper(): bg_color = colors.HexColor("#e74c3c")

            section_title = ParagraphStyle('SecTitle', parent=styles['Heading2'], fontSize=12, textColor=colors.white, backColor=bg_color, borderPadding=5, spaceAfter=5)
            elements.append(Paragraph(titulo, section_title))

            # Tabela de Designações
            table_data = []
            table_data.append(["Parte", "Designado / Ajudante"])

            for item in secao['itens']:
                parte_txt = item.get('parte', '')
                nome = item.get('nome', '')
                ajudante = item.get('ajudante', '')
                
                full_name = nome
                if ajudante:
                    full_name += f" / {ajudante}"
                
                if not full_name.strip():
                    full_name = "__________________________"

                p_parte = Paragraph(parte_txt, styles['Normal'])
                p_nome = Paragraph(f"<b>{full_name}</b>", styles['Normal'])
                
                table_data.append([p_parte, p_nome])

            t = Table(table_data, colWidths=[300, 180])
            t.setStyle(TableStyle([
                ('BACKGROUND', (0, 0), (-1, 0), colors.lightgrey),
                ('TEXTCOLOR', (0, 0), (-1, 0), colors.black),
                ('ALIGN', (0, 0), (-1, -1), 'LEFT'),
                ('VALIGN', (0, 0), (-1, -1), 'MIDDLE'),
                ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
                ('BOTTOMPADDING', (0, 0), (-1, 0), 8),
                ('GRID', (0, 0), (-1, -1), 0.5, colors.grey),
            ]))
            elements.append(t)
            elements.append(Spacer(1, 15))

        if 'conclusao' in data:
            elements.append(Paragraph(f"<b>Conclusão:</b> {data['conclusao']}", subtitle_style))

        doc.build(elements)

def main(page: ft.Page):
    app = ProgramApp(page)

if __name__ == "__main__":
    ft.run(main)
