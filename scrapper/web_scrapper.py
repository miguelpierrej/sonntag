from playwright.sync_api import sync_playwright
import locale
from datetime import datetime, timedelta
from bs4 import BeautifulSoup
from exceptions import  DateTimeConfigError

def get_week_extremes(target_date: datetime = None) -> str:
    # Se não passar data, usa a de hoje
    if target_date is None:
        target_date = datetime.now().date()

    monday = target_date - timedelta(days=target_date.weekday())
    sunday = monday + timedelta(days=6)

    meses = {
        1: "ENERO", 2: "FEBRERO", 3: "MARZO", 4: "ABRIL", 
        5: "MAYO", 6: "JUNIO", 7: "JULIO", 8: "AGOSTO", 
        9: "SEPTIEMBRE", 10: "OCTUBRE", 11: "NOVIEMBRE", 12: "DICIEMBRE"
    }

    # QUANDO A SEMANA COMEÇAR NO MES E TERMINAR NO MESMO MES
    if monday.month == sunday.month:
        nome_mes = meses[monday.month]
        return f"{monday.day}-{sunday.day} DE {nome_mes}"
    # QUANDO A SEMANA COMEÇAR NO MES E TERMINAR NO OUTRO MES
    else:
        mes_segunda = meses[monday.month]
        mes_domingo = meses[sunday.month]
        return f"{monday.day} DE {mes_segunda} A {sunday.day} DE {mes_domingo}"



def scrape_data(page) -> list[str]:
    content = []
    html = page.content()
    soup = BeautifulSoup(html, 'html.parser')
    
    for header in soup.find_all(['h1', 'h2', 'h3']):
        content.append(header.text.strip())

    return content

class DataScrapper:
    def __init__(self):
        try:
            locale.setlocale(locale.LC_ALL, 'es_MX.UTF-8')
        except:
            locale.setlocale(locale.LC_ALL, 'es-MX')

        try:
            self.year = datetime.now().year
            self.month = datetime.now().strftime('%B')
            self.week = datetime.now().isocalendar()[1]
            self.week_of_the_meeting = get_week_extremes()
            self.url = f"https://wol.jw.org/es/wol/meetings/r4/lp-s/{self.year}/{self.week}"
            self.meeting_selector = f"{self.week_of_the_meeting}"
        except Exception as e:
            raise DateTimeConfigError(e)



    def open_browser_and_scrappe_data(self) -> list[str]:
        with sync_playwright() as p:            
            # headless=False para visualizar a janela
            browser = p.chromium.launch(headless=False)
            page = browser.new_page()
            page.goto(self.url)
            page.get_by_role("link", name= self.meeting_selector).click()

            data = scrape_data(page)
            print(data)
            browser.close()

            return data
    
