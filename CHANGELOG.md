## Unreleased

Reading emails is now safe by default — plain text, no remote image, a look at every link before
it opens — plus a pass over the findings of a five-angle code review (bugs, security,
performance, UX, code quality).

### Added
- **Settings screen** (the small button above the +), three switches with the safe side as default: render HTML emails (off), load remote images (off), confirm before opening links (on).
- **Emails are shown as plain text** unless HTML rendering is on: nothing the sender wrote is rendered or fetched. The HTML is converted to text with links spelled out after their anchor text. A single message can be viewed as HTML, and back, from its menu.
- **Remote images are blocked** when an email is rendered as HTML — images, styles, fonts and frames alike, so the sender cannot tell the email was opened. A banner and a menu entry load them for that message only.
- **Links are confirmed before they open**: a dialog shows the site (or the address, for mailto:) in large type and the full URL, with Open, Copy link and Cancel.
- **Delete button on every provider that supports it** — Guerrilla Mail and mail.tm, not only Maildrop. The message disappears from the list at once, without a refetch.
- **Feedback while an address is created.** The dialog stays open with a progress line, Create disabled, and closes by itself when the new inbox opens; a failure shows on that same line, and Cancel abandons the creation.
- **Dark mode**: dark window from the first frame, and HTML emails darkened in the message view.
- **Unit tests** for the mailbox-name helpers.

### Changed
- **Links in an email open in the browser** (`mailto:` in the mail app). Anything else — `intent://`, `tel:`, another app's deep link — is dropped, and nothing ever navigates inside the message view.
- **Readable errors.** "No internet connection", "The server took too long to answer", "Unexpected reply from the server"… instead of the exception's own text. Errors sit in a Material snackbar (announced by TalkBack, swipeable, auto-dismissed with an OK action); the refresh throttle is a short notice, no longer an error.
- **Polling pauses in the background** and resumes on return, waiting out what is left of the minute.
- **Guerrilla Mail: one request per refresh** instead of two — the session token is cached and only renewed when the API shows the session is gone.
- **mail.tm explains a refused address** (username not valid, already used…) instead of always claiming it is taken; an account deleted server-side reads as such instead of "HTTP 401" every minute.
- **Copying a message body** no longer echoes it in the confirmation toast, and marks the clip sensitive (Android 13+ hides it from the clipboard preview).
- **Release APK 24 MB → 2.7 MB**: R8 code and resource shrinking enabled.
- Random names and mail.tm passwords come from `SecureRandom`; a user-typed name has accents stripped and dots normalised so every provider accepts it.
- Provider calls (network and JSON parsing) run off the main thread; replies over 8 MB are refused.
- The (i) button reaches the 48 dp touch target; the provider logo is no longer announced twice by screen readers.

### Fixed
- **Going back during a load** showed "StandaloneCoroutine was cancelled" and left the progress bar stuck.
- **A slow reply could land on another screen**: refreshing inbox A, going back and opening B could paint A's list, or A's message body, into B.
- **"Inbox is empty" before the first listing** (or after a failed one): the screen now says "Loading…" or "Could not load the inbox".
- **Guerrilla Mail's welcome mail** jumped to the top as "now" on every poll; an expired message crashed the JSON parser instead of reading as expired.
- **The mail.tm password went into the Google cloud backup**: the app opts out of Android backup altogether.
- Maildrop's (i) claimed the app never deletes anything on the server while it was the only provider with a delete button.
- One malformed entry in the saved inbox list no longer wipes the whole list; a creation timestamp in the future (clock set back) no longer blocks the quota for a day; a failed listing no longer starts the 30 s refresh throttle.

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
