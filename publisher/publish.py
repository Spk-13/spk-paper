"""Generate a complete dated issue and a manifest for the Android reader."""
import hashlib
import json
import os
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo
from pypdf import PdfReader


def write_manifest(pdf: Path, output: Path, issue_date: str) -> dict:
    reader = PdfReader(pdf)
    if not reader.pages:
        raise ValueError('Generated PDF has no pages')
    result = {
        'date': issue_date,
        'edition': 'GREATER HYDERABAD',
        'pdf': pdf.relative_to(output).as_posix(),
        'pages': len(reader.pages),
        'sha256': hashlib.sha256(pdf.read_bytes()).hexdigest(),
        'generated_at': datetime.now(ZoneInfo('Asia/Kolkata')).isoformat(),
    }
    (output / 'latest.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    return result


def main():
    from source import create_issue_pdf
    now = datetime.now(ZoneInfo('Asia/Kolkata'))
    output = Path(os.environ.get('OUTPUT_DIR', 'public'))
    issue = output / 'issues' / f'{now:%Y-%m-%d}.pdf'
    issue.parent.mkdir(parents=True, exist_ok=True)
    create_issue_pdf(now, issue)
    if datetime.now(ZoneInfo('Asia/Kolkata')).date() != now.date():
        raise RuntimeError('Date changed while creating issue; rerun')
    manifest = write_manifest(issue, output, now.date().isoformat())
    (output / '.nojekyll').touch()
    (output / 'index.html').write_text('<!doctype html><meta charset="utf-8"><title>SPK Paper feed</title><h1>SPK Paper</h1><p>Daily reader feed. Configure the Android app with this site’s latest.json URL.</p>', encoding='utf-8')
    print(f"Verified {manifest['date']} with {manifest['pages']} pages")


if __name__ == '__main__':
    main()
