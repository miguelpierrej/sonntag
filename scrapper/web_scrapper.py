from playwright.sync_api import sync_playwright
import locale
from datetime import datetime, timedelta
from bs4 import BeautifulSoup
from exceptions import WeekExtremesError, DateTimeConfigError

def get_week_extremes() -> str:
    try:
        # TODO: AQUI VOCE VAI PASSAR A DATA DESEJADA DENTRO DESSA VARIAVEL "today", A DATA TEM QUE SER ENVIADA NO FORMATO DATETIME
        today = datetime.now().date()
        
        # .weekday() returns 0 for Monday, 1 for Tuesday, ..., 6 for Sunday
        day_of_the_week = today.weekday()
        
        # Calculate Monday of the current week
        monday = today - timedelta(days=day_of_the_week)
        
        # Calculate Sunday of the current week
        sunday = monday + timedelta(days=6)
    except Exception as e:
        raise WeekExtremesError(e)

    return monday.strftime("%d"), sunday.strftime("%d")


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
            self.start_date, self.end_date = get_week_extremes()
            self.url = f"https://wol.jw.org/es/wol/meetings/r4/lp-s/{self.year}/{self.week}"
            self.meeting_selector = f"{self.start_date}-{self.end_date} de {self.month}"
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
    
