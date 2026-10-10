## v0.5.9 — 2026-10-10

0.5.8 again, with its APK: the repository's releases became immutable just before 0.5.8 was published, which froze that release before the APK could be attached. No change in the app; see 0.5.8 below for what it brings.

### Changed
- **Releases are published as drafts first**, the APK attached, then made public (and immutable).

## v0.5.8 — 2026-10-10

Another three-angle security review, of 0.5.7 this time, and the fixes for what it found.

### Fixed
- **A keystore failing for a moment no longer wipes the kept emails**: an address's message file that cannot be opened is left alone, new mail waits in a side file and is merged back once it opens again.
- **DropMail.me never replaces a restore key it could not open** with a new one, and an unreadable device token can no longer lock the provider for ever: it only blocks while the keystore itself fails.
- **DropMail.me keeps the list of received ids bounded** (500 ids), recorded after the local copy and under its lock; oversized ids from the server are ignored.
- **DropMail.me: removing the last address keeps the device token gone** instead of a refresh bringing it back.
- **An address removed then created again in the same run** keeps its mail again.
- **Mail view**: only HTML whitespace is skipped before the doctype, so the privacy tags cannot be pushed behind it; `<link>` elements are disarmed as a whole while remote content is blocked, whatever their `rel`; the viewport meta is always given, the email's own one still wins.
- **Link dialog**: nothing is cut any more, the scrollable text shows a punycode host first, then its readable form.
- **mailto: links** are decoded by the app: the mail app receives the checked recipients, subject and body as fields, never the raw link.
- **U+061C (Arabic letter mark)** is removed with the other bidi controls.
- **The sender line is shown whole**, so a long display name can no longer hide the address.

### Changed
- **Kotlin 2.4.21.**
- **Release signing is read from outside the repository** (`~/.trashmails`, or a path in `TRASHMAILS_KEYSTORE_PROPERTIES`).
- **GitHub**: releases are immutable once published, and `main` only accepts commits whose CI passed.

## v0.5.7 — 2026-10-10

A three-angle security review of 0.5.6 (sender-controlled content, providers and storage, CI and platform) and the fixes for what it found.

### Fixed
- **A keystore failing for a moment no longer erases a sealed token**: the next save dropped it for good (for mail.tm the password, and with it the account). Its sealed value is now kept and written back as it was.
- **The mail view's privacy meta tags always land in the head**: a `<head>` in a comment, an attribute or a title let a sender leave the Content-Security-Policy inert in the body (checked with headless Chromium). They now go at the start of the document.
- **Explicit prefetch links are disarmed while remote content is blocked**: `<link rel=dns-prefetch>`, `preconnect` or `prefetch` could still reach the sender's host.
- **A link in plain text reads as what it opens**: bidi controls in a message body could make `https://evil.example/` read as `https://bank.com/…`. They are removed from message text.
- **The link dialog shows a mail link's recipients only**, one per line: a subject full of line breaks could push an added recipient out of sight.
- **Sender and recipient lines are one line each**, bounded on screen: line breaks could push the real address away or fake extra lines.
- **DropMail.me**: mail dropped from the local copy (kept a week, or past its bounds) no longer comes back from the session; an unreadable but valid device token is no longer replaced (which stranded every session); a new restore key is on disk before going on.
- **mail.tm listings keep memory bounded**: pages are reduced to summaries as they come, 150 at most.
- **Message files are synced before replacing the old ones**, and a temporary file left by an interrupted write goes with its address.

### Changed
- **Kept emails are sealed on disk** with the Keystore key, like the tokens; files written before are sealed when first read.
- **Kept emails of addresses never reopened are dropped after a week too.**
- **Sealed secrets are bound to what they are for** (format `k2:`): sealed values swapped between entries in the app's files no longer open. Older values are sealed again when read.
- **One Keystore key holder for the whole app**, so the key cannot be generated twice at first use.
- **"Load remote content"** replaces "Load remote images": styles, fonts, frames and media load with it too.
- **README**: the signing certificate's fingerprint, to check a downloaded APK; **SECURITY.md** says how to report a vulnerability privately.
- **CI**: the open-source Gradle cache, and the unsigned APK's checksum in each run's summary. The repository's `main` branch and release tags are now protected against force-push, moving and deletion.

## v0.5.6 — 2026-10-04

Lighter refreshes, a few interface fixes, and continuous integration.

### Fixed
- **mail.tm lists every message**, not only the first 30: pages are read up to the total, at most five (150 messages) per refresh.
- **Errors and notices survive a rotation or a theme change** instead of vanishing unread.
- **"expires in…" and "next slot in…" count down** while on screen instead of staying frozen, and a creation slot coming free while the create dialog is open makes Create available again.

### Changed
- **DropMail.me refreshes ask for the message ids only**; the bodies are downloaded once a new mail shows up, not at every refresh.
- **A message just read reopens at once**: the last ten bodies opened are kept in memory.
- **Provider logos are 192 px lossless WebP** (138 KB down to 75 KB).
- **AGP 9.4.1.**
- **Continuous integration**: every push is checked on Linux (official Gradle wrapper, tests, lint, release build with dependency verification); `gradlew` is now executable there.

## v0.5.5 — 2026-10-04

The rest of the 0.5.3 security review: the points it rated informational.

### Changed
- **Stored secrets are sealed with an Android Keystore key**: mail.tm passwords, inbox tokens, DropMail.me restore keys and device token (AES-256-GCM, key never leaves the Keystore). Only those values are sealed, so a lost key costs one address its access, not the list. Secrets stored in clear by earlier versions are sealed at the first launch.
- **The mail view turns DNS prefetching off**, and while remote content is blocked a Content-Security-Policy allows nothing but inline styles and data: images and fonts: a `<link rel=dns-prefetch>` can no longer tell the sender's DNS server the mail was opened.
- **mailto links open a compose screen with recipients, subject and body only**: no cc or bcc the sender slipped in, no attachment request.
- **"Block screenshots" also withholds the screen from accessibility services that are not accessibility tools** (Android 14+); TalkBack still reads it.
- **The link confirmation setting says what turning it off exposes.**
- **OkHttp 5.5.0** (4.12 only gets maintenance fixes).

### Fixed
- **A damaged setting no longer keeps the app from starting**: each falls back to its default on its own.
- **Guerrilla Mail: a listing without the inbox name is no longer taken as the inbox's own.**

## v0.5.4 — 2026-10-04

A five-angle security review of 0.5.3 (sender-controlled content, hostile servers, data on the device, platform and build, resilience) and the fixes for everything it found.

### Fixed
- **A crafted email could still freeze the app.** The 0.5.3 fix for HTML conversion was incomplete: a body of `<` (or `<li `, `<p `) with no `>` after them still took minutes to hours of CPU. Shown as HTML, a body of `<head` froze the main thread until the system killed the app. A Maildrop header folded over many lines froze a thread too. All three are linear now.
- **The local email cache could crash the app at every launch.** tempmail.lol and DropMail.me emails shared one file, loaded whole: filling two addresses could run it out of memory, after which listing, removing the address and (with "forget old addresses") every launch crashed. Each address now has its own file, bounded to 2 M characters, and a file that cannot be read is an empty cache. The 0.5.3 file is moved over once.
- **Out of memory on one reply or message is an error, not a crash**, and a Maildrop message is parsed within 1 MiB and five levels of nesting.
- **Oversized senders, subjects and links no longer crash the app** when it goes to the background or opens or copies them (binder limit): headers are cut at 1000 characters, links over 8 KiB are refused, and opening or copying reports a failure.
- **A provider listing an id twice no longer crashes the inbox screen**, nor a duplicate stored address the home screen.
- **The link dialog never shows a look-alike host alone**: punycode failed on recent Unicode characters (an invisible one will do) and the bare host was shown.
- **DropMail.me**: a firewall 403 no longer throws the device token away (which stranded every session), and removing an address while its session was being restored no longer leaves its restore key behind.
- **tempmail.lol message ids use SHA-256**: a sender could forge a message with the same 32-bit hash, which replaced the real one.
- **Refresh is throttled from the last attempt**, not the last success, and on return to the foreground too.
- Server ids in URL paths refuse `.` and `..`; server times are checked before conversion (a huge one made "forget old addresses" drop the address at once); addresses handed out by a server must be plain ASCII.
- A sender can no longer forge the "text <address>" a real link gets in plain text; at most 500 links are made tappable in a plain body.

### Changed
- **Kept emails go a week after the app kept them** (tempmail.lol, DropMail.me), instead of staying as long as the address.
- **No snapshot of the app in the recent-apps screen** (Android 13+), whatever the screenshot setting; deliberate screenshots still work.
- **The HTML view sends a generic user agent** (no device model) and what Chromium writes to disk is cleared.
- **Copies flagged sensitive on Android 10–12 too**, and the address of a public inbox (Inbox Kitten, Maildrop, Guerrilla Mail) is copied as sensitive.
- **The name field no longer lets the keyboard learn the name** (no auto-correction).
- **The DropMail.me device token goes with the last DropMail.me address.**
- **Server error lines are always attributed** to their service, without bidi or zero-width characters; senders, subjects and recipients lose bidi controls.
- **Inbox Kitten**: the retention dialog says that a hyphenated name can be found by any of its words.
- **Build**: official Gradle wrapper jar, every dependency checked against its SHA-256 (`gradle/verification-metadata.xml`), AndroidX and Android tools resolved from Google's repository only, no task affinity (task hijacking on Android 10), any keystore git-ignored, no coroutines debug file in the APK.

## v0.5.3 — 2026-09-28

A security, reliability and packaging review ahead of the F-Droid submission, and the fixes for what it found.

### Added
- **MIT license.** The provider logos are excluded: they remain their services' artwork and trademarks.
- **F-Droid store metadata** (fastlane): title, descriptions, icon, changelog.

### Fixed
- **A crafted email could crash the app or freeze the message screen**: a Maildrop mail nesting thousands of MIME parts overflowed the stack, and an HTML mail full of links without an address took minutes of CPU to turn into text.
- **tempmail.lol emails could be lost for good** when the inbox was left while a refresh was in flight: the server drops them as it hands them over. The listing now completes once sent.
- **A deleted message came back** when its inbox was reopened within the refresh interval.
- **DropMail.me failed for ten minutes at each daily token renewal**: the token was replaced before its end, leaving the live sessions unreachable. It is now kept to its end.
- **Refresh survives a clock change**: a clock set back no longer stalls auto-refresh or blocks the manual one.
- **Guerrilla Mail: the inbox is the name the server settled on**, should it differ from the one typed.
- **Removing an address really drops all of it**, even with a refresh or a DropMail.me restoration in flight; two DropMail.me deletions at once no longer lose one.
- **The local email cache is no longer rewritten at every refresh** when a message body is over 128 KiB.
- An HTML mail without text (images only) offers the HTML view instead of showing "(empty message)".
- Opening a message after the app was killed no longer resets the unread badge to zero; polling no longer restarts on a message screen when the app comes back.

### Changed
- **The mail view keeps no cache or cookies** once remote images are loaded, so a sender cannot tell two addresses are read on the same device; WebView metrics and Safe Browsing lookups are off.
- **No HTTP redirect is followed** (none of the APIs uses one; it would carry tokens to another host), and every request is limited to 60 s.
- **Backups and device transfers exclude all of the app's storage**, WebView data included.
- **"Copy link" marks the clip sensitive** (hidden from the clipboard preview on Android 13+).
- **Block screenshots applies from the first frame** of a cold start.
- **The build uses the installed JDK** (17 or later) instead of downloading one, and the release APK carries no Google dependency metadata block and no commit info.

## v0.5.2 — 2026-09-22

A first app icon.

### Changed
- **App icon**: a burning envelope replaces the placeholder envelope. Adaptive icon with a monochrome variant for themed icons (Android 13+).

## v0.5.1 — 2026-09-18

A two-angle review of 0.5.0 (provider logic; UX, accessibility and privacy) and the fixes for everything it found.

### Fixed
- **Every provider error message was mangled** before display: a broken control-character regex (since 0.4.0) replaced the letters p, C, n, t, r, l and s with spaces.
- **JSON nulls no longer become the word "null"**: on Android, a plain-text DropMail.me or tempmail.lol message ended up with an HTML body of "null" (and "null" senders or subjects), which was then cached. Every provider now reads message fields through null-safe helpers.
- **DropMail.me could lose an address for good** when a restoration was interrupted (screen left while the reply was in flight): the server had already rotated the restore key. The restoration is no longer cancellable, and a session found still holding the address is adopted with its current key.
- **A deleted message no longer comes back** when a refresh runs at the same time (leaving a message screen starts one): the cache merge is atomic and DropMail's hidden ids apply to cached entries too.
- **"Forget old addresses" and removing an address really forget**: cached emails, DropMail sessions and hidden ids are deleted instead of left behind as empty entries.
- **tempmail.lol messages are readable again after a process death** (the body comes from the local copy).
- **The locally kept emails are shown when a refresh fails** (offline, DropMail captcha, quota) instead of an empty list.
- **Creating an existing Guerrilla name again** (another domain, or scrambled) replaces the entry instead of being silently dropped.
- The delete dialog no longer claims DropMail.me keeps no copy (it does, until the session ends).

### Changed
- **DropMail.me tells when a lapsed session was replaced** ("mail sent meanwhile bounced"), and the open inbox is refreshed as soon as the app comes back, which keeps the session alive.
- **The message screen shows which extended address a mail came to** ("to name-tag@sub.domain").
- **Create dialog**: the provider list scrolls on its own under the pinned name field and options (large fonts no longer hide them); providers cannot be switched while creating; a creation error only shows for the provider it concerns; the status line is announced by screen readers; the domain choosers are single, named controls (TalkBack) and a long domain no longer squeezes the name input.
- **Extended address dialog**: an empty tag cannot be copied; ASCII keyboard without auto-correction.
- The local email cache is bounded to 50 messages and 128 KiB per body part, and is not rewritten when a refresh brings nothing.
- README: what the app keeps on the device (tokens, restore keys, cached emails).

## v0.5.0 — 2026-09-15

Two more providers and the creation options that came with them.

### Added
- **Guerrilla Mail: the domain and a scrambled address can be chosen when creating** — tap the domain at the end of the name field to pick one of the eleven (they all reach the same inbox; a lesser-known one gets past sites that refuse guerrillamail.com) and, optionally, a scrambled address: a random alias of the inbox handed out instead of its name, which anyone could otherwise open. Nothing is remembered: every creation starts from guerrillamail.com, unscrambled.
- **tempmail.lol.** A private inbox behind a token, alive one hour, on a random domain. The name typed is a prefix the service completes; a checkbox leaves out the random subdomain. The service hands each email over once and keeps nothing, so the app stores what it fetched (bodies included) and deleting a message only drops that local copy.
- **DropMail.me.** A random address on one of fifteen permanent domains (random by default, or picked from the Domain field). The address lives in a 10-minute session that every refresh extends; once it has lapsed, the app restores the same address into a new session at the next refresh with its restore key (mail sent while it has lapsed bounces). Mail goes with the session and cannot be deleted on the server, so the app keeps what it fetched and deleting a message drops that local copy. The @ button on the inbox makes an extended address (`name-tag@sub.domain`, delivered to the same inbox) to hand to a site that refuses the plain one. The API needs a free token, which the app requests for the device (renewed daily).

### Changed
- **The create dialog keeps one height whatever the provider** (its options sit inline: the domain at the end of the name field, checkboxes below), so nothing jumps or scrolls when switching providers.

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
