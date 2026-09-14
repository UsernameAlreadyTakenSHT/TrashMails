# Changelog

All releases are published on GitHub with a signed APK (same key throughout, so each version
installs over the previous one).

## v0.2.0 — 2026-09-14

New providers, one removed from service.

### Added
- **Guerrilla Mail**: public inbox by name (custom or random), emails kept 1 hour, manual deletion.
- **mail.tm**: private inbox (account + password kept on the device), emails kept 7 days, manual deletion.
- (i) button per provider: retention rules, website, privacy policy and source code links.
- Envelope launcher icon.

### Changed
- Providers grouped open source / closed source in the create dialog; the retention block that used to sit at the bottom of the dialog moved into the (i) popup.
- Address always drawn on a single line (font shrinks when needed).

### Removed
- **Burner Kiwi is marked unavailable** (still listed, greyed out): two of its domains have expired and the last one rejects every recipient, so mail never arrives. Details in the README.

## v0.1.0 — 2026-09-14

First release.

- Disposable inboxes from Inbox Kitten, Burner Kiwi and Maildrop.
- Several inboxes side by side, persisted locally.
- Auto-refresh every minute, manual refresh (max once per 30 s).
- HTML emails rendered in a WebView (JavaScript disabled).
- Copy address / copy message text.
- Per-provider retention rules shown when creating or removing an address.
- Courtesy creation quota per rolling 24 h (Burner Kiwi 5, others 20).
