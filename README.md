# SPK Paper — daily Greater Hyderabad reader

**Integration status (17 October 2026):** APK v0.3 builds and passes Android lint. Publisher unit tests pass, but the official homepage returns HTTP 403 Forbidden from GitHub Actions. No live newspaper has been published. Daily delivery is blocked until the source permits server access. No device test has been completed.

Feed address: `https://spk-13.github.io/spk-paper/latest.json`

Version 0.3 checks the feed and saves today's issue when the app starts. Tap **Open today's paper** to read it in Google Drive or another installed PDF reader. Each issue is checked for the India date, Greater Hyderabad edition, checksum and page count. The feed address is included in the app.

The daily publisher runs at 07:17, 08:17, 09:17, 10:17 and 11:17 India time. A push changing the publisher also starts a run. GitHub schedules can be delayed and can stop after repository inactivity; check Actions if updates stop. Only successful generation is deployed. Failed runs leave the previously published issue available, and the app refuses to label it today's paper.

GitHub Pages must use **GitHub Actions** as its source. This repository and its deployed PDFs are public. Publish only material you are authorized to redistribute. This independent app is not affiliated with Eenadu.

## Installation

Get the APK from the **Build Android APK** workflow artifact. Cloud builds use a different debug signing key from the earlier testing APK: uninstall the earlier test app before installing this build if Android reports a signature conflict. App settings and saved issue are removed by uninstalling. A stable private release signing key is required for future in-place app version upgrades; daily newspaper changes do not require APK upgrades.

## Build and checks

Open `android` in Android Studio, or run `android/gradlew -p android assembleDebug lintDebug` with JDK 17 and Android SDK 35. The GitHub build workflow runs these checks and uploads an APK artifact.

```sh
pip install -r publisher/requirements.txt
python -m playwright install --with-deps chromium
python -m unittest discover -s publisher -p 'test_*.py'
python publisher/publish.py
```

`publisher/source.py` uses the site's public edition selection and Skip registration route. It creates a PDF from the visible high-resolution page images. It stops on access restrictions, unexpected dates, duplicate pages or invalid images. The full page count comes from the current issue; it is not fixed to yesterday's count.

`publisher/publish.py` writes a dated PDF and a `latest.json` manifest containing date, edition, relative PDF path, page count and SHA-256. Android fetches over HTTPS and keeps the current PDF in private storage, then grants the external PDF viewer temporary read-only access. Newspaper pages are images, so text selection and text-to-speech are not provided.

See Actions for the authoritative deployment and build results. A schedule configured in source is not proof of a successful deployment.
