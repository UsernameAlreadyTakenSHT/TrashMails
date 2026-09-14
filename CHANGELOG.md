## v0.2.0 — 2026-09-14

Two new providers, one that had silently stopped working, and a create dialog reorganised around
what each service actually is.

### Added
- **Guerrilla Mail.** Public inbox by name (custom or random), emails kept one hour, manual deletion. The session token expires after an hour, so the app re-attaches to the inbox by name on every refresh rather than relying on a stored token.
- **mail.tm.** The first private inbox in the app: a real account behind a random password kept on the device, emails kept seven days, manual deletion. The bearer token is fetched on demand and refreshed once on 401.
- **An (i) button on every provider** with its retention rules and tappable links to the website, the privacy policy and the source repository — or a plain "not published" where a service has none.
- **An envelope launcher icon** in place of the default Android robot.

### Changed
- **Providers are grouped open source / closed source** in the create dialog, unavailable ones sinking to the bottom of their group.
- **The retention block left the bottom of the create dialog** for the (i) popup; the dialog is shorter and no longer changes height with the selection.
- **An address is always drawn on a single line**, shrinking the font when it would not fit, instead of wrapping the domain onto a second line.

### Removed
- **Burner Kiwi is marked unavailable.** Its API still hands out inboxes, but mail never arrives: two of its three domains (`farcical.lol`, `hushed.space`) no longer exist, and the last one (`deceit.pro`) points at a mail server that answers "can't verify recipient" for every address. It stays listed, greyed out, with the diagnosis behind its (i).

## v0.1.0 — 2026-09-14

First release.

### Added
- **Disposable inboxes from Inbox Kitten, Burner Kiwi and Maildrop**, several side by side, persisted locally.
- **Auto-refresh every minute** while an inbox is open, manual refresh at most once per 30 s.
- **HTML emails rendered in a WebView** with JavaScript disabled, plain-text fallback.
- **Copy the address or the message text** to the clipboard.
- **Per-provider retention rules** shown when creating or removing an address.
- **A courtesy creation quota per rolling 24 h**: 5 for Burner Kiwi, 20 for the others.
