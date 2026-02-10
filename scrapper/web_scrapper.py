from playwright.sync_api import sync_playwright
import locale
from datetime import datetime, timedelta
from bs4 import BeautifulSoup
from data_handling import process_data
import json
import os


class DataScrapper:
    def __init__(self):
        try:
            locale.setlocale(locale.LC_ALL, "es_MX.UTF-8")
        except:
            locale.setlocale(locale.LC_ALL, "es-MX")

        self.base_dir = os.path.dirname(os.path.abspath(__file__))
        self.json_dir = os.path.join(self.base_dir, "json_data")

        os.makedirs(self.json_dir, exist_ok=True)

    def open_browser(self):
        self.playwright = sync_playwright().start()

        try:
            self.browser = self.playwright.chromium.launch(
                headless=False, args=["--start-maximized"]
            )
        except:
            try:
                self.browser = self.playwright.firefox.launch(
                    headless=False, args=["--start-maximized"]
                )
            except Exception as e:
                raise e

        self.context = self.browser.new_context(no_viewport=True)
        return self.context.new_page()

    @staticmethod
    def get_week_extremes() -> str:
        target_date = datetime.now().date()

        monday = target_date - timedelta(days=target_date.weekday())
        sunday = monday + timedelta(days=6)

        meses = {
            1: "ENERO", 2: "FEBRERO", 3: "MARZO", 4: "ABRIL",
            5: "MAYO", 6: "JUNIO", 7: "JULIO", 8: "AGOSTO",
            9: "SEPTIEMBRE", 10: "OCTUBRE", 11: "NOVIEMBRE", 12: "DICIEMBRE",
        }

        if monday.month == sunday.month:
            return f"{monday.day}-{sunday.day} DE {meses[monday.month]}".lower()
        else:
            return (
                f"{monday.day} DE {meses[monday.month]} "
                f"A {sunday.day} DE {meses[sunday.month]}"
            ).lower()

    @staticmethod
    def scrape_data(page) -> list[str]:
        html = page.content()
        soup = BeautifulSoup(html, "html.parser")

        return [h.text.strip() for h in soup.find_all(["h1", "h2", "h3"])]

    def extract_this_month(self):
        try:
            page = self.open_browser()
            data = []

            current_month = datetime.now().strftime("%B").lower()
            current_year = datetime.now().year
            current_week_text = self.get_week_extremes()

            link = (
                f"https://wol.jw.org/es/wol/library/r4/lp-s/"
                f"biblioteca/guía-de-actividades/"
                f"guía-de-actividades-{current_year}/{current_month}"
            )

            page.goto(link)

            items = page.locator("#materialNav nav ul li a.cardContainer").all()

            valid_links = []
            found_current_week = False

            for item in items:
                if current_week_text in item.inner_text().lower():
                    found_current_week = True

                if found_current_week:
                    href = item.get_attribute("href")
                    valid_links.append(
                        f"https://wol.jw.org{href}" if href.startswith("/") else href
                    )

            for url in valid_links:
                page.goto(url)
                data.append(self.scrape_data(page))

            path = os.path.join(self.json_dir, "programa_do_mes_atual.json")
            with open(path, "w", encoding="utf-8") as f:
                json.dump(process_data(data), f, indent=4, ensure_ascii=False)

        except Exception as e:
            print(f"Erro em extract_this_month: {e}")

    def extract_this_week(self) -> list[str]:
        try:
            page = self.open_browser()
            page.goto("https://wol.jw.org/es/wol/h/r4/lp-s")

            page.click("#menuToday")
            page.wait_for_load_state("networkidle")

            current_week = self.get_week_extremes()
            links = page.locator("ul.directory.navCard li.todayItem a.cardContainer")

            for i in range(links.count()):
                link = links.nth(i)
                if current_week in link.inner_text().lower():
                    link.click()
                    break

            data = self.scrape_data(page)

            path = os.path.join(self.json_dir, "programa_da_semana.json")
            with open(path, "w", encoding="utf-8") as f:
                json.dump(process_data(data), f, indent=4, ensure_ascii=False)

            return data

        except Exception as e:
            print(f"Erro em extract_this_week: {e}")
            return []

    def extract_all_available_weeks(self):
        try:
            page = self.open_browser()
            current_year = datetime.now().year

            link = (
                f"https://wol.jw.org/es/wol/library/r4/lp-s/"
                f"biblioteca/guía-de-actividades/guía-de-actividades-{current_year}"
            )

            page.goto(link)
            page.wait_for_selector("ul.directory.navCard li.row.card a.cardContainer")

            urls = []
            for locator in page.locator(
                "ul.directory.navCard li.row.card a.cardContainer"
            ).all():
                href = locator.get_attribute("href")
                urls.append(f"https://wol.jw.org{href}")

            data = self.__extract_everything_from_now(page, urls)

            path = os.path.join(
                self.json_dir, "programa_de_todas_as_semanas_disponiveis.json"
            )
            with open(path, "w", encoding="utf-8") as f:
                json.dump(process_data(data), f, indent=4, ensure_ascii=False)

        except Exception as e:
            print(f"Erro em extract_all_available_weeks: {e}")

    def __extract_everything_from_now(self, page, urls):
        data = []
        valid_links = []
        current_week_text = self.get_week_extremes()
        found_current_week = False

        for url in urls:
            page.goto(url)
            items = page.locator("#materialNav nav ul li a.cardContainer").all()

            for item in items:
                if not found_current_week and current_week_text in item.inner_text().lower():
                    found_current_week = True

                if found_current_week:
                    href = item.get_attribute("href")
                    valid_links.append(
                        f"https://wol.jw.org{href}" if href.startswith("/") else href
                    )

        for url in valid_links:
            page.goto(url)
            data.append(self.scrape_data(page))

        return data

# Exemplo de uso
# main = DataScrapper()
# data = main.extract_all_available_weeks()
