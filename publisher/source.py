"""Create a dated PDF from the public official Eenadu viewer."""
from __future__ import annotations

import logging
import os
import re
import tempfile
from datetime import datetime
from pathlib import Path
from urllib.parse import urlparse
from zoneinfo import ZoneInfo

from PIL import Image
from reportlab.pdfgen import canvas as pdf_canvas
from playwright.sync_api import TimeoutError as PlaywrightTimeoutError
from playwright.sync_api import sync_playwright

logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"), format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("eenadu_pdf_delivery")
TZ_NAME = os.getenv("TIMEZONE", "Asia/Kolkata")
TZ = ZoneInfo(TZ_NAME)
MAX_IMAGE_BYTES = int(os.getenv("MAX_IMAGE_MB", "20")) * 1024 * 1024
SOURCE_URL = "https://epaper.eenadu.net/"
EDITION_TITLE = "GREATER HYDERABAD"
IMAGE_HOST = "d66zsp32hue2v.cloudfront.net"


def verify_jpeg(path: Path) -> None:
    if not path.exists() or path.stat().st_size < 4 or path.stat().st_size > MAX_IMAGE_BYTES:
        raise RuntimeError("A page image is missing, empty, or over the configured size limit")
    with path.open("rb") as stream:
        if stream.read(2) != b"\xff\xd8":
            raise RuntimeError("Eenadu viewer did not return a JPEG page image")


def _page_image_url(page) -> str:
    image = page.locator("img.img_jpg")
    image.wait_for(state="visible", timeout=30000)
    page.wait_for_function(
        "() => { const e=document.querySelector('img.img_jpg'); return !!e && e.complete && e.naturalWidth > 1000; }",
        timeout=30000,
    )
    url = image.get_attribute("src") or ""
    parsed = urlparse(url)
    if parsed.scheme != "https" or parsed.hostname != IMAGE_HOST or "/Eenadu/" not in parsed.path or "_hr.jpg" not in parsed.path:
        raise RuntimeError("The official viewer returned an unexpected page image URL")
    return url


def download_page_image(page, url: str, destination: Path) -> None:
    response = page.request.get(url, timeout=60000)
    if not response.ok:
        raise RuntimeError(f"Page image request failed with HTTP {response.status}")
    data = response.body()
    if len(data) > MAX_IMAGE_BYTES or len(data) < 4 or data[:2] != b"\xff\xd8":
        raise RuntimeError("The page image is invalid or over the configured size limit")
    destination.write_bytes(data)


def create_issue_pdf(now: datetime, output_path: Path) -> Path:
    """Open Greater Hyderabad in the public viewer and assemble all pages."""
    issue_date = now.strftime("%d/%m/%Y")
    image_paths: list[Path] = []
    with tempfile.TemporaryDirectory(prefix="eenadu-pages-") as temp_dir:
        temp_path = Path(temp_dir)
        try:
            with sync_playwright() as playwright:
                browser = playwright.chromium.launch(headless=True)
                context = browser.new_context(accept_downloads=True)
                page = context.new_page()
                log.info("Opening official viewer for %s", issue_date)
                page.goto(SOURCE_URL, wait_until="domcontentloaded", timeout=60000)
                page.locator(".ep_ed_pg").first.wait_for(state="visible", timeout=30000)

                cards = page.locator(".ep_ed_pg")
                chosen = None
                for index in range(cards.count()):
                    card = cards.nth(index)
                    label = " ".join((card.inner_text() or "").split()).upper()
                    if EDITION_TITLE in label:
                        chosen = card
                        break
                if chosen is None:
                    raise RuntimeError("Greater Hyderabad is not listed on the official e-paper home page")
                card_date = chosen.locator(".ep_ed_img").get_attribute("edate")
                if card_date != issue_date:
                    raise RuntimeError(f"Official page does not show a Greater Hyderabad issue for {issue_date}")
                chosen.locator(".ep_ed_img").click()

                # The site offers a public Skip registration route to the viewer.
                if "/login/login" in (page.url or ""):
                    page.get_by_text("Skip registration", exact=True).click(timeout=20000)
                page.locator("#myPageList").wait_for(state="visible", timeout=45000)
                body = " ".join((page.locator("body").inner_text() or "").split()).upper()
                if EDITION_TITLE not in body:
                    raise RuntimeError("The official reader did not open the Greater Hyderabad edition")
                if now.strftime("%d-%b-%y").upper() not in body:
                    raise RuntimeError("The official reader date does not match today's issue")

                page_list = page.locator("#myPageList")
                count = page_list.locator("option").count()
                if count < 1:
                    raise RuntimeError("No pages were listed in the official reader")
                log.info("Greater Hyderabad %s has %d pages", issue_date, count)
                seen_urls = set()
                for index in range(count):
                    previous = page.locator("img.img_jpg").get_attribute("src") if index else None
                    page_list.select_option(index=index, timeout=30000)
                    if index and previous:
                        page.wait_for_function(
                            "previous => { const e=document.querySelector('img.img_jpg'); return !!e && e.src !== previous && e.complete && e.naturalWidth > 1000; }",
                            arg=previous,
                            timeout=45000,
                        )
                    image_url = _page_image_url(page)
                    if image_url in seen_urls:
                        raise RuntimeError("Viewer returned a duplicate newspaper page")
                    seen_urls.add(image_url)
                    log.info("Capturing page %d of %d", index + 1, count)
                    image_path = temp_path / f"page-{index + 1:02d}.jpg"
                    download_page_image(page, image_url, image_path)
                    verify_jpeg(image_path)
                    image_paths.append(image_path)
                browser.close()

            if not image_paths:
                raise RuntimeError("No page images were captured")
            dpi = float(os.getenv("PDF_DPI", "180"))
            document = pdf_canvas.Canvas(str(output_path), pageCompression=1)
            document.setTitle(f"Eenadu Greater Hyderabad - {now.strftime('%Y-%m-%d')}")
            document.setAuthor("Eenadu E-paper reader")
            for image_path in image_paths:
                with Image.open(image_path) as image:
                    width_px, height_px = image.size
                width_pt, height_pt = width_px * 72 / dpi, height_px * 72 / dpi
                document.setPageSize((width_pt, height_pt))
                document.drawImage(str(image_path), 0, 0, width=width_pt, height=height_pt)
                document.showPage()
            document.save()
        except PlaywrightTimeoutError as exc:
            raise RuntimeError(f"Eenadu e-paper viewer timed out while opening or loading a page: {exc}") from exc
    if not output_path.exists() or output_path.stat().st_size < 1000:
        raise RuntimeError("PDF creation failed")
    return output_path

