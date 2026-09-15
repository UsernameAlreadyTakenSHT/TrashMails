## v0.4.0 — 2026-09-15

A second five-angle review of 0.3.0 (bugs, security, performance, UX, code quality) and the fixes
for everything it rated red or orange.

### Added
- **Links are tappable in the plain-text view**, through the same confirmation dialog as HTML, and a banner offers "View as HTML" when the message has an HTML part — the only way to reach that was the overflow menu.
- **Deleting a message asks first** and confirms with a "Message deleted" notice.
- **"Forget old addresses" asks before dropping addresses** that are already older than the chosen age.
- **A failed automatic refresh shows one line under the top bar** — the problem and when the list was last updated — instead of a snackbar at every tick.
- **The open screen survives a process death** (coming back from the browser on a low-memory phone): the inbox is listed again and the message re-fetched.
- **Unread badges survive a restart**; the badge and each row are announced as unread by screen readers.
- **Unit tests** for the stores (round trips, malformed data, quota window), the MIME reader, session renewal, and the hostile-input cases of the HTML converter (51 tests).

### Changed
- **The settings button moved to the top bar**; it was a small button above the + that covered the last card's buttons.
- **The message WebView only follows navigations the user tapped**: `<meta refresh>` and frame loads are dropped (with link confirmation off they opened the browser — and disclosed the reader's IP — on opening the mail), forms (POST) and any navigation of the mail frame get an empty reply whatever the image policy, and file / content access is off.
- **The HTML-to-text converter runs once, off the main thread, in linear time**, capped at 1 MiB: it ran in the composable twice per recomposition and a crafted message with unclosed tags could freeze the app.
- **HTTP calls are cancelled when their screen is left**; the message WebView is destroyed on leaving.
- **Guerrilla Mail drops a rejected session and attaches again** instead of failing until the app restarts; its session token is no longer stored.
- **Maildrop messages without HTML are read from their MIME parts** (quoted-printable, base64, charset) instead of showing raw headers.
- **The create dialog cannot be dismissed by a tap outside while creating**; creation errors have their own line and no longer share the home snackbar.
- Preferences are excluded from cloud backup and device-to-device transfer explicitly; provider error text is bounded on screen; server ids are URL-encoded; a non-ASCII link host is shown with its punycode form.
- Providers are registered in one exhaustive place (a new one does not compile until implemented); template dependencies removed.
- **Fewer requests**: no listing while a message is read, and an inbox reopened within the refresh interval shows its last listing instead of fetching again; a card whose account is being deleted is dimmed and inert.
- Wording, screen-reader descriptions and touch targets: "3 h 12 min", "Remove address", "Copy message text", "Open inbox", 48 dp radio and checkbox rows, headings, a link dialog that wraps at large fonts; an exhausted provider row reads "Limit reached for today"; very long plain bodies are cut with a "Show all" button.

### Fixed
- **The inbox empty state said "Auto-refresh every minute" whatever the setting** — misleading in manual mode.
- **A mail.tm account already purged could not be removed** with "Also delete the account" ticked.
- **A poll that succeeded wiped an unrelated error**, turning "This message has expired" into "(empty message)".
- **Deleted messages could come back** through a poll in flight, the unread badge could count a deleted message, and deleting a mail.tm account while its inbox was open kept polling it.

## v0.3.0 — 2026-09-15

Reading emails is now safe by default — plain text, no remote image, a look at every link before
it opens — plus a pass over the findings of a five-angle code review (bugs, security,
performance, UX, code quality).

### Added
- **Settings screen** (the small button above the +), three switches with the safe side as default: render HTML emails (off), load remote images (off), confirm before opening links (on).
- **Emails are shown as plain text** unless HTML rendering is on: nothing the sender wrote is rendered or fetched. The HTML is converted to text with links spelled out after their anchor text. A single message can be viewed as HTML, and back, from its menu.
- **Remote images are blocked** when an email is rendered as HTML — images, styles, fonts and frames alike, so the sender cannot tell the email was opened. A banner and a menu entry load them for that message only.
- **Links are confirmed before they open**: a dialog shows the site (or the address, for mailto:) in large type and the full URL, with Open, Copy link and Cancel.
- **Removing a mail.tm address can delete the account on the server**, messages included (ticked by default in the remove dialog). Before, only the password was forgotten and the messages stayed seven days.
- **More settings**: auto-refresh interval (30 s, 1 min, 5 min or manual only), forget old addresses (24 h, 7 or 30 days — local list only), block screenshots (FLAG_SECURE), theme (system, light, dark), copy a new address to the clipboard on creation.
- **The create dialog preselects the last provider used.**
- **Unread messages**: the badge on an address counts the messages never opened, and the inbox list shows them in bold.
- **Delete button on every provider that supports it** — Guerrilla Mail and mail.tm, not only Maildrop. The message disappears from the list at once, without a refetch.
- **Feedback while an address is created.** The dialog stays open with a progress line, Create disabled, and closes by itself when the new inbox opens; a failure shows on that same line, and Cancel abandons the creation.
- **Dark mode**: dark window from the first frame, and HTML emails darkened in the message view.
- **Unit tests**: the mailbox-name helpers, the HTML-to-text converter, and every provider parser against JSON fixtures (real replies where they could be captured) through an injected HTTP layer — including Guerrilla Mail session renewal, mail.tm token refresh and 422 details, Inbox Kitten recipient filtering.

### Changed
- **New application id `io.github.usernamealreadytakensht.trashmails`** (was the template's `com.example.trashmails`, which stores refuse). Android sees a different app: this version does not install over 0.2.0 — uninstall it first; the addresses it held are lost, so remove the mail.tm ones from the old version beforehand if you want their accounts deleted.
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
