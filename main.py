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


def main(page: ft.Page):
    app = ProgramApp(page)

if __name__ == "__main__":
    ft.run(main)