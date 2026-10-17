# SPK Paper 0.4

Phone-based Eenadu Greater Hyderabad PDF preparation. Tap **Open today’s paper** to load the official reader on the phone, select today’s edition, prepare all its page images as a PDF, and open Google Drive’s PDF viewer (another PDF app is offered if Drive cannot handle it). No GitHub feed URL is required.

Keep the app open while preparing. Internet is required for a new issue. Once prepared, the saved issue works offline. Only one completed issue is retained: a new PDF is checked for page count and readability, installed, and the previous issue removed. Failed preparation keeps the previous issue with its original date. Temporary preparation data is removed on cancellation; Android may retain interrupted cache data until the next cleanup. External viewer caches are controlled by that viewer, not SPK Paper.

The app follows the visible official reader and public Skip registration option. Sign-in must be completed by the user if required. HTTP errors, access restrictions, changed selectors, wrong dates or duplicate images stop preparation. Availability on a phone connection must be tested; successful build or fixture tests do not prove live newspaper access. No proxy or restriction bypass is included.

## Install

Install the APK from the Build Android APK workflow artifact. Uninstall an earlier debug build if Android reports a signing conflict. Only an app upgrade needs installation; daily papers do not. Android 8 or newer is required.

## Validation

CI builds the APK, runs lint and Android emulator instrumentation tests: actual PDF rendering, complete-issue replacement, rejection of incomplete replacement, and WebView reader selection/date checks using a controlled fixture. The live publisher and Google Drive installation are not part of the fixture tests. See Actions for results.

The old server publisher remains manual-only because Eenadu returned HTTP 403 to GitHub Actions. It is not used by this APK.
