# RichHealth Android — Dead Code & Unused Resources Report

> **Generated:** February 22, 2026  
> **Scope:** Full codebase deep analysis — 82 Java files, 89 layouts, 108 drawables, 5 animations, all values/menus/colors/raw resources  
> **Method:** Every file read, every method/class/resource cross-referenced across the entire codebase via exhaustive search

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Unused Java Files (Entire Classes)](#2-unused-java-files-entire-classes)
3. [Unused Activities & Fragments](#3-unused-activities--fragments)
4. [Unused Methods/Functions Within Used Files](#4-unused-methodsfunctions-within-used-files)
5. [Unused Layout XML Files](#5-unused-layout-xml-files)
6. [Unused Drawable XML Files](#6-unused-drawable-xml-files)
7. [Unused Animation Files](#7-unused-animation-files)
8. [Unused Values Resources (Colors, Styles, Themes, IDs)](#8-unused-values-resources-colors-styles-themes-ids)
9. [Unused Raw Resources](#9-unused-raw-resources)
10. [Manifest Issues](#10-manifest-issues)
11. [Dead Navigation Chains](#11-dead-navigation-chains)
12. [Full Navigation Flow (Reference)](#12-full-navigation-flow-reference)
13. [Summary Statistics](#13-summary-statistics)

---

## 1. Executive Summary

| Category | Unused | Total | % Unused |
|----------|--------|-------|----------|
| Java files (entire classes) | **14** | 82 | 17% |
| Activities | **2** (+1 effectively unreachable) | 9 | 22-33% |
| Fragments | **2** | 10 | 20% |
| Dialog Fragments | **1** | 2 | 50% |
| Methods/Functions | **22** | ~600+ | ~4% |
| Layout XMLs | **19** | 89 | 21% |
| Drawable XMLs | **32** | 108 | 30% |
| Animation XMLs | **1** | 5 | 20% |
| Color definitions | **3** | 9 | 33% |
| Style definitions | **8** | 17 | 47% |
| Theme definitions | **2** | 2 | 100% |
| ID definitions | **3** | 4 | 75% |
| Raw files | **2** | 6 | 33% |
| **Total unused items** | **109** | | |

---

## 2. Unused Java Files (Entire Classes)

These 14 files are **never imported, instantiated, or referenced** from any other file in the codebase. They can be safely deleted.

### Activities (2 files)

| # | File | Path | Reason |
|---|------|------|--------|
| 1 | `EditProfileActivity.java` | `Activities/EditProfileActivity.java` | Empty stub class (`public class EditProfileActivity {}`). Does not extend Activity. Not in AndroidManifest. Zero references. |
| 2 | `CustomPodcastActivity.java` | `Activities/CustomPodcastActivity.java` | Empty stub class (`public class CustomPodcastActivity {}`). Does not extend Activity. Not in AndroidManifest. Zero references. |

### Adapters (5 files)

| # | File | Path | Reason |
|---|------|------|--------|
| 3 | `ChatSessionAdapter.java` | `Adapters/ChatSessionAdapter.java` | Never imported or instantiated. `AIFragment` defines its own inline session adapter instead. |
| 4 | `PendingRequestAdapter.java` | `Adapters/PendingRequestAdapter.java` | Never imported. `PendingRequestsFragment` has its own inner class with the same name that it uses instead. |
| 5 | `ConnectedDoctorAdapter.java` | `Adapters/ConnectedDoctorAdapter.java` | Never imported. `ConnectedDoctorsFragment` has its own inner class with the same name that it uses instead. |
| 6 | `DoctorAdapter.java` | `Adapters/DoctorAdapter.java` | Never imported. `FindDoctorFragment` has its own inner class with the same name that it uses instead. |
| 7 | `WorkoutTypeAdapter.java` | `Adapters/WorkoutTypeAdapter.java` | Imported in `WorkoutsFragment` and declared as a field, but **never instantiated** (`new WorkoutTypeAdapter(...)` appears nowhere). Zombie import. |

### Models (2 files)

| # | File | Path | Reason |
|---|------|------|--------|
| 8 | `Symptom.java` | `Models/Symptom.java` | Standalone model never used. Codebase uses `MedicalData.Symptom` (inner class) instead. |
| 9 | `SignupPagerAdapter.java` | `Models/SignupPagerAdapter.java` | Never imported or instantiated anywhere. Misplaced under Models/. Orphaned code. |

### API Services (3 files)

| # | File | Path | Reason |
|---|------|------|--------|
| 10 | `ChatApiService.java` | `Api/ChatApiService.java` | Never imported or instantiated. AI chat uses direct Volley/OkHttp calls in `AIFragment` instead. |
| 11 | `PodcastApiService.java` | `Api/PodcastApiService.java` | Never imported or instantiated outside its own file. |
| 12 | `WorkoutApiService.java` | `Api/WorkoutApiService.java` | Never imported or instantiated outside its own file. |

### Utils (3 files)

| # | File | Path | Reason |
|---|------|------|--------|
| 13 | `CustomTableView.java` | `Utils/CustomTableView.java` | Never imported in Java. Not referenced in any XML layout. Completely dead. |
| 14 | `GeminiApiClient.java` | `Utils/GeminiApiClient.java` | Never imported or instantiated. AI chat uses direct API calls in `AIFragment` instead. |

### Borderline / Effectively Dead (3 additional files)

| # | File | Path | Reason |
|---|------|------|--------|
| — | `MarkdownChunker.java` | `Utils/MarkdownChunker.java` | Never imported or referenced anywhere. |
| — | `AuthenticatedRequest.java` | `Utils/AuthenticatedRequest.java` | Never imported or referenced anywhere. |
| — | `ToolsFragment.java` | `Activities/ToolsFragment.java` | Complete fragment with real code but never instantiated by any other class (see Section 3). |

---

## 3. Unused Activities & Fragments

### Unused Fragments

| # | Fragment | File | Reason |
|---|----------|------|--------|
| 1 | `ToolsFragment` | `Activities/ToolsFragment.java` | **Never loaded by any Activity.** The bottom nav's "Vitals" tab (`navigation_tools`) maps to `HealthDataFragment`, NOT `ToolsFragment`. Appears to be a legacy fragment that was replaced. Contains podcast player and a button launching `MedicalReportsActivity`. |
| 2 | `MedicalReportsDialogFragment` | `Activities/MedicalReportsDialogFragment.java` | **Never shown (`.show()` never called).** Complete DialogFragment implementation for medical report uploads, but `MedicalReportsActivity` is used for this flow instead. Parallel duplicate implementation. |

### Effectively Unreachable Activity

| # | Activity | File | Reason |
|---|----------|------|--------|
| 1 | `MedicalReportsActivity` | `Activities/MedicalReportsActivity.java` | **Declared in AndroidManifest** but the **only navigation to it** is from `ToolsFragment` (line 146), which is itself dead code. Technically reachable via deep link (`exported="true"`), but unreachable through normal app navigation. |

### Empty Stub Activities (Also in Section 2)

| # | Activity | Status |
|---|----------|--------|
| 1 | `EditProfileActivity` | Empty stub, not in manifest, zero references |
| 2 | `CustomPodcastActivity` | Empty stub, not in manifest, zero references |

---

## 4. Unused Methods/Functions Within Used Files

### 4.1 AIFragment.java — 6 Dead Methods

| # | Method | Signature | Status | Notes |
|---|--------|-----------|--------|-------|
| 1 | `showModelDropdown` | `private void showModelDropdown()` | Root of dead chain | Legacy model selection. Superseded by `showModelSelectionDropdown()`. |
| 2 | `createSessionOnServer` | `private void createSessionOnServer(String title)` | Dead chain | Only called from `showModelDropdown()` which is dead. |
| 3 | `createSessionOnServerWithModel` | `private void createSessionOnServerWithModel(String title, String modelType)` | Isolated dead | Copy-paste variant, never wired in. |
| 4 | `createOrGetSession` | `private void createOrGetSession()` | Root of dead chain | Original session-init flow, replaced by `initializeChatSession()` + `createSessionThenSend()`. |
| 5 | `syncMessagesFromBackend` | `private void syncMessagesFromBackend(String sessionId)` | Dead chain | Only called from `createOrGetSession()` which is dead. |
| 6 | `loadChatHistory` | `private void loadChatHistory()` | Dead chain | Only called from dead methods `createOrGetSession()` and `syncMessagesFromBackend()`. |

### 4.2 ProfileFragment.java — 4 Dead Methods

| # | Method | Signature | Status | Notes |
|---|--------|-----------|--------|-------|
| 7 | `setupFamilyMedicalRecordsSection` | `private void setupFamilyMedicalRecordsSection(View rootView)` | Root of dead chain | Never called from `onCreateView()`. HealthDataFragment has its own working copy. |
| 8 | `showAddFamilyMemberDialog` | `private void showAddFamilyMemberDialog()` | Dead chain | Only called from dead `setupFamilyMedicalRecordsSection()`. |
| 9 | `sendFamilyRelationshipRequest` | `private void sendFamilyRelationshipRequest(String email, String relationship, SimpleProgress progress)` | Dead chain | Only called from dead `showAddFamilyMemberDialog()`. |
| 10 | `fetchFamilyRelationships` | `private void fetchFamilyRelationships()` | Dead chain | Only called from dead methods. |

### 4.3 DatabaseHelper.java — 9 Dead Methods

| # | Method | Signature | Status | Notes |
|---|--------|-----------|--------|-------|
| 11 | `getContactsForUser` | `public List<...> getContactsForUser(long userId)` | Never called | `saveContacts()` is used, but this read counterpart has no consumers. |
| 12 | `deleteAllChatsForUser` | `public void deleteAllChatsForUser(long userId)` | Never called | Utility method written but never invoked. |
| 13 | `deleteAllChats` | `public void deleteAllChats()` | Never called | Utility method written but never invoked. |
| 14 | `deleteMedicalReport` | `public void deleteMedicalReport(long reportId)` | Callers commented out | All call sites commented out in `MedicalReportsActivity.java` (line ~699). |
| 15 | `updateMedicalReportAnalysis` | `public void updateMedicalReportAnalysis(long reportId, String aiAnalysis)` | Callers commented out | All call sites commented out in `MedicalReportsActivity.java` (line ~678). |
| 16 | `getMedicalDataCount` | `public int getMedicalDataCount(long userId)` | Never called | Utility method written but never invoked. |
| 17 | `createChatSession` | `public long createChatSession(ChatSession session)` | Dead chain | Only called from `AIFragment.createOrGetSession()` which is dead. |
| 18 | `saveMessage` | `public long saveMessage(String sessionId, ChatMessage message)` | Dead chain | Only external call from `AIFragment.syncMessagesFromBackend()` which is dead. |
| 19 | `getSessionMessages` | `public List<ChatMessage> getSessionMessages(String sessionId)` | Dead chain | Only called from `AIFragment.loadChatHistory()` which is dead. |

### 4.4 Utilities.java — 5 Dead Items

| # | Method/Item | Signature | Status | Notes |
|---|-------------|-----------|--------|-------|
| 20 | `createNotificationChannel` | `private void createNotificationChannel(Context context)` | Never called | Notification channel is never created. |
| 21 | `checkInternetWithDialog` | `public void checkInternetWithDialog(Context context, View view, InternetCheckCallback callback)` | Never called | `checkAndShowInternetStatus()` is used instead. |
| 22 | `InternetCheckCallback` | `public interface InternetCheckCallback` | Never used | Only parameter type for unused `checkInternetWithDialog()`. |
| 23 | `hasInternetConnection` | `public boolean hasInternetConnection(Context context)` | Never called externally | Public wrapper for `isInternetAvailable()` that was never adopted. |
| 24 | `showNoInternetOptions` | `private void showNoInternetOptions(Context context, View view, String connectionType)` | Dead chain | Only called from unused `checkInternetWithDialog()`. |

---

## 5. Unused Layout XML Files

**19 of 89 layout files (21%) are never inflated or referenced.**

| # | File | Notes |
|---|------|-------|
| 1 | `activity_medical_data.xml` | No `R.layout.activity_medical_data` found anywhere. No Activity uses this layout. |
| 2 | `fragment_progress.xml` | No `ProgressFragment` class exists. Layout is orphaned. |
| 3 | `layout.xml` | Generic name, zero references in Java or XML. |
| 4 | `dialog_add_exercise.xml` | Never inflated by any Java class. |
| 5 | `dialog_create_session.xml` | Never inflated. Session creation uses inline dialog in `AIFragment`. |
| 6 | `dialog_dropdown_field.xml` | Never inflated by any Java class. |
| 7 | `dialog_edit_exercise.xml` | Never inflated by any Java class. |
| 8 | `dialog_exercise_details.xml` | Never inflated by any Java class. |
| 9 | `dialog_progress.xml` | Never inflated. `SimpleProgress` utility is used instead. |
| 10 | `dialog_text_field.xml` | Never inflated by any Java class. |
| 11 | `dialog_workout_exercises.xml` | Never inflated by any Java class. |
| 12 | `exercise_list_recycler.xml` | Never inflated by any Java class. |
| 13 | `signup_step_health_metrics.xml` | Never inflated. Signup uses `signup_page_*.xml` files. |
| 14 | `spinner_dropdown_item.xml` | Never used as a spinner layout. |
| 15 | `item_activity_card.xml` | Never used by any adapter. |
| 16 | `item_date_divider.xml` | Never used by any adapter. |
| 17 | `item_medical_report.xml` | Never used. (`item_medical_data.xml` IS used instead.) |
| 18 | `item_relationship_request.xml` | Never used by any adapter. |
| 19 | `item_workout_exercises.xml` | Never used. (`item_exercise_in_workout.xml` IS used instead.) |

---

## 6. Unused Drawable XML Files

**32 of 108 drawable files (30%) are never referenced.**

### Unused Background/Shape Drawables (11)

| # | File |
|---|------|
| 1 | `analyze_button_background.xml` |
| 2 | `bg_rounded_card.xml` |
| 3 | `bg_rounded_dialog.xml` |
| 4 | `bg_subscription_option.xml` |
| 5 | `button_filled_background.xml` |
| 6 | `button_outline_red.xml` |
| 7 | `button_primary.xml` |
| 8 | `chip_background.xml` |
| 9 | `dialog_background.xml` |
| 10 | `normal_podcast_background.xml` |
| 11 | `pro_podcast_background.xml` |

### Unused Icon Drawables (17)

| # | File |
|---|------|
| 12 | `ic_ai_chat_advanced.xml` |
| 13 | `ic_ai_model.xml` |
| 14 | `ic_collapse.xml` |
| 15 | `ic_crown.xml` |
| 16 | `ic_excel.xml` |
| 17 | `ic_expand.xml` |
| 18 | `ic_food_danger.xml` |
| 19 | `ic_image.xml` |
| 20 | `ic_incognito.xml` |
| 21 | `ic_loading.xml` |
| 22 | `ic_logout.xml` |
| 23 | `ic_medication.xml` |
| 24 | `ic_menu.xml` |
| 25 | `ic_pdf.xml` |
| 26 | `ic_progress.xml` |
| 27 | `ic_workout.xml` |
| 28 | `progress_ring.xml` |

### Unused Launcher/Misc Drawables (4)

| # | File | Notes |
|---|------|-------|
| 29 | `ic_launcher_background.xml` | App uses `ic_launcher.png` directly |
| 30 | `ic_launcher_foreground.xml` (drawable-v24) | Adaptive icon component never used |
| 31 | `spinner_background.xml` | Zero references |
| 32 | `rounded_button.xml` | Zero references |

---

## 7. Unused Animation Files

**1 of 5 animation files is unused.**

| # | File | Status |
|---|------|--------|
| 1 | `card_animation.xml` | **UNUSED** — zero references in Java or XML |
| — | `slide_in_right.xml` | Used in `DialogAnimationSlideRight` style |
| — | `slide_out_right.xml` | Used in `DialogAnimationSlideRight` style |
| — | `layout_animation_slide_bottom.xml` | Used in 4+ Java files |
| — | `item_animation_slide_bottom.xml` | Used via `layout_animation_slide_bottom.xml` |

---

## 8. Unused Values Resources (Colors, Styles, Themes, IDs)

### 8.1 Unused Colors (from `res/values/colors.xml`)

| Color Name | Status |
|------------|--------|
| `purple_200` (#FFBB86FC) | **UNUSED** — Android Studio default, never referenced |
| `purple_500` (#FF6200EE) | **UNUSED** — Android Studio default, never referenced |
| `purple_700` (#FF3700B3) | **UNUSED** — Android Studio default, never referenced |

### 8.2 Unused Styles (from `res/values/styles.xml`)

| # | Style | Notes |
|---|-------|-------|
| 1 | `CustomTextInputLayoutLabel` | Zero references from Java or any layout XML |
| 2 | `CustomDialogStyle` | Zero references from Java or any layout XML |
| 3 | `CustomBottomSheetDialogTheme` | Zero references from Java or any layout XML |
| 4 | `CustomShapeAppearanceOverlay` | Only referenced from unused `CustomDialogStyle` |
| 5 | `CustomDialogBodyStyle` | Only referenced from unused `CustomDialogStyle` |
| 6 | `CustomDialogTitleStyle` | Only referenced from unused `CustomDialogStyle` |
| 7 | `CustomBottomSheetStyle` | Only referenced from unused `CustomBottomSheetDialogTheme` |
| 8 | `CustomShapeAppearanceBottomSheetDialog` | Only referenced from unused `CustomBottomSheetStyle` |

### 8.3 Unused Themes

| Theme | File | Status |
|-------|------|--------|
| `Theme.RichHealth` | `res/values/themes.xml` | **UNUSED** — Manifest uses `@style/AppTheme` from styles.xml, NOT this theme |
| `Widget.App.BottomNavigationView` | `res/values/themes.xml` | **UNUSED** — Only referenced from unused `Theme.RichHealth` |
| Night variant of `Theme.RichHealth` | `res/values-night/themes.xml` | **UNUSED** — Entire file is dead since `Theme.RichHealth` is never applied |

### 8.4 Unused IDs (from `res/values/ids.xml.xml`)

| ID | Status |
|----|--------|
| `navigation_workouts` | **UNUSED** — zero references |
| `navigation_exercises` | **UNUSED** — zero references |
| `navigation_progress` | **UNUSED** — zero references |

> Note: The file itself has a double extension: `ids.xml.xml` — likely a naming error.

---

## 9. Unused Raw Resources

| # | File | Status |
|---|------|--------|
| 1 | `gym_exercises.json` | **UNUSED** — zero references. (`exercises.json` IS used.) |
| 2 | `sample_podcast.mp3` | **UNUSED** — zero references. Other MP3s (aging, cold, vit_d) ARE used. |

---

## 10. Manifest Issues

### Activities Declared But Effectively Unreachable

| Activity | Issue |
|----------|-------|
| `MedicalReportsActivity` | Declared with `exported="true"` but only navigation path is from dead `ToolsFragment`. Reachable only via deep link. |
| `AddWorkout` | Declared with `exported="true"` — should be `false` since it's only launched internally from `WorkoutsFragment`. Security concern. |

### Activities NOT Declared (But Files Exist)

| Activity | Issue |
|----------|-------|
| `EditProfileActivity` | File exists but empty stub, not in manifest |
| `CustomPodcastActivity` | File exists but empty stub, not in manifest |

### Broken Resource Reference

| File | Issue |
|------|-------|
| `activity_signup.xml` (line 127) | References `@mipmap/ic_launcher_round` but **no `res/mipmap/` directory exists** in the project. Should be `@drawable/ic_launcher_round` or a proper mipmap directory should be created. |

---

## 11. Dead Navigation Chains

### Chain 1: ToolsFragment → MedicalReportsActivity (Entire podcast/reports flow)

```
ToolsFragment [DEAD - never loaded]
├── MedicalReportsActivity [DEAD - only called from ToolsFragment]
│   └── Uses: UploadedFilesAdapter, MedicalReportApiService, ProUpgradeDialog
└── Podcast player code [DEAD - entire feature in ToolsFragment is unreachable]
```

**Impact:** The `MedicalReportsActivity` and the podcast player inside `ToolsFragment` are completely unreachable at runtime.

### Chain 2: AIFragment Legacy Session Flow

```
AIFragment.createOrGetSession() [DEAD - never called]
├── DatabaseHelper.createChatSession()
├── AIFragment.syncMessagesFromBackend()
│   ├── DatabaseHelper.saveMessage()
│   └── AIFragment.loadChatHistory()
│       └── DatabaseHelper.getSessionMessages()
└── AIFragment.loadChatHistory()

AIFragment.showModelDropdown() [DEAD - never called]
└── AIFragment.createSessionOnServer()

AIFragment.createSessionOnServerWithModel() [DEAD - isolated]
```

### Chain 3: ProfileFragment Family Records

```
ProfileFragment.setupFamilyMedicalRecordsSection() [DEAD - never called]
├── ProfileFragment.showAddFamilyMemberDialog()
│   └── ProfileFragment.sendFamilyRelationshipRequest()
│       └── ProfileFragment.fetchFamilyRelationships()
└── ProfileFragment.fetchFamilyRelationships()
```

**Note:** This entire feature was duplicated in `HealthDataFragment` where it IS called. The `ProfileFragment` copy was never cleaned up.

---

## 12. Full Navigation Flow (Reference)

This shows the LIVE navigation flow (what the user can actually reach):

```
APP LAUNCH
└── SplashActivity
    ├── [not logged in] → LoginActivity
    │   ├── [signup link] → SignupActivity
    │   │   └── [success] → LoginActivity
    │   └── [login success + terms] → MainActivity
    ├── [logged in, terms accepted] → MainActivity
    └── [logged in, terms NOT accepted] → TermsAndConditionsDialog
        ├── [accept] → MainActivity
        └── [decline] → LoginActivity

MainActivity (bottom navigation)
├── Home tab → HomeFragment
│   ├── "Start Workout" card → WorkoutsFragment
│   │   └── "+" button → AddWorkout (Activity)
│   ├── "Browse Exercises" card → ExercisesFragment
│   └── "Link Doctor" button → DoctorSearchActivity
│       ├── Tab 0 → FindDoctorFragment
│       ├── Tab 1 → ConnectedDoctorsFragment
│       └── Tab 2 → PendingRequestsFragment
├── Vitals tab → HealthDataFragment
├── Richie tab → AIFragment
└── Profile tab → ProfileFragment
    └── Logout → LoginActivity

UNREACHABLE (dead code):
├── ToolsFragment (was likely old "Vitals" tab, replaced by HealthDataFragment)
├── MedicalReportsActivity (only called from dead ToolsFragment)
├── MedicalReportsDialogFragment (parallel implementation, never shown)
├── EditProfileActivity (empty stub)
└── CustomPodcastActivity (empty stub)
```

---

## 13. Summary Statistics

### Total Unused Items: 109

| Category | Count |
|----------|-------|
| Entire Java files (deletable) | 17 (14 confirmed + 3 borderline) |
| Dead methods in used files | 22 |
| Unused layout XMLs | 19 |
| Unused drawable XMLs | 32 |
| Unused animation XMLs | 1 |
| Unused color definitions | 3 |
| Unused style definitions | 8 |
| Unused theme definitions | 2 |
| Unused ID definitions | 3 |
| Unused raw resources | 2 |

### Files Safe to Delete (17 Java files)

```
app/src/main/java/com/example/richhealth/Activities/EditProfileActivity.java
app/src/main/java/com/example/richhealth/Activities/CustomPodcastActivity.java
app/src/main/java/com/example/richhealth/Activities/ToolsFragment.java
app/src/main/java/com/example/richhealth/Activities/MedicalReportsDialogFragment.java
app/src/main/java/Adapters/ChatSessionAdapter.java
app/src/main/java/Adapters/PendingRequestAdapter.java
app/src/main/java/Adapters/ConnectedDoctorAdapter.java
app/src/main/java/Adapters/DoctorAdapter.java
app/src/main/java/Adapters/WorkoutTypeAdapter.java
app/src/main/java/Models/Symptom.java
app/src/main/java/Models/SignupPagerAdapter.java
app/src/main/java/Api/ChatApiService.java
app/src/main/java/Api/PodcastApiService.java
app/src/main/java/Api/WorkoutApiService.java
app/src/main/java/Utils/CustomTableView.java
app/src/main/java/Utils/GeminiApiClient.java
app/src/main/java/Utils/MarkdownChunker.java
app/src/main/java/Utils/AuthenticatedRequest.java
```

### Layout XMLs Safe to Delete (19 files)

```
res/layout/activity_medical_data.xml
res/layout/fragment_progress.xml
res/layout/layout.xml
res/layout/dialog_add_exercise.xml
res/layout/dialog_create_session.xml
res/layout/dialog_dropdown_field.xml
res/layout/dialog_edit_exercise.xml
res/layout/dialog_exercise_details.xml
res/layout/dialog_progress.xml
res/layout/dialog_text_field.xml
res/layout/dialog_workout_exercises.xml
res/layout/exercise_list_recycler.xml
res/layout/signup_step_health_metrics.xml
res/layout/spinner_dropdown_item.xml
res/layout/item_activity_card.xml
res/layout/item_date_divider.xml
res/layout/item_medical_report.xml
res/layout/item_relationship_request.xml
res/layout/item_workout_exercises.xml
```

### Drawable XMLs Safe to Delete (32 files)

```
res/drawable/analyze_button_background.xml
res/drawable/bg_rounded_card.xml
res/drawable/bg_rounded_dialog.xml
res/drawable/bg_subscription_option.xml
res/drawable/button_filled_background.xml
res/drawable/button_outline_red.xml
res/drawable/button_primary.xml
res/drawable/chip_background.xml
res/drawable/dialog_background.xml
res/drawable/normal_podcast_background.xml
res/drawable/pro_podcast_background.xml
res/drawable/ic_ai_chat_advanced.xml
res/drawable/ic_ai_model.xml
res/drawable/ic_collapse.xml
res/drawable/ic_crown.xml
res/drawable/ic_excel.xml
res/drawable/ic_expand.xml
res/drawable/ic_food_danger.xml
res/drawable/ic_image.xml
res/drawable/ic_incognito.xml
res/drawable/ic_loading.xml
res/drawable/ic_logout.xml
res/drawable/ic_medication.xml
res/drawable/ic_menu.xml
res/drawable/ic_pdf.xml
res/drawable/ic_progress.xml
res/drawable/ic_workout.xml
res/drawable/progress_ring.xml
res/drawable/ic_launcher_background.xml
res/drawable/spinner_background.xml
res/drawable/rounded_button.xml
res/drawable-v24/ic_launcher_foreground.xml
```

### Raw Resources Safe to Delete (2 files)

```
res/raw/gym_exercises.json
res/raw/sample_podcast.mp3
```

---

> **Note:** Before deleting `MedicalReportsActivity`, `ToolsFragment`, and their associated resources, verify whether the podcast and medical reports features are planned for future use. If so, they need to be wired into the live navigation flow (e.g., accessible from `HomeFragment` or `HealthDataFragment`). Currently they are unreachable dead code.
