# NajiOne — Two-Skin UI Roadmap (Freedom default + Nexus secondary)

Audience: AI coding agents (and humans) implementing this in `E:\project\NajiOne` (package `com.najishab.one`, Jetpack Compose).
Source of truth for the NEW look: `E:\project\NajiOne\Freedom Redesign Mockup.html` (open it in a browser; click Connect, the error toggle, and all 4 tabs). Its `<script>` at the bottom is the behaviour spec.
The CURRENT look is called **Nexus** (`app\src\main\java\com\najishab\one\ui\nexus\`: NexusShell, NexusHome, NexusDirectory, NexusSession, NexusTheme).

## 0. Goal

1. Two selectable skins: **Freedom** (new, DEFAULT) and **Nexus** (today's UI, kept intact).
2. The user switches in Settings (Display area) and it applies instantly, no activity restart, and the choice persists.
3. Both skins use exactly the same connect logic, destinations, back handling and data. Only presentation differs.
4. Nothing that is reachable today may become unreachable under Freedom.

## 1. Facts about the current code (verified by reading; do not re-derive)

- `ui/AppScreen.kt` is the root. Landing screen = `if (onHome) NexusShell(...)`. State: `activeTab` (home or a tab id), `navStack`, `activeModal`, `nexusPage` (rememberSaveable String of `NexusPage` enum: HOME, METHODS, TOOLS, SETTINGS), `settingsRoute`.
- Every visited tab stays composed and is parked off-screen with `offset(x = 10000.dp)`. This is deliberate (running scans etc.). Do not remove or restructure it.
- The hosted **settings** tab and **vpn_settings** tab are wrapped in `LocalNexusSkin provides true` + `Box(Modifier.fillMaxSize().nexusBackdrop())`. The settings tab ALSO draws `NexusBottomBar` over itself and adds `NEXUS_NAV_RESERVE` to `LocalSystemBottomPadding`. These blocks must become skin-aware.
- `BackHandler` in AppScreen: modal -> navStack -> non-home tab -> `nexusPage != HOME` goes to HOME -> exit dialog. Freedom needs the same rule for its own page state.
- `LocalNexusSkin` (NexusTheme.kt) is read by `frostedGlass` (ui/home/Glass.kt) and the `Ios` palette (ui/settings/IosSettings.kt area) to switch glass/colors on Settings screens. Find ALL usages before changing it (grep the whole `app/src` for `LocalNexusSkin`).
- `NexusHome.kt` contains the real connect logic: `LinkState {OFF, CONNECTING, ON, ERROR}` (internal), failed-state derivation (CONNECTING->IDLE without user stop => ERROR, auto-clears after 12 s), VPN consent launcher, `connectLast()` (ScanGuard check, `VpnService.prepare`, `WidgetConnectService.start(context, methodId, null)`), `onPower` (busy -> `WidgetConnect.disconnect`, has last -> connect, else open Methods).
- Live data: `rememberNexusSession()` -> `WidgetLive.conn` (up, busy, phase, method, country, since) + `LastEngine`; `WidgetLive.traffic` (rxRate, txRate); `WidgetFormat.rate/clock/flag/countryName`; `WidgetMethods.app(id)/isMethod(id)`; `WidgetPainter.labelOf(app)`.
- Catalog: `NexusCatalog.methodGroups()/toolGroups()` built from `HomeDestinations.DOCK + GRID_DEFAULT` (registry untouched, unknown ids fall into "Additional Methods"). `describe(id)` in NexusDirectory.kt is `private`.
- Preferences pattern to copy: `ui/home/Appearance.kt` (SharedPreferences default file + `mutableStateOf` mirror + `init(context)` called once at activity start + `setX(context, v)`).
- Settings search: `ui/settings/SettingsIndex.kt` has a hand-kept `SettingsEntry` list, enforced by `SettingsIndexTest` (a row with a title missing from the index FAILS the test). Any new Settings row must be added there.
- Strings: use `tr("فارسی", "English")` (com.najishab.one.store.tr) or string resources. The mockup is mostly English; the app must be bilingual.
- Layout insets: `LocalSystemTopPadding`, `LocalSystemBottomPadding`, `LocalContentTopInset`.

## 2. Decisions (defaults chosen; change only if the owner says so)

| # | Question | Default |
|---|----------|---------|
| D1 | Skin storage | Pref key `app_skin`, values `freedom` / `nexus`, default `freedom` (existing installs also get Freedom, as requested: Freedom is the main UI). |
| D2 | What do the 3 home protocol cards do? | They are the mockup's favourites: MAE, FLUX, WARP² = ids `mae`, `flux`, `warp_on_warp` (verify ids in HomeDestinations). Tap = "use this protocol for the next connect": store `FreedomPrefs.preferredMethod`; connect uses `preferredMethod ?: last`, only if `WidgetMethods.isMethod(id)`; otherwise open that method's own page. Green tick on the selected one. No ping on cards. |
| D3 | Tabs | Freedom bottom bar has 4 tabs, same count as the mockup: **Home / Engines / Cloud & Tools / Settings**. Engines = `NexusCatalog.methodGroups()`; Cloud & Tools = `NexusCatalog.toolGroups()`; Settings = existing hosted settings tab. A unit/instrumentation check must prove the SET of reachable ids is identical to Nexus. |
| D4 | Ping / exit IP | The mockup's speed, ping and masked IP are FAKE demo numbers. Port only real data: down/up from `WidgetLive.traffic`, timer from `conn.since`, flag+country from `conn.country`. Ping chip: omit unless a real measurement already exists in the codebase (check WidgetLive). Never invent values. |
| D5 | Header buttons | "Support" -> open the existing help destination (`tutorial`). Notification bell: hide (no notification feature exists). |
| D6 | Header logo | Reuse `AppIconMark` (real launcher icon) instead of the mockup's gray "M" square. |
| D7 | Mockup-only controls | Do NOT port: the "next attempt errors" toggle, the right-hand explainer panels, the toast on engine select (a short Toast is fine), the phone frame/notch/status bar. |
| D8 | Engine list rows | Tap a row = open that method's page (same as Nexus `onOpen(app)`), NOT select. Mockup's engine names that do not exist in this project (e.g. Proton VPN, Windscribe, GitHub Tunnel, Telegram Proxy, Free Configs) must NOT be invented: render the real catalog only. Title shows the real count ("All N engines"). |

## 3. Visual spec (from the mockup; copy values, do not eyeball)

### Palette
Night sky `#0A1120` · Card `#141922` · Card2 `#1A212E` · Border `#232C3B` · Cage steel `#93A7C4` (connected tint `#C8B299`) · Primary/dawn `#D97706` · Sunrise `#FFB35C` · Free/OK `#2FD6A3` · Bird `#1996D2` · Text `#F2F5F9` · Muted `#8B96A8` · Amber `#F5B94A` · Error `#FF6B5E` (button error bg `#4A2320`, text `#FFB4AB`, border `#7A3530`) · Connected button bg `#17352B`, text `#7FE7C6`, border `#1F7A5C`.
Sky night gradient `#070F1E -> #0D1930 -> #1A2740`, dawn gradient `#2B4D68 -> #5F7F9C -> #D98D5F -> #FFC98A`; sun radial `#FFEDB9 -> #FFB35C -> transparent`, 170dp; hills `#101C30` and `#080F1C`; motes `#FFD9A0`.
Latin font Manrope, Persian font Vazirmatn. DO NOT load Google Fonts at runtime (the mockup does; the app must bundle fonts as resources or reuse what `ui/theme/Type.kt` already has — check `res/font` first).
Sizes: scene corner 26dp, cards 16–20dp, cage 188 x 222dp (viewBox 220x260), bird 86dp, horizontal padding 18dp, min touch 48dp, bottom bar = 4 tabs with text labels.

### Home layout (no scrolling on a normal phone)
Header (logo, title "NajiOne VPN", sub-line "Not protected" / "Protected · <engine>", Support chip) -> Scene (weight 1f, min ~216dp, state chip top-start) -> status title + sub + timer -> Connect button -> "Connection protocol" header with "All N engines >" link -> 3 protocol cards -> Live speed card (Download / Upload with 40-point sparklines) -> country/IP row. For very large font scale or tiny screens, fall back to `verticalScroll` instead of clipping.

### Scene state machine (driven by `LinkState`, never by timers)
Map: OFF=idle, CONNECTING=connecting, ON=connected, ERROR=error.

| State | Chip text | Cage | Sky/sun | Bird (Lottie speed) | Button | Title / sub |
|-------|-----------|------|---------|---------------------|--------|-------------|
| OFF | "در قفس / In the cage" | closed, swaying ±1.25° over 7.5 s (origin top) | night, sun low (translateY 46, alpha .25) | inside cage, speed 0.55 | primary amber "اتصال / Connect" | "Still in the cage" / "Tap connect to break free — via <engine>" |
| CONNECTING | "در حال گشودن قفس… / Opening the cage…" | rattles (0.13 s loop, <=1 dp jitter), lock shakes ±7° 0.3 s alternate | still night | speed 2.3 | disabled-looking "در حال اتصال… / Connecting…" but tap still cancels (existing behaviour) | "Breaking the cage…" / "The bird is pushing on the bars" |
| ON | "آزاد شد! / Free!" | halves open: left translate(-27,12) rot -25°, right mirrored, alpha .2, origin 88%/12% top, 1050 ms cubic-bezier(.5,.05,.3,1); top ring translateY -15 alpha 0; lock falls translateY 130 rot 55 alpha 0 (800 ms ease-in); cage color -> `#C8B299` 1.4 s | crossfade to dawn 1.5 s, sun rises 1.8 s, far birds fade in (alpha .55, 1.6 s, 0.35 s delay) + drift 15 s, motes float 5.5 s | bird rises to (0,-80dp) in 1.15 s, then after ~1.18 s loops "soar" 11 s: (0,-80,0°) -> (-90,-94,-5°) @22% -> (-14,-104,2°) @48% -> (86,-92,5°) @74% -> start; speed 1.15 | green "قطع اتصال / Disconnect" | "Free — you are protected" / "Connected via <engine> · the bird is out" + session timer |
| ERROR | "قفس نشکست — خطا در اتصال / The cage held — connection failed" | halves snap back in 240 ms, bird returns fast (450 ms), red radial flash 550 ms | back to night | speed 0.55 | red "تلاش دوباره / Try again" (button stays tappable) | "Connection failed" / "The cage held — try again or switch protocol" |

Disconnect (ON -> OFF) plays the reverse: bird returns into the cage, halves close, dawn -> night.
Reduced motion (system animator scale 0 or accessibility setting): no sway, rattle, soar, drift or motes; state changes snap; bird shows a static frame; the text/chip/button still convey state. Give the scene a `contentDescription` that states the current state.

### Assets
- Bird: ONE Lottie file, embedded in the mockup as base64 (`path:"data:application/json;base64,..."` inside `initBird`). Extract it to `app/src/main/res/raw/freedom_bird.json` (script in section 8). Add `com.airbnb.android:lottie-compose` only if not already a dependency; the repo has a `local-maven` folder, so check whether the build must work offline and add the artifact there if needed. License: LottieFiles Simple License (mockup credits the original; keep the credit in About/licenses if the project lists any).
- Cage, lock, hills, far birds: vector paths. Copy the exact `d="..."` strings from the `<svg class="cage-svg">`, `.hills` and `.far-birds` elements in the mockup HTML and draw them on a Compose `Canvas` using `androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toPath()` (or build an `ImageVector`). The cage has four independently animated groups: `cageTop`, `cageL`, `cageR`, `cageLock`.

## 4. Architecture

New package `com.najishab.one.ui.skin` (shared) and `com.najishab.one.ui.freedom` (Freedom-only). Nexus files are only touched in Phase 2 (behaviour-preserving extraction) and for visibility changes.

```
ui/skin/AppSkin.kt        enum AppSkin { FREEDOM, NEXUS }, object SkinPrefs (key app_skin, mutableStateOf mirror, init/set), val LocalAppSkin
ui/skin/LinkController.kt LinkState (moved from NexusHome, still internal), rememberLinkController(session) -> {state, conn, traffic, onPower, connectLast(methodId?)}
ui/freedom/FreedomTheme.kt   object Freedom (palette), freedomCard()/freedomBackdrop() modifiers, FREEDOM_NAV_RESERVE
ui/freedom/FreedomScene.kt   Canvas cage + sky + sun + hills + motes + far birds + flash; takes (state: LinkState, reducedMotion)
ui/freedom/FreedomBird.kt    Lottie wrapper: speed by state, offset/rotation animation (rise, soar, return)
ui/freedom/FreedomHome.kt    header, scene, status, connect button, protocol cards, speed card, country row
ui/freedom/FreedomEngines.kt Engines page (search + groups + rows)  [reuse NexusCatalog, SettingsIndex search like NexusDirectory]
ui/freedom/FreedomTools.kt   Cloud & Tools page
ui/freedom/FreedomShell.kt   FreedomPage enum {HOME, ENGINES, TOOLS, SETTINGS}, FreedomShell(...), FreedomBottomBar(...)
ui/settings/ (existing)      new "App skin" row + picker page, SettingsIndex entry
```

`AppScreen.kt` gets a thin dispatcher: `when (SkinPrefs.skin) { FREEDOM -> FreedomShell(...) ; NEXUS -> NexusShell(...) }`, with the same `onOpen` / `onOpenSetting` lambdas. Page state: keep `nexusPage`, add `freedomPage` (rememberSaveable) and extend the BackHandler: on the active skin's non-HOME page go to its HOME.

## 5. Phases (do in order; each phase must leave the app building and Nexus unchanged)

### Phase 0 — Baseline
- [ ] `git status` clean or on a new branch `feature/freedom-skin`. Run `gradlew.bat :app:assembleDebug` and the unit tests once to know the baseline.
- [ ] Skim: AppScreen.kt, NexusShell/Home/Directory/Theme/Session, Appearance.kt, SettingsIndex.kt (head), HomeDestinations.kt, WidgetConnect.kt, WidgetMethods, WidgetLive.
- [ ] Locate where `Appearance.init(context)` is called (MainActivity or Application) and where Settings > Display rows live (grep `Appearance.setTheme` / `Appearance.dockLabels` in `ui/settings`). Record both paths in the PR description.

### Phase 1 — Skin plumbing (no UI change yet)
- [ ] `AppSkin`, `SkinPrefs` (copy Appearance.kt style; default FREEDOM), `LocalAppSkin`. Init next to `Appearance.init`.
- [ ] `LocalNexusSkin` keeps its meaning ("Nexus look on settings screens"): wherever AppScreen currently provides `true`, provide `SkinPrefs.skin == NEXUS`. Provide `LocalAppSkin` alongside.
- Done when: app builds, and with skin forced to NEXUS everything looks exactly as before.

### Phase 2 — Extract shared connect logic (pure refactor)
- [ ] Move `LinkState`, the failed-state derivation, consent launcher, `connectLast`, `onPower` out of `NexusHome` into `rememberLinkController(session, onChooseMethod, methodOverride: String? = null)`. NexusHome calls it with no override.
- [ ] Make `describe(id)` in NexusDirectory `internal`; make `stateColor`-like helpers skin-specific (keep Nexus ones private).
- Done when: Nexus Home behaves identically (connect, cancel while connecting, error state clears after 12 s, no-method case opens Methods). Do a manual run-through before moving on.

### Phase 3 — Freedom building blocks (parallel track A)
- [ ] `Freedom` palette object + `freedomBackdrop()` + `freedomCard(shape)`.
- [ ] `FreedomScene` per the table in section 3. Use one `updateTransition(state)` or a few `animateFloatAsState` values: `split` (0..1, 1050 ms), `lockDrop`, `dawn` (0..1, 1500 ms), `sun` (1800 ms), `cageTint`. Infinite transitions (sway, drift, motes, soar) run only while the scene is composed and the lifecycle is RESUMED, and are skipped under reduced motion. ERROR uses 240 ms for `split` and triggers a one-shot red flash.
- [ ] `FreedomBird`: Lottie composition from `R.raw.freedom_bird`, `LottieConstants.IterateForever`, speed by state (0.55 / 2.3 / 1.15 / 0.55), position animated separately (rise 1150 ms; soar 11 s loop only after the rise has finished; on error/disconnect animate back to the cage slot first).
- [ ] A `@Preview` per state (and an in-app debug switch is NOT needed).
- Done when: each of the 4 states visually matches the mockup side by side.

### Phase 4 — Freedom Home (track B, needs Phase 2 and 3)
- [ ] `FreedomHome(session, onOpen, onChooseMethod)` using `rememberLinkController`. Header, scene, status block, connect button (4 states, section 3), 3 protocol cards (D2), speed card (down/up + 40-sample sparkline built from `traffic.rxRate/txRate`, sampled at 1 Hz only while ON, cleared on disconnect), country row (D4), "All N engines >" -> `onPage(ENGINES)`.
- [ ] Respect `WidgetLive.wantCountry("nexus", true)` style registration — copy the DisposableEffect from NexusHome with a Freedom-specific key.
- [ ] `ScanGuard.isScanBusy()` toast stays (it is in the shared controller).
- Done when: connect / cancel / error / disconnect all work on a real device with a real tunnel, and Home does not scroll on a 360x800dp screen at default font scale.

### Phase 5 — Engines and Cloud & Tools pages (track C)
- [ ] `FreedomEngines`: search field (same `SearchMatcher` + Settings-row search as NexusDirectory, so search results can still open Settings rows), group headers from `NexusCatalog.methodGroups()`, rows with monogram icon (2 letters), name, `describe(id)`, live/last indicator from the session (same logic as Nexus `EntryStatus`). Row tap = `onOpen(app)`.
- [ ] `FreedomTools`: same for `toolGroups()`.
- Done when: every id in `methodGroups()` + `toolGroups()` is reachable from Freedom. Add a JVM test that collects the ids from both skins' catalogs (they share `NexusCatalog`, so also assert nothing is filtered out in the Freedom UI models).

### Phase 6 — Freedom shell and navigation (track D)
- [ ] `FreedomShell(page, onPage, onOpen, onOpenSetting)`: `Crossfade` between pages like NexusShell (260 ms), `FreedomBottomBar` floating or docked per mockup (docked, `rgba(13,17,24,.96)`, top border `#232C3B`, 4 tabs with labels, selected color `#2FD6A3`, min height 48dp touch). Export `FREEDOM_NAV_RESERVE`.
- [ ] AppScreen: dispatcher for the landing screen; skin-aware hosted `settings` and `vpn_settings` blocks (bar, backdrop, bottom reserve, `LocalNexusSkin`/`LocalAppSkin`); `freedomPage` state; BackHandler branch.
- [ ] From Settings, the bar's other tabs must return to the matching Freedom page (`freedomPage = target.name; switchTab(homeTab)`), mirroring what Nexus does today.
- Done when: switching skin while on any screen never crashes, never leaves a stale bar, and back-button behaviour is identical in both skins.

### Phase 7 — Settings look under Freedom
- [ ] Enumerate every consumer of `LocalNexusSkin` (Glass.kt `frostedGlass`, Ios palette, any others). Generalise them to `when (LocalAppSkin.current)` so Freedom gets: backdrop = Freedom night gradient, cards `#141922` with `#232C3B` hairline, accent `#2FD6A3` for switches, primary actions `#D97706`. Keep behaviour for Nexus byte-for-byte.
- [ ] Other feature screens (Nodes, Cloud, Scanner, transports...) are OUT OF SCOPE: they keep their current look in both skins (as they do under Nexus today).

### Phase 8 — Settings switch (track D)
- [ ] In the Display/appearance section found in Phase 0, add a row "پوسته / App skin" (value text: Freedom or Nexus) that opens a picker with two preview cards (static Canvas thumbnails: Freedom = caged bird on night sky, Nexus = power button + rings). Selecting applies immediately via `SkinPrefs.set(context, skin)` and persists.
- [ ] Add the `SettingsEntry` (title both languages; keywords: skin, theme, پوسته, ظاهر, Freedom, Nexus, design, طرح) to `SettingsIndex` and satisfy `SettingsIndexTest`.
- [ ] Add a line to `ChangelogData.kt` describing the two skins.

### Phase 9 — Accessibility, RTL, TV, performance
- [ ] RTL: Compose mirrors rows; the scene is symmetric; verify header, bottom bar order and chevrons in Persian.
- [ ] TalkBack: connect button has a state-aware label; scene has a state `contentDescription`; protocol cards are `Role.RadioButton` with selected state.
- [ ] D-pad/TV: reuse the project's focus helpers (`ui/home/TvFocus.kt`, `NodesFocus.kt`) so cards and the connect button are focusable with a visible focus ring.
- [ ] Performance: no animation work when Home is not on screen; Lottie paused when not RESUMED; cache parsed `Path`s with `remember`; no allocations inside `DrawScope` loops.
- [ ] Font scale 1.3x and a 320dp-wide device: no clipping.

### Phase 10 — QA and release
Manual matrix (both skins): cold start; first install (default = Freedom); switch skin from Settings and from a deep Settings sub-page; rotate / split-screen; connect, cancel while connecting, forced failure (disconnect network), disconnect; method with no consent yet; widget opens a tile (`WidgetOpen.pending`) while in each skin; process death restores page (saveable); back button chain in each page; Persian and English; light/dark `Appearance.theme` still behaves; accessibility animator scale off.
Commands: `gradlew.bat :app:assembleDebug`, `gradlew.bat :app:testDebugUnitTest` (includes SettingsIndexTest).

## 6. Work split for several AIs (avoid file conflicts)

| Track | Owner of files | Needs |
|-------|----------------|-------|
| Core | `ui/skin/*`, NexusHome.kt (extraction only), AppScreen.kt | none; finish Phases 1–2 FIRST and merge |
| A Scene | `ui/freedom/FreedomScene.kt`, `FreedomBird.kt`, `res/raw/freedom_bird.json`, gradle dependency | Phase 1 |
| B Home | `ui/freedom/FreedomHome.kt`, `FreedomTheme.kt` | Phase 2, a stub of Scene API: `FreedomScene(state: LinkState, modifier)` |
| C Pages | `ui/freedom/FreedomEngines.kt`, `FreedomTools.kt` | Phase 1 |
| D Shell+Settings | `ui/freedom/FreedomShell.kt`, `ui/settings/*` (new row only), `SettingsIndex.kt`, Glass/Ios palette generalisation | Phase 1 |
Only the Core owner edits AppScreen.kt; everyone else sends the owner the exact snippet to insert.

## 7. Rules for every agent

1. Do not modify engine/tunnel/service code, `WidgetConnect*`, `MyVpnService`, `TunnelController`. Connection behaviour stays exactly as is.
2. Do not delete or rename Nexus files or symbols except the Phase 2 extraction; Nexus must stay selectable and pixel-identical.
3. Do not invent features, engines, numbers or IPs that the mockup shows only as demo data (D4, D8).
4. All user-visible text via `tr(fa, en)` or string resources, both languages.
5. Keep the parked-tab architecture in AppScreen.
6. Small, reviewable commits per phase; write the phase checkbox results in the PR description.
7. If something here contradicts the code, trust the code, then note the discrepancy and adjust the plan.

## 8. Helper: extract the Lottie JSON from the mockup

```python
# run from E:\project\NajiOne
import re, base64, pathlib
html = pathlib.Path("Freedom Redesign Mockup.html").read_text(encoding="utf-8")
m = re.search(r'path:"data:application/json;base64,([A-Za-z0-9+/=]+)"', html)
out = pathlib.Path(r"app/src/main/res/raw/freedom_bird.json")
out.parent.mkdir(parents=True, exist_ok=True)
out.write_bytes(base64.b64decode(m.group(1)))
print(out, out.stat().st_size, "bytes")
```
(The mockup's first `<script src="data:application/javascript;base64,...">` is the Lottie web player; it is NOT needed in the app.)

## 9. Tooling notes (Filesystem MCP on Windows)
- Use `edit_file` with `dryRun: true` first, then `false`; one edit object per call (batching can silently time out); re-read with `read_text_file` after each edit.
- `search_files` matches file NAMES only; to find usages (e.g. `LocalNexusSkin`) read files or use a shell `rg`/`findstr`.
- Use `write_file` only for new files.

## 10. Definition of done
- Fresh install shows Freedom; Settings > Display > App skin switches to Nexus and back instantly, persisted across restarts.
- All 4 scene states match the mockup; real tunnel state drives them; reduced-motion respected.
- Every destination reachable in Nexus is reachable in Freedom; Settings (and its sub-pages, search, deep links from widgets) work in both.
- Build and `SettingsIndexTest` pass; no regressions in Nexus.
