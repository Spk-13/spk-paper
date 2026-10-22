"""Download only the requested TradingRef issue through its public form."""
import json
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo
from playwright.sync_api import sync_playwright
from pypdf import PdfReader

out = Path("tradingref-output")
out.mkdir(exist_ok=True)
now = datetime.now(ZoneInfo("Asia/Kolkata"))
report = {"source": "https://www.tradingref.com/", "date": now.date().isoformat(),
          "requested_edition": "GREATER HYDERABAD", "downloaded": False}
try:
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(accept_downloads=True)
        response = page.goto(report["source"], wait_until="domcontentloaded", timeout=60000)
        report["http_status"] = response.status if response else None
        if response is not None and response.status >= 400:
            report["error_page"] = (response.text())[:1500]
            report["response_server"] = response.headers.get("server", "")
            print(json.dumps(report), flush=True)
        if response is None or response.status >= 400:
            raise RuntimeError(f"TradingRef homepage returned HTTP {report['http_status']}")
        page.locator("#datePicker").click()
        calendar_label = now.strftime("%B ") + str(now.day) + now.strftime(", %Y")
        page.get_by_label(calendar_label, exact=True).click()
        page.locator("#languageSelect").select_option(label="telugu", timeout=60000)
        page.locator("#newspaperSelect").select_option(label="Eenadu", timeout=60000)
        page.wait_for_function("() => document.querySelector('#editionSelect')?.options.length > 1", timeout=60000)
        editions = page.locator("#editionSelect option").evaluate_all(
            "els => els.map(e => ({label:e.textContent.trim(),value:e.value}))")
        report["available_editions"] = editions
        print(json.dumps(report, ensure_ascii=False), flush=True)
        matches = [e for e in editions if " ".join(e["label"].upper().split()) == "GREATER HYDERABAD"]
        if len(matches) != 1:
            raise RuntimeError("Greater Hyderabad is not offered by TradingRef for this date; no substitute downloaded.")
        page.locator("#editionSelect").select_option(value=matches[0]["value"])
        with page.expect_download(timeout=240000) as event:
            page.locator("#downloadPdfBtn").click()
        download = event.value
        target = out / (now.date().isoformat() + "-Greater-Hyderabad.pdf")
        download.save_as(target)
        if target.read_bytes()[:5] != b"%PDF-":
            target.unlink()
            raise RuntimeError("The download is not a PDF")
        pages = len(PdfReader(target).pages)
        if pages < 1:
            target.unlink()
            raise RuntimeError("The downloaded PDF has no pages")
        report.update(downloaded=True, pages=pages, file=target.name,
                      validation="Selected date and edition verified on source form; PDF opens. Masthead/content requires visual verification before app publication.")
        browser.close()
except Exception as exc:
    report["error"] = str(exc)
    raise
finally:
    (out / "result.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
