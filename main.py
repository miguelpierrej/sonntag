import json
import os
import threading
import tkinter as tk
from tkinter import messagebox
from scrapper.web_scrapper import DataScrapper

# Bibliotecas para PDF
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Paragraph, Spacer
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle

class ProgramEditor:
    def __init__(self, data):
        self.window = tk.Toplevel()
        self.window.title(f"Editar Programa: {data['metadata']['data']}")
        self.window.geometry("850x900")
        
        self.data = data
        self.entries = {}
        
        # Garante que as pastas existam
        os.makedirs("json", exist_ok=True)
        os.makedirs("pdf", exist_ok=True)
        
        # Define o caminho do arquivo dentro da pasta 'json'
        safe_date = self.data['metadata']['data'].replace(' ', '_').replace('/', '-')
        self.filename = os.path.join("json", f"assignments_{safe_date}.json")

        self.load_existing_assignments()

        self.main_canvas = tk.Canvas(self.window)
        self.scrollbar = tk.Scrollbar(self.window, orient="vertical", command=self.main_canvas.yview)
        self.scrollable_frame = tk.Frame(self.main_canvas)

        self.scrollable_frame.bind(
            "<Configure>",
            lambda e: self.main_canvas.configure(scrollregion=self.main_canvas.bbox("all"))
        )

        self.main_canvas.create_window((0, 0), window=self.scrollable_frame, anchor="nw")
        self.main_canvas.configure(yscrollcommand=self.scrollbar.set)

        self.setup_ui()

        self.main_canvas.pack(side="left", fill="both", expand=True)
        self.scrollbar.pack(side="right", fill="y")

    def load_existing_assignments(self):
        if os.path.exists(self.filename):
            try:
                with open(self.filename, 'r', encoding='utf-8') as f:
                    self.data = json.load(f)
            except:
                pass

    def setup_ui(self):
        tk.Label(self.scrollable_frame, text="PROGRAMA PARA LA REUNIÓN VIDA Y MINISTERIO CRISTIANOS", 
                 font=("Arial", 14, "bold"), fg="#1a5276").pack(pady=10)
        
        header_frame = tk.LabelFrame(self.scrollable_frame, text="Asignaciones Fijas", padx=10, pady=5)
        header_frame.pack(fill="x", padx=30, pady=5)
        
        tk.Label(header_frame, text="Presidente:").grid(row=0, column=0)
        self.chairman_entry = tk.Entry(header_frame, width=25)
        self.chairman_entry.insert(0, self.data.get('presidente', ''))
        self.chairman_entry.grid(row=0, column=1, padx=5)

        tk.Label(header_frame, text="Oración Inicial:").grid(row=0, column=2)
        self.prayer_entry = tk.Entry(header_frame, width=25)
        self.prayer_entry.insert(0, self.data.get('oracao_inicial', ''))
        self.prayer_entry.grid(row=0, column=3, padx=5)

        tk.Label(header_frame, text="Oración Final:").grid(row=1, column=2, pady=5)
        self.final_prayer_entry = tk.Entry(header_frame, width=25)
        self.final_prayer_entry.insert(0, self.data.get('oracao_final', ''))
        self.final_prayer_entry.grid(row=1, column=3, padx=5)

        for s_idx, section in enumerate(self.data['secoes']):
            section_frame = tk.LabelFrame(self.scrollable_frame, text=section['titulo'], 
                                         font=("Arial", 11, "bold"), padx=10, pady=10)
            section_frame.pack(fill="x", padx=30, pady=10)

            for i_idx, item in enumerate(section['itens']):
                item_row = tk.Frame(section_frame)
                item_row.pack(fill="x", pady=4)

                original_text = item.split(" [ASSIGNED:")[0]
                current_assignment = item.split(" [ASSIGNED: ")[1].replace("]", "") if " [ASSIGNED: " in item else ""

                tk.Label(item_row, text=original_text, wraplength=400, justify="left", width=50, anchor="w").pack(side="left")
                
                entry_key = f"{s_idx}_{i_idx}"
                name_entry = tk.Entry(item_row, width=25)
                
                if "Canción" in original_text:
                    name_entry.config(state='disabled', bg="#f0f0f0")
                else:
                    name_entry.insert(0, current_assignment)
                
                name_entry.pack(side="right", padx=5)
                self.entries[entry_key] = (name_entry, original_text)

        button_container = tk.Frame(self.scrollable_frame)
        button_container.pack(pady=30)

        tk.Button(button_container, text="GUARDAR CAMBIOS", bg="#27ae60", fg="white", 
                  font=("Arial", 10, "bold"), command=self.save_and_update, width=25).grid(row=0, column=0, padx=10)

        tk.Button(button_container, text="GENERAR PDF (S-140)", bg="#2980b9", fg="white", 
                  font=("Arial", 10, "bold"), command=self.export_to_pdf, width=25).grid(row=0, column=1, padx=10)

    def save_and_update(self):
        self.data['presidente'] = self.chairman_entry.get().strip()
        self.data['oracao_inicial'] = self.prayer_entry.get().strip()
        self.data['oracao_final'] = self.final_prayer_entry.get().strip()

        for key, (entry, original_text) in self.entries.items():
            s_idx, i_idx = map(int, key.split('_'))
            if entry.cget('state') != 'disabled':
                new_name = entry.get().strip()
                self.data['secoes'][s_idx]['itens'][i_idx] = f"{original_text} [ASSIGNED: {new_name}]" if new_name else original_text
            else:
                self.data['secoes'][s_idx]['itens'][i_idx] = original_text

        with open(self.filename, 'w', encoding='utf-8') as f:
            json.dump(self.data, f, indent=4, ensure_ascii=False)
        messagebox.showinfo("Éxito", "¡Datos guardados correctamente en la carpeta JSON!")
        self.window.destroy()

    def export_to_pdf(self):
        current_chairman = self.chairman_entry.get().strip()
        current_prayer_init = self.prayer_entry.get().strip()
        current_prayer_final = self.final_prayer_entry.get().strip()

        # Define o caminho do PDF dentro da pasta 'pdf'
        pdf_name = f"S-140_{self.data['metadata']['data'].replace(' ', '_')}.pdf"
        pdf_path = os.path.join("pdf", pdf_name)
        
        doc = SimpleDocTemplate(pdf_path, pagesize=A4, leftMargin=30, rightMargin=30, topMargin=30, bottomMargin=30)
        elements = []
        styles = getSampleStyleSheet()
        
        color_treasures = colors.HexColor("#606060")     
        color_ministry = colors.HexColor("#C18600")      
        color_christian_life = colors.HexColor("#942932") 

        section_style = ParagraphStyle('Section', fontSize=10, textColor=colors.white, fontName='Helvetica-Bold', alignment=1)
        item_style = ParagraphStyle('Item', fontSize=9)

        elements.append(Paragraph("<b>PROGRAMA PARA LA REUNIÓN VIDA Y MINISTERIO CRISTIANOS</b>", ParagraphStyle('H', fontSize=11, alignment=1)))
        elements.append(Spacer(1, 10))
        header_data = [[Paragraph(f"<b>{self.data['metadata']['data']} | {self.data['metadata']['texto_biblico']}</b>", styles['Normal']), 
                        f"Presidente: {current_chairman}"]]
        elements.append(Table(header_data, colWidths=[380, 160]))

        intro_raw = self.data['metadata']['introducao'].split("|")
        table_data = []
        table_data.append([Paragraph(f"<b>{intro_raw[0].strip()}</b>", item_style), f"Oración: {current_prayer_init}"])
        table_data.append([Paragraph(intro_raw[1].strip() if len(intro_raw)>1 else "", item_style), ""])

        for s_idx, section in enumerate(self.data['secoes']):
            title = section['titulo'].upper()
            bg_color = color_treasures
            if "MAESTROS" in title or "MINISTÉRIO" in title or "MEJORES" in title: bg_color = color_ministry
            elif "VIDA" in title: bg_color = color_christian_life
            table_data.append([Paragraph(title, section_style), ""]) 
            for i_idx, item in enumerate(section['itens']):
                entry_key = f"{s_idx}_{i_idx}"
                assigned_name = self.entries[entry_key][0].get() if self.entries[entry_key][0].cget('state') != 'disabled' else ""
                original_text = self.entries[entry_key][1]
                table_data.append([Paragraph(original_text, item_style), assigned_name])

        concl_raw = self.data['conclusao'].split("|")
        table_data.append([Paragraph("CONCLUSIÓN", section_style), ""])
        table_data.append([Paragraph(concl_raw[0].strip(), item_style), ""])
        table_data.append([Paragraph(f"<b>{concl_raw[1].strip() if len(concl_raw)>1 else ''}</b>", item_style), f"Oración: {current_prayer_final}"])

        t = Table(table_data, colWidths=[400, 140])
        style = TableStyle([
            ('GRID', (0, 0), (-1, -1), 0.5, colors.grey),
            ('VALIGN', (0, 0), (-1, -1), 'MIDDLE'),
            ('BOTTOMPADDING', (0, 0), (-1, -1), 6),
        ])

        curr_row = 2 
        for section in self.data['secoes']:
            title = section['titulo'].upper()
            color = color_treasures
            if "MAESTROS" in title or "MINISTÉRIO" in title or "MEJORES" in title: color = color_ministry
            elif "VIDA" in title: color = color_christian_life
            style.add('BACKGROUND', (0, curr_row), (-1, curr_row), color)
            style.add('SPAN', (0, curr_row), (1, curr_row)) 
            curr_row += len(section['itens']) + 1
        style.add('BACKGROUND', (0, curr_row), (-1, curr_row), colors.black)
        style.add('SPAN', (0, curr_row), (1, curr_row))

        t.setStyle(style)
        elements.append(t)
        
        doc.build(elements)
        messagebox.showinfo("Éxito", f"PDF generado en la carpeta PDF: {pdf_name}")

class ProgramApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Schedule Manager")
        self.root.geometry("480x520")
        self.scrapper = DataScrapper()
        self.json_history = os.path.join("json", "saved_schedules.json")
        
        os.makedirs("json", exist_ok=True)
        os.makedirs("pdf", exist_ok=True)
        
        btn_config = {"font": ("Arial", 10), "width": 30, "pady": 5, "cursor": "hand2"}
        tk.Label(root, text="MAIN MENU", font=("Arial", 14, "bold"), pady=20).pack()

        extract_frame = tk.LabelFrame(root, text="Extraction", padx=10, pady=10)
        extract_frame.pack(fill="x", padx=20, pady=5)
        tk.Button(extract_frame, text="Extract This Week", command=self.extract_week, **btn_config).pack(pady=2)
        tk.Button(extract_frame, text="Extract Month", command=self.extract_month, **btn_config).pack(pady=2)
        tk.Button(extract_frame, text="Extract All Available", command=self.extract_all, bg="#fff3cd", **btn_config).pack(pady=2)

        view_frame = tk.LabelFrame(root, text="Saved Data", padx=10, pady=10)
        view_frame.pack(fill="x", padx=20, pady=10)
        tk.Button(view_frame, text="View Saved Schedules", command=self.view_saved, bg="#d1e7ff", **btn_config).pack(pady=2)

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

    def extract_week(self):
        threading.Thread(target=self._run_task, args=(self.scrapper.extract_this_week,), daemon=True).start()

    def extract_month(self):
        threading.Thread(target=self._run_task, args=(self.scrapper.extract_this_month,), daemon=True).start()

    def extract_all(self):
        threading.Thread(target=self._run_task, args=(self.scrapper.extract_all_available_weeks,), daemon=True).start()

    def _run_task(self, task_func):
        data = task_func()
        if data:
            self.save_to_history(data)
            self.root.after(0, lambda: messagebox.showinfo("Éxito", "¡Datos extraídos y guardados en JSON!"))

    def view_saved(self):
        if os.path.exists(self.json_history):
            with open(self.json_history, 'r', encoding='utf-8') as f:
                data = json.load(f)
            self.show_selector(data)

    def show_selector(self, data):
        win = tk.Toplevel(self.root)
        win.title("Seleccionar Fecha")
        for item in data:
            tk.Button(win, text=item['metadata']['data'], width=40, command=lambda d=item: ProgramEditor(d)).pack(pady=2)

if __name__ == "__main__":
    app_root = tk.Tk()
    app = ProgramApp(app_root)
    app_root.mainloop()