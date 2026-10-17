# SPK Paper 0.4 validation

Verified build: https://github.com/Spk-13/spk-paper/actions/runs/39059497379

Commit: e8479837e6514bb8420d3199c59d124a4dad9783

- APK assembly and Android lint passed (warnings remain).
- Five instrumentation tests passed on Android 15 (API 35): app startup, rendering three PDF pages, incomplete JPEG rejection, preserving the old issue when validation fails and deleting it after successful replacement, and WebView page selection / stale-date rejection with a controlled HTML fixture.
- The app no longer requests the missing GitHub Pages feed.
- Live Eenadu capture on the recipient’s phone and opening the real Google Drive app have NOT been verified. GitHub’s live source access previously returned HTTP 403. These tests do not establish live daily newspaper availability.
- Minimum SDK is 26; runtime tests were performed only on API 35.
- A new issue is prepared after tapping Open today’s paper, with the app kept open. There is no unattended background job.
