# AI Change Log — RichHealth Android
# This file is maintained by AI. Every change made to the codebase must be logged here
# with a datestamp, affected files, what changed, and why. Keep entries short and factual.
# Other AI agents reading this should be able to reconstruct intent from this log alone.

---

## [2026-08-09] Services → Tools: follow-up — bare date labels (owner review)

Owner wanted the card date consistent with iOS: **bare "X ago"**, no descriptive prefix
("Last insight / Updated / Checked / Last check-in" all dropped). Position unchanged
(left, under the content). No divider (Android already had none).
File: `Activities/HomeFragment.java` (setDate/setText calls for health analysis, check-in,
nutricheck, dietary, advisory, AQI).

---

## [2026-08-09] Services → Tools: ONE reusable card component + status chevron + missing cards — 6 files

### WHY
Every Tools card was hand-inlined in fragment_home.xml (no shared component), and the
card set lagged iOS. Consolidated all primary tool cards onto one reusable component,
added an iOS-style status-coloured chevron, and added the cards Android was missing so
the set matches iOS (Briefing, Health Insights, Connect-a-Device, Check-In, Daily
Advisory, AQI, Dietary Insights, NutriCheck, Health Feed).

### NEW: app/src/main/java/Utils/ServiceCardView.java
- Custom compound view extending MaterialCardView; inflates view_service_card.xml (<merge>).
- Implements the TOOLS CARD STANDARD (icon 30dp · teal title + top-right pill · subtitle ·
  meta date · trailing chevron). Whole card surface is the click target.
- Public API: setIcon, setTitle, setSubtitle, setPill(StatusPill.Intent,text), hidePill,
  setDate, hideDate, setChevronStatus(ChevronStatus{NORMAL,ATTENTION,URGENT}); getters
  getPillView/getMetaView/getSubtitleView (escape hatch used to keep existing wiring intact).
- Card chrome (corner 18dp, elevation 0, bg @color/rh_surface) set in code. Chevron tint:
  NORMAL=@color/rh_text_tertiary, ATTENTION=@color/rh_warning, URGENT=@color/rh_danger.
- Optional XML attrs serviceIcon/serviceTitle/serviceSubtitle for the static cards.

### NEW: app/src/main/res/layout/view_service_card.xml
- The single card row layout. Colours use @color/rh_* only (no hardcoded hex).

### res/values/attrs.xml
- Added <declare-styleable name="ServiceCardView"> (serviceIcon/serviceTitle/serviceSubtitle).

### res/layout/fragment_home.xml
- Replaced the 4 hand-inlined MaterialCardView cards (health_analysis_card, checkin_home_card,
  nutri_check_card, watch_connect_card) with <Utils.ServiceCardView> (SAME ids).
- Added 4 new ServiceCardViews: daily_advisory_card, aqi_card, dietary_insights_card (promoted
  from the hidden compat block to visible), feed_card. Order now mirrors iOS.
- Kept the Daily Briefing carousel card (daily_digest_card) as-is; the digest TEXT is now a
  separate Daily Advisory card.
- Moved retired 0dp compat ids (health_status_chip, health_analysis_last_updated,
  checkin_start_button, nutri_stale_indicator, watch_connect_button, dietary_stale_indicator)
  into the hidden compat block so existing null-guarded Java toggles still resolve.
- Rewrote the TOOLS CARD STANDARD comment to point at the component.

### Activities/HomeFragment.java
- Field types: healthAnalysisCard / checkInHomeCard → Utils.ServiceCardView; added fields
  nutriCheckCard, watchConnectCard, advisoryCard, aqiCard, dietaryCard, feedCard, advisoryFullContent.
- initViews: bound legacy pill/subtitle/meta fields to the ServiceCardView inner views via
  getters (findViewById of the old inner ids removed) — preserves ALL existing pill/stale logic.
- setupClickListeners: watch pill now from watchConnectCard.getPillView() (old id gone); added
  clicks for advisory (expand digest in CardInfoDialog), aqi (AQI history), feed (openFeedTab).
- Status-coloured chevron wired per iOS contract (URGENT>ATTENTION>NORMAL):
  Health = URGENT if CRITICAL, else ATTENTION if healthDataNeedsUpdate/BAD/NEEDS_ATTENTION;
  Check-In = ATTENTION if due/pending/in-progress; NutriCheck/Dietary = ATTENTION if stale;
  Advisory = ATTENTION if digest stale; AQI/Feed = NORMAL.
- Daily Advisory: now calls fetchDailyDigest() on load; showDigestCard/empty/error paths
  repointed from the briefing card to advisoryCard (subtitle=content, date=generatedAt, pill/
  chevron on `stale`).
- AQI card: added updateAqiCard() reading cached AQI (aqi_prefs: cached_aqi/cached_city/
  last_update_time); pill via aqiIntent + getAqiQualityLabel; refreshed from updateIQAirDisplay().
- Dietary card: added fetchDietaryInsights() (GET /api/home/dietary-insights) → "Eat more: … ·
  Limit: …" subtitle, lastUpdated meta, stale chevron. Helpers added: aqiIntent, relativeFromMillis,
  joinFoods.

### Activities/ServicesFragment.java
- Added public showFeedTab() so the Health Feed card can switch the host to the Feed tab.

---

## [2026-03-24] Upgrade/limit flow fixes — 4 files changed

### MedicalReportApiService.java
- Added `default void onLimitReached(String message)` to `OnReportUploadListener` interface
- Added `default void onNotAllowed(String message)` to `OnAnalysisListener` interface
- Upload (OkHttp): now checks `response.code() == 429` → calls `onLimitReached` with server message
- Analyze (Volley): now checks `error.networkResponse.statusCode == 403` → calls `onNotAllowed` with server message
- Default implementations fall through to `onError` so existing callers not broken

### MedicalReportsActivity.java
- Added import: `Utils.ProUpgradeDialog`
- Replaced `showProUpgradeDialog()` — removed demo toggle that was granting free users pro access locally without payment. Now opens real `ProUpgradeDialog` with Razorpay payment flow
- Upload callback: added `onLimitReached` override → shows AlertDialog with Upgrade button → opens `ProUpgradeDialog`
- Analyze callback: added `onNotAllowed` override → shows AlertDialog with Upgrade button → opens `ProUpgradeDialog`. Resets file status to pre-failed state so user can retry after upgrading

### HealthAnalysisActivity.java
- Added import: `Utils.ProUpgradeDialog`
- Fixed `showLimitReachedDialog()`: Upgrade button had `// TODO` comment and did nothing. Now opens `ProUpgradeDialog` and syncs pro status on success

### ProStatusManager.java — `checkProAccessOnStartup()`
- Now reads `tier` field from `/api/user/pro-access` response (backend already returns it, Android was ignoring it)
- If server returns a valid non-free tier, uses it as the plan name instead of stale locally stored value
- Falls back to locally stored plan if server returns empty or "free"

---

## [2026-03-24] Remaining upgrade dialog & usage display fixes — 4 files changed

### PodcastAdapter.java
- Added import: `Utils.ProUpgradeDialog`
- Replaced `showProUpgradeDialog()`: removed demo toggle `proStatusManager.setProStatus(!currentStatus)` + fake Toast. Now opens real `ProUpgradeDialog`; notifies adapter if user upgrades

### HealthFeedActivity.java
- Added import: `Utils.ProUpgradeDialog`
- Replaced `showProUpgradeDialog()`: removed stub that only showed "Payment flow would start here" Toast. Now opens real `ProUpgradeDialog`; calls `adapter.notifyDataSetChanged()` if user upgrades

### UsageStatusActivity.java
- Added import: `Utils.ProUpgradeDialog`
- Free user NutriCheck usage card: changed `"Locked"` → `"5/month"` (free tier gets 5 NutriCheck uses/month)
- Feature table NutriCheck (free tab): changed `"--"` → `"5/month"` to match actual free tier limit
- Feature table AI model row: was hardcoded `"All"` for every tier. Now tier-aware: free/plus → `"5 Models"`, pro/ultra → `"All"`
- Upgrade button: had `// TODO` and did nothing. Now opens `ProUpgradeDialog` and syncs pro status on success; pro users click through to finish

### HomeFragment.java — usage dialog upgrade button (line ~3772)
- Upgrade button had `// TODO: Navigate to upgrade/payment when ready` with no action
- Now opens `ProUpgradeDialog` and syncs pro status on success

---

## [2026-03-24] Health Analysis business logic fixes — 2 files changed

### HealthAnalysisActivity.java
- Added `skipTabRender` boolean field to prevent double-render after POST generate
- POST generate success: sets `skipTabRender = true` before calling `loadHealthAnalysisData(false)` re-fetch
- GET success handler: skips overwriting `cachedTypeAnalyses` and skips `displayAnalysisTabContent` if `skipTabRender` is true; resets flag after
- Change banner: added `genetics` key read from `dataChangesSinceAnalysis` — family health data changes now appear in the "Changed since last analysis" banner
- Data on File summary: added `measurementCount` from `dataPoints.measurements` — was silently dropped before; now shows e.g. "12 Measurements" alongside Conditions, Medications, Reports, Symptoms, Family Members
- `getCachedUserAnalysis()`: added 24h staleness check using `cache_time` key from `user_analysis_cache` SharedPreferences — stale profile pills (gender, blood, weight, sleep) no longer shown
- `populateAqi()`: when local `aqi_prefs` has no cached AQI, now falls back to `metrics.aqi` + `metrics.location` from `lastHealthAnalysisJson` — shows server-stored AQI instead of "–"
- Hardcoded health status defaults replaced: `statusLevel` default changed from `"NORMAL"` to `""`, `reason` default changed from `"Your health metrics are stable"` to `""` — neutral gray chip shown instead of misleadingly positive green "● Health looks stable"

### HomeFragment.java
- Profile completion on home card: was using locally-computed `cachedProfilePercent`. Now uses `profileCompletion.percent` from backend response, falls back to local only if backend field is absent — fixes inconsistency with HealthAnalysisActivity which already used backend value
- Health status defaults: same fix as HealthAnalysisActivity — removed hardcoded `"NORMAL"` / `"Your health metrics are stable"` defaults in both home card and dialog code paths

---

## [2026-03-24] AIFragment (chat screen) — comprehensive UX + correctness fixes — 5 files

### fragment_ai.xml
- Header: was 2 rows (title + subtitle row). Now **single 48dp RelativeLayout row**: `ic_launcher` logo (26dp) + "Richie" bold + model pill + plan label on left; usage count + history button on right. Saves 40dp vertical space.
- Empty state: `gravity` changed `center` → `center_horizontal`; `paddingTop` set to `20dp` (not dead-centre).
- Empty state: added `ic_launcher` logo (48dp) above greeting; added `health_data_context` TextView (teal, hidden by default); `hint_box` shown only when no health data.
- Send button: `ic_send` (paper plane) → `ic_send_arrow` (arrow-up circle).
- Usage shows compact `"3/10"` not `"3 of 10"`.
- Input area padding tightened: `paddingTop=4dp`, `paddingBottom=10dp`.

### item_chat_ai.xml
- AI bubble icon: changed from `ic_richie_ai` (sparkle stars) to **`ic_launcher`** (real teal geometric logo). Size 32dp → 28dp with `marginTop=4dp`. No tint (PNG doesn't need it).

### layout_chat_limit_dialog.xml — NEW FILE
- Follows exact `layout_simple_progress.xml` card style: `#1A1A1A` card, `cardCornerRadius=16dp`, `ic_launcher` logo (56dp), white bold title, teal plan badge.
- Contains: what-happened text, upgrade benefits block (3 tier-aware lines, hidden for pro+), reset-date line, "Not Now/New Chat" + "Upgrade" MaterialButtons.

### ic_nav_richie.xml (drawable selector)
- Both `state_checked` and default were pointing to `ic_richie_ai` (sparkle stars). Changed both states to `@drawable/ic_launcher` (real app logo). ColorStateList in MainActivity applies teal/gray tint automatically.

### AIFragment.java
1. **Limit dialogs** — `showLimitReachedDialog()` and `showSessionLimitReachedDialog()` now delegate to `showChatLimitDialog()`:
   - Inflates `layout_chat_limit_dialog.xml` via `Dialog(context, R.style.DialogTheme)`.
   - Calculates reset date locally using `Calendar` (first day of next month) — no API needed.
   - Free/plus: shows Upgrade button → `ProUpgradeDialog` directly.
   - Pro+: hides upgrade block and upgrade button, expands dismiss button to full-width.
   - `buildPlanBadge()` helper returns human-readable tier name ("Free Plan", "Pro Plan" etc.).
   - `buildUpgradeBenefits(tier)` returns tier-aware String[3] — plus users see "was 25"/"was 10" copy.
   - Fixed 0/0 bug: `if (sessionLimit > 0)` guard before showing count string.
2. **Thinking animation** — single `Handler` at 400ms drives BOTH dot cycle AND message rotation:
   - `DOT_STATES = {".", "..", "...", "....", "....."}`, `DOT_INTERVAL_MS = 400`, `DOTS_PER_MSG = 5`.
   - `buildThinkingMessages()` reads `user_analysis_cache` SharedPreferences; builds data-aware pool:
     - Reports: "Analysing X medical reports" / "Cross-referencing your report findings"
     - Meds: "Reviewing your X medications" / "Checking for medication interactions"
     - Symptoms (age-aware): warns "data may not be current" if >60 days, shows exact days if 14-60 days
     - Measurements: "Reviewing your vitals" / "Analysing X health readings"
     - Always includes "Reading your health profile" + "Preparing a personalised response" + "Putting it all together"
     - Sparse-data hint ("Add reports and medications for richer insights") only if pool has < 4 useful entries
   - Messages shuffled at start; first message + "." shown immediately.
   - `ai_icon` in thinking bubble spins via `ObjectAnimator` (360° infinite, LinearInterpolator).
   - `hideThinkingAnimation()` resets all state fields + cancels handler + cancels animator.
3. **Welcome state**: `populateHealthDataContext()` reads cache, shows "Richie knows: 📋 X reports · 💊 X meds..." in teal, hides generic hint box when data exists.
4. **Welcome greeting**: "Hi [FirstName], what's on your mind?" from local DB profile; fallback "What can I help with?".
5. **Loading delays**: Were all 2000ms (non-cumulative). Fixed to actual cumulative values: 800ms / 1800ms / 2800ms.
6. **Removed `userContext`** from message POST body — backend ignores it (reads full health profile from MongoDB via `buildHealthContext()`).
7. **`onDestroy`**: cancels both `iconAnimator` and `thinkingCycleHandler` to prevent leaks.

### Backend — NO CHANGES for chat
- `buildHealthContext()` reads all health data from MongoDB; `userContext` Android was sending is never read.
- Model gating by tier works correctly. Council mode is real (system prompt changes for pro/ultra/family).

---

---

## [2026-03-25] Back navigation — whole-app overhaul — 4 files

### BackPressHandler.java — NEW INTERFACE
- New file: `com.example.richhealth.Activities.BackPressHandler`
- Single method: `boolean handleBackPress()` — return true if the fragment consumed the press, false to let MainActivity handle it.
- Used by AIFragment and HealthDataFragment.

### MainActivity.java
- Added fields: `BACK_PRESS_EXIT_INTERVAL = 2000`, `lastBackPressTime`, `backPressToast`
- Replaced bare `onBackPressed()` with a 4-step priority chain:
  1. **Panel check**: if active fragment implements `BackPressHandler`, call it — if it returns true, stop (a side panel was dismissed).
  2. **Non-Home tab**: if current fragment is not `HomeFragment`, clear back stack + `setSelectedItemId(navigation_home)`.
  3. **Back stack**: if back stack has entries (from `HomeFragment.navigateToFragment()`), pop one.
  4. **Double-press exit**: first press shows "Press back again to exit" toast; second press within 2 s calls `super.onBackPressed()` to exit.

### AIFragment.java
- Added `implements BackPressHandler` to class declaration.
- Added `handleBackPress()`: checks `chatHistoryPanel.isShowing()` first, then `savedChatsPanel.isShowing()`. Dismisses the first open panel and returns true.

### HealthDataFragment.java
- Added `implements BackPressHandler` to class declaration.
- Added `handleBackPress()`: checks all 6 side panels in order — `symptomsPanel`, `measurementsPanel`, `medicationsPanel`, `medicalReportsPanel`, `medicalDataPanel`, `familyMembersPanel`. Dismisses the first open panel and returns true.

### What is NOT changed (intentional)
- `SignupActivity.onBackPressed()` — already correct: navigates multi-step form back, confirms exit at step 0.
- `OnboardingActivity.onBackPressed()` — already correct: delegates to `handleBack()` with exit confirmation.
- All other Activities (HealthAnalysisActivity, MedicalReportsActivity, HealthFeedActivity etc.) — back button calls `finish()` which is correct behaviour for activities launched from elsewhere.
- Dialogs created as local variables (model picker, edit dialogs, limit dialogs) — Android dismisses these on back press by default; no change needed.

---

## Intent & Context

**What was asked:** Audit the full app's paid service flow — tiers, limits, upgrade dialogs, backend enforcement — then fix what's wrong on the Android side.

**What we found:**
1. `showProUpgradeDialog()` in MedicalReportsActivity was toggling pro status locally — debug code that shipped to production, giving free users pro access by tapping Upgrade
2. The Upgrade button in HealthAnalysisActivity's limit dialog had a TODO and did nothing
3. Neither the upload nor the analyze API calls checked for 429/403 responses — limit enforcement from backend was silently ignored
4. On cold app start, the `tier`/plan name from the server was being discarded, leaving the usage card showing stale plan info

**Intent of fixes:** Make all three upgrade touchpoints consistent — they all now open the same `ProUpgradeDialog` (which already worked correctly for NutriCheck). Ensure backend limit signals (429, 403) surface to the user as upgrade prompts rather than generic errors. Sync tier name from server on startup so displayed plan is always fresh.

**Backend changes prompted separately** (see backend prompt in session) — not applied here.
