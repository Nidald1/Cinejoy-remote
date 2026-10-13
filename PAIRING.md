# CineJoy Remote — Pairing & Release Guide

Phone (Android) + PC (CineJoy Downloader ≥ 1.2.9) pair through Supabase.
No Firebase, no extra accounts. One Supabase email is the whole pairing.

## 1. Pair the PC (2 minutes)

1. Open the PC app → left rail → **Phone** tab.
2. Enter your Supabase email + password → **Sign up** once (creates the
   account), then **Sign in**.
3. The tab shows `Signed in as <email>` with a ● dot on the Phone tab.
   While signed in, the PC mirrors downloads + library and executes the
   phone's commands. **Sign out** stops everything (mirror rows stay, but
   go stale — the phone shows "No PC online yet" only when no heartbeat
   row is fresh; stale rows simply stop updating).

## 2. Pair the phone (1 minute)

1. Install the APK (first time: `android/app/build/outputs/apk/debug/app-debug.apk`,
   later: Settings → Check for updates).
2. Sign in with the **same email**. The Downloads tab lists your PC by its
   Windows hostname; tap queue buttons or open Library / Activity.
3. Background the app and start a PC download — a start/finish alert
   arrives in seconds (app open or backgrounded; a force-closed app cannot
   be woken — Android limit, the Activity tab keeps the record).

## 3. Publish the Android repo + auto-releases

`gh` is not on this machine, so these are dashboard steps (one time):

1. Create a **public or private** repo named `cinejoy-remote` on GitHub.
2. Push this folder:
   `git init && git add -A && git commit -m "CineJoy Remote v1" && git remote add origin <url> && git push -u origin main`
3. Set the app's update source — in
   `android/app/src/main/java/com/nedal/cinejoyremote/update/UpdateChecker.kt`
   replace `YOUR_GITHUB_USER` with your GitHub username, commit + push.
   (Until then, Settings → Check for updates says "not configured".)
4. Tag a release: `git tag v1.0.0 && git push origin v1.0.0` → Actions builds
   the APK and attaches it to the GitHub Release → the phone's Settings tab
   offers Download & install.
5. Optional signed builds: generate a keystore once
   (`keytool -genkeypair -keystore cinejoy.jks -alias cinejoy -keyalg RSA -keysize 2048 -validity 9125`),
   add repo secrets `ANDROID_KEYSTORE_BASE64` (base64 of the .jks),
   `CINEJOY_KEYSTORE_PASSWORD`, `CINEJOY_KEY_ALIAS`, `CINEJOY_KEY_PASSWORD`.
   Without secrets every tag still ships a debug APK — nothing fails.

## 4. Ship the PC update

`npm run dist` in `sopranos_app` (electron-builder → `release/`), install the
1.2.9 setup on the PC. The Phone tab appears in the left rail; sign in again
(the 1.2.8 build never stored a session).

## Troubleshooting

- Phone sees no PC: PC Phone tab must say "Signed in"; same email both ends.
- No alerts: Android Settings → Apps → CineJoy Remote → Notifications → allow;
  Android 13+ also asks on first launch — accept it.
- Commands stuck "pending": PC app must be running and signed in (polls every 3s).
- Update check says "not configured": step 3.3 above (owner constant).
