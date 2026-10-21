# iChat v2.3 — unified Compose design system

## Design system (new)
- `ui/theme/Theme.kt` — one `IChatTheme` (Material 3 colour scheme, shapes, typography) built from `Look`, so light / dark / AMOLED and the user's accent apply everywhere.
- `ui/components/Components.kt` — shared top bar, avatar, cards, settings rows, bar chart.
- `ui/ComposeBaseActivity.kt` — edge-to-edge bars + rebuild on style change.
- `Look` palette and default accent (violet `#6750A4`) now match the Compose home screen, so the screens still built with Views show identical colours.

## Screens in Jetpack Compose
Home, conversation list (`MainActivity` + `ConversationRepo`), Settings, Starred, Insights.

## Still View-based (next to migrate)
`ThreadActivity` (chat bubbles), `NewMessageActivity`, `StyleActivity`. They already use the new palette.

---

# iChat v2.2 — AndroidX / Compose hardening pass

## Applied in this revision
- Corrected Android Gradle source-set configuration; Android resources and manifest use standard `src/main` locations.
- Removed manifest `package`, `uses-sdk`, and stale version declarations; Gradle owns namespace, SDK levels, and version.
- Disabled Android backup for private messaging preferences (`allowBackup=false`).
- Set `targetSdk` to 35.
- Made `MainActivity` and `BootReceiver` non-exported; `BootReceiver` also validates its action.
- Added immutable flags to ordinary `PendingIntent`s; the RemoteInput reply action remains mutable where required.
- Changed phone-number preference keys to use the full normalized digit string rather than only the last eight digits.
- Changed SharedPreferences writes from synchronous `commit()` to `apply()`.
- Corrected `sms://` URI parsing for `//` authorities.
- Removed untrusted `forward_text` / `jump_id` handling from exported thread entry points; standard share text / SMS body is used instead.
- Made scheduled message IDs monotonic relative to stored entries, checked default-SMS status before sending, and retained scheduled entries if sending fails.
- Escaped `%`, `_`, and backslash in message search input.

## Still requires device-level implementation/testing
- MMS decoding and persistence: `MmsReceiver` still only posts a notification. Do not rely on this build as a complete MMS client.
- Full background-thread migration for contacts/bitmap lookup and remaining database operations.
- Thread loader conversion from deprecated `AsyncTask`, lifecycle-safe state handling, and exception recovery.
- Atomic/locked list preference mutations and full delivery aggregation for multipart SMS.
- OTP detection hardening and notification/unread normalization.
- A full dark/light dynamic Material 3 theme and functional AI provider integration.
- Validate default-SMS role behavior, permissions, and all flows on physical Android devices.

## Build
Open this project in Android Studio with JDK 17 and Android SDK 35. A successful build has not been verified in this environment.
