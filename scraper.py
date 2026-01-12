import requests
from bs4 import BeautifulSoup, Tag
import re

class JwScraper:
    """
    Scrapes JW.ORG meeting schedule data from a given URL.
    """

    def _extract_time_from_title(self, text: str) -> str | None:
        """Extracts time like '(10 min.)' from a string."""
        match = re.search(r'\((\d+\s+min\.)\)', text)
        if match:
            return match.group(1)
        return None

    def _get_task_time_from_p(self, h3_tag: Tag) -> str | None:
        """Finds the time for a task in the subsequent <p> tag."""
        next_sibling = h3_tag.find_next_sibling()
        if next_sibling and next_sibling.name == 'p':
            time_text = next_sibling.get_text(strip=True)
            if "min." in time_text:
                return time_text.splitlines()[0] # Get first line in case of multiple lines
        return None

    def extrair_semana(self, url: str) -> list[dict]:
        """
        Extracts all tasks for a given week's meeting from the URL.

        Args:
            url: The URL of the meeting schedule page on JW.ORG.

        Returns:
            A list of dictionaries, where each dictionary represents a task.
            Returns an empty list if the page cannot be fetched or parsed.
        """
        try:
            response = requests.get(url, headers={'User-Agent': 'Mozilla/5.0'})
            response.raise_for_status()
        except requests.RequestException as e:
            print(f"Erro ao buscar a URL: {e}")
            return []

        soup = BeautifulSoup(response.text, 'html.parser')
        tarefas = []

        article_header = soup.select_one('article header h1')
        if not article_header:
            print("Não foi possível encontrar o cabeçalho da semana (tag h1).")
            return []
        semana = article_header.get_text(strip=True)

        content_area = soup.select_one('article .bodyTxt')
        if not content_area:
            print("Não foi possível encontrar a área de conteúdo (article .bodyTxt).")
            return []

        # Maps CSS class fragment to the section name (Tipo)
        section_map = {
            'teal-700': 'Tesouros da Palavra de Deus',
            'gold-700': 'Faça Seu Melhor no Ministério',
            'maroon-600': 'Nossa Vida Cristã',
        }

        current_section_type = None

        # Iterate through all relevant tags in document order
        for element in content_area.find_all(['h2', 'h3'], recursive=True):
            if element.name == 'h2':
                # When an h2 is found, check if it marks a new section
                class_str = ' '.join(element.get('class', []))
                section_found = False
                for color_class, section_name in section_map.items():
                    if color_class in class_str:
                        current_section_type = section_name
                        section_found = True
                        break
                if not section_found:
                    # This h2 might not be a section header we are interested in.
                    # Uncomment the line below to debug if sections are being missed.
                    # print(f"H2 sem seção mapeada: {element.get_text(strip=True)}")
                    pass
            
            elif element.name == 'h3' and current_section_type:
                # If we are inside a known section, process the h3 as a task
                titulo_raw = element.get_text(strip=True)
                
                # A simple heuristic to exclude non-task h3s (e.g., empty or decorative)
                if not titulo_raw or len(titulo_raw) < 5:
                    continue

                # Extract time if present in the title itself, e.g., "Leitura da Bíblia (4 min.)"
                tempo = self._extract_time_from_title(titulo_raw)
                titulo = re.sub(r'\s*\(\d+\s+min\.\)', '', titulo_raw).strip()

                # For "Ministério", try to find time in the next <p> tag if not found in title
                if current_section_type == 'Faça Seu Melhor no Ministério' and not tempo:
                    p_tempo = self._get_task_time_from_p(element)
                    if p_tempo:
                        tempo = p_tempo

                tarefas.append({
                    'semana': semana,
                    'tipo': current_section_type,
                    'titulo': titulo,
                    'tempo': tempo if tempo else None,
                    'designacao': None, # Designação não é extraída por este scraper
                })

        return tarefas
