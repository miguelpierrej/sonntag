from playwright.sync_api import sync_playwright
import locale
from datetime import datetime, timedelta
from bs4 import BeautifulSoup
from .data_handling import process_data


class DataScrapper:
    def __init__(self):
        try:
            locale.setlocale(locale.LC_ALL, 'es_MX.UTF-8')
        except:
            locale.setlocale(locale.LC_ALL, 'es-MX')


    def open_browser(self):
        self.playwright = sync_playwright().start()
        
        self.browser = self.playwright.chromium.launch(
            headless=False, 
            args=["--start-maximized"]
        )

        self.context = self.browser.new_context(no_viewport=True)
        
        page = self.context.new_page()

        return page
    
    @staticmethod
    def get_week_extremes() -> str:
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
            return f"{monday.day}-{sunday.day} DE {nome_mes}".lower()
        # QUANDO A SEMANA COMEÇAR NO MES E TERMINAR NO OUTRO MES
        else:
            mes_segunda = meses[monday.month]
            mes_domingo = meses[sunday.month]
            return f"{monday.day} DE {mes_segunda} A {sunday.day} DE {mes_domingo}".lower()

    @staticmethod
    def scrape_data(page) -> list[str]:
        content = []
        html = page.content()
        soup = BeautifulSoup(html, 'html.parser')
        
        for header in soup.find_all(['h1', 'h2', 'h3']):
            content.append(header.text.strip())

        return content


    def extract_this_month(self):
        try:
            page = self.open_browser()
            data = []
            
            current_month = datetime.now().strftime('%B').lower()
            current_year = datetime.now().year
            current_week_text = self.get_week_extremes() 

            link = f"https://wol.jw.org/es/wol/library/r4/lp-s/biblioteca/guía-de-actividades/guía-de-actividades-{current_year}/{current_month}"
            page.goto(link)

            items = page.locator("#materialNav nav ul li a.cardContainer").all()

            valid_links = []
            found_current_week = False

            for item in items:
                item_text = item.inner_text().lower()

                if current_week_text.lower() in item_text:
                    found_current_week = True
                
                if found_current_week:
                    href = item.get_attribute("href")
                    full_url = f"https://wol.jw.org{href}" if href.startswith('/') else href
                    valid_links.append(full_url)

            for url in valid_links:
                page.goto(url)
                data.append(self.scrape_data(page))
            
            page.close()
            self.browser.close()
            self.playwright.stop()
            
            return process_data(data)
        except Exception as e:
            print(f"Erro em extract_this_month: {str(e)}")
            try:
                page.close()
                self.browser.close()
                self.playwright.stop()
            except:
                pass
            return []

    

    def extract_this_week(self) -> list[str]:
        try:
            page = self.open_browser()

            link = "https://wol.jw.org/es/wol/h/r4/lp-s"

            page.goto(link)

            page.click("#menuToday")
        
            page.wait_for_load_state("networkidle")

            current_week = self.get_week_extremes() 
            
            links = page.locator("ul.directory.navCard li.todayItem a.cardContainer")
        

            count = links.count()
            
            for i in range(count):
                link = links.nth(i)
                text = link.inner_text()
                
                if current_week.lower() in text.lower():
                    link.click()
                    break

            data = self.scrape_data(page)

            page.close()
            self.browser.close()
            self.playwright.stop()
            
            return process_data(data)
        except Exception as e:
            print(f"Erro em extract_this_week: {str(e)}")
            try:
                page.close()
                self.browser.close()
                self.playwright.stop()
            except:
                pass
            return []
    
    def extract_all_available_weeks(self):
        try:
            page = self.open_browser()

            current_year = datetime.now().year
            link = f"https://wol.jw.org/es/wol/library/r4/lp-s/biblioteca/guía-de-actividades/guía-de-actividades-{current_year}"
            selector = "ul.directory.navCard li.row.card a.cardContainer"
            
            page.goto(link)

            page.wait_for_selector(selector)
            
            locators = page.locator(selector).all()
            
            urls = []
            for locator in locators:
                href = locator.get_attribute("href")
                if href:
                    full_url = f"https://wol.jw.org{href}"
                    urls.append(full_url)
            
            data = self.__extract_everything_from_now(page, urls)

            page.close()
            self.browser.close()
            self.playwright.stop()
            
            return process_data(data)
        except Exception as e:
            print(f"Erro em extract_all_available_weeks: {str(e)}")
            try:
                page.close()
                self.browser.close()
                self.playwright.stop()
            except:
                pass
            return []

    def __extract_everything_from_now(self, page, urls):
        valid_links = []
        data = []
        current_week_text = self.get_week_extremes()
     
        found_current_week = False

        for url in urls:
            page.goto(url)
            items = page.locator("#materialNav nav ul li a.cardContainer").all()

            for item in items:
                if not found_current_week:
                    item_text = item.inner_text().lower()
                    if current_week_text in item_text:
                        found_current_week = True
                
                if found_current_week:
                    href = item.get_attribute("href")
                    full_url = f"https://wol.jw.org{href}" if href.startswith('/') else href
                    valid_links.append(full_url)

        for url in valid_links:
            page.goto(url)
            data.append(self.scrape_data(page))
        
        
        return data
    

