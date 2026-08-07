# Backend Changes Prompt for RichHealth Express

Apply all the following changes to the RichHealth Express backend. Each change is numbered and independent unless noted. Keep LLM calls as separate functions following the same middleware pattern (auth + callForJSON). Read the full codebase before making changes to avoid breaking anything.

---

## 1. Fix Usage Tracker — Monthly Reset (usageTracker.js)

The usage tracker currently increments `count` forever with no reset. Fix it to reset monthly.

**In `utils/usageTracker.js`:**

- In `checkUsageLimit`: Before returning, check if the feature's `lastUsed` date is from a previous month. If so, reset `count` to 0 and save.
- In `incrementUsage`: Same — before incrementing, check if `lastUsed` is from a previous month. If so, reset `count` to 0 before incrementing.
- In `getUsageStatus` and `getAllUsageStatus`: Same check.

Add a helper:
```javascript
function shouldResetUsage(lastUsed) {
  if (!lastUsed) return false;
  const now = new Date();
  const last = new Date(lastUsed);
  return now.getMonth() !== last.getMonth() || now.getFullYear() !== last.getFullYear();
}
```

Use it in all 4 exported functions: if `shouldResetUsage(user.usage[feature].lastUsed)` is true, set `user.usage[feature].count = 0` and save.

**Also update the comment** in `getLimitForFeature`: change "Free: 1/week" to "Free: 1/month".

---

## 2. Enforce NutriCheck Usage Limits (homeScreenController.js)

The `nutriCheck` endpoint doesn't check usage limits at all.

**In `controllers/homeScreenController.js`, `nutriCheck` function:**

Add at the top (after getting userId):
```javascript
const { checkUsageLimit, incrementUsage } = require('../utils/usageTracker');
const usageCheck = await checkUsageLimit(userId, 'nutricheck');
if (usageCheck.limitReached) {
  return res.status(429).json({
    success: false,
    message: 'NutriCheck limit reached. Upgrade to Pro for unlimited access.',
    usage: usageCheck
  });
}
```

And after successful response, add:
```javascript
await incrementUsage(userId, 'nutricheck');
```

---

## 3. Fix buildHealthContext to Include Both Symptom Collections (services/ai.js)

`buildHealthContext` only queries `MedicalData` type="symptom". The app also has a separate `Symptom` model.

**In `services/ai.js`, `buildHealthContext` function:**

Add `const Symptom = require("../models/Symptom");` at the top of the function.

In the `Promise.all`, add a 4th query:
```javascript
Symptom.find({
  user: dependentId ? undefined : user._id, // Only for main user
  isDeleted: { $ne: true }
}).sort({ date: -1 }).limit(20)
```

Then merge the Symptom results into the health context string. After building the "Recent Health Issues" section from MedicalData symptoms, append:
```
Additional Symptom Records:
```
And list each Symptom with: name, severity, date, notes.

This ensures the AI chat and all analysis endpoints see the complete symptom picture.

---

## 4. Remove overallHealthAnalysis Field & Update Doctor Portal (User.js, healthAnalysisController.js, doctorPortalController.js)

The `overallHealthAnalysis` field on User model is NEVER written to by any code, making it a dead field. The `generateHealthAnalysis` endpoint writes to `healthAnalysisCache.overall` instead.

**Changes:**

### a) `controllers/healthAnalysisController.js` → `getHealthAnalysis`:
Find where it reads `overallHealthAnalysis` as a headline fallback:
```javascript
headline = user.overallHealthAnalysis || headline;
```
Replace with:
```javascript
if (user.healthAnalysisCache?.overall?.text) {
  try {
    const parsed = JSON.parse(user.healthAnalysisCache.overall.text);
    headline = parsed.headline || headline;
  } catch(e) {}
}
```

### b) `controllers/doctorPortalController.js` → `getPatientDetails`:
Find where it reads `overallHealthAnalysis`. Replace with reading from `healthAnalysisCache.overall.text`:
```javascript
let overallAnalysis = '';
if (patient.healthAnalysisCache?.overall?.text) {
  try {
    const parsed = JSON.parse(patient.healthAnalysisCache.overall.text);
    overallAnalysis = parsed.summary || '';
  } catch(e) {}
}
```

### c) `models/User.js`:
Remove the `overallHealthAnalysis: String` field from the schema entirely.

---

## 5. Remove /refresh-headline Endpoint (healthAnalysisController.js, routes)

The `refreshAnalysisHeadline` endpoint generates random fake headlines. It's dead code — the Android app no longer calls it.

**Delete:**
- The entire `refreshAnalysisHeadline` function from `controllers/healthAnalysisController.js`
- Its route from the routes file (likely `POST /api/health/analysis/refresh-headline`)

---

## 6. Cache Health Screening Results (homeScreenController.js)

`getHealthScreening` makes an LLM call every time with no caching.

**In `controllers/homeScreenController.js`, `getHealthScreening`:**

Before calling the LLM, check if cached:
```javascript
if (user.healthScreeningCache && user.healthScreeningCache.text) {
  const cachedAt = user.healthScreeningCache.cachedAt;
  const hoursSince = (Date.now() - new Date(cachedAt).getTime()) / (1000 * 60 * 60);
  if (hoursSince < 24) {
    return res.json({ success: true, ...JSON.parse(user.healthScreeningCache.text), cached: true });
  }
}
```

After successful LLM response:
```javascript
user.healthScreeningCache = { text: JSON.stringify(result), cachedAt: new Date() };
await user.save();
```

**In `models/User.js`**, add to schema:
```javascript
healthScreeningCache: {
  text: String,
  cachedAt: Date
},
```

---

## 7. Add Genetics/Family Analysis (prompts.js, healthAnalysisController.js, User.js)

Add a new "genetics" analysis type that analyzes family health data.

### a) `utils/prompts.js` — Add new prompt:
```javascript
GENETICS_ANALYSIS_PROMPT: (familyData, profile) => `
You are a health genetics counselor AI. Analyze the family health data and identify hereditary risk patterns.

Family Members and Their Health Data:
${familyData}

User Profile:
${profile}

Return JSON:
{
  "summary": "Brief overview of family health patterns",
  "hereditaryRisks": ["risk1", "risk2"],
  "patterns": ["pattern1", "pattern2"],
  "recommendations": ["recommendation1", "recommendation2"],
  "newMemberAlert": "Note if any new family members were added since last analysis, or empty string"
}

If no family data is available, return:
{ "noData": true, "message": "Link family members to get genetics analysis" }
`,
```

### b) `controllers/healthAnalysisController.js` → `generateHealthAnalysis`:

After fetching symptoms/medications/measurements/reports, also fetch family data:
```javascript
const relationships = await Relationship.find({
  $or: [{ user: user._id }, { relatedUser: user._id }],
  status: 'accepted'
}).populate('user relatedUser');

// For each related user, fetch their health data summary
let familyDataStr = '';
for (const rel of relationships) {
  const relatedUser = rel.user._id.equals(user._id) ? rel.relatedUser : rel.user;
  const relMedData = await MedicalData.find({ user: relatedUser._id, isDeleted: false }).limit(10);
  familyDataStr += `\n${rel.relationship} (${relatedUser.name}):\n`;
  familyDataStr += relMedData.map(d => `- ${d.type}: ${d.title || d.description}`).join('\n');
}
```

Add genetics to the parallel LLM calls:
```javascript
const geneticsPromise = familyDataStr
  ? callForJSON(PROMPT_CONSTANTS.GENETICS_ANALYSIS_PROMPT(familyDataStr, profileStr))
  : Promise.resolve({ noData: true, message: "Link family members to get genetics analysis" });
```

Store in cache:
```javascript
user.healthAnalysisCache.genetics = {
  text: JSON.stringify(geneticsResult),
  generatedAt: new Date(),
  dataCount: relationships.length
};
```

### c) `models/User.js` — Add to `healthAnalysisCache`:
```javascript
genetics: { text: String, generatedAt: Date, dataCount: { type: Number, default: 0 } },
```

### d) Add to `dataChangesSinceAnalysis`:
```javascript
genetics: { type: Number, default: 0 },
```

### e) In `utils/logUtils.js`, update `markHealthDataUpdate` to also increment genetics counter when family/relationship data changes.

---

## 8. Add AQI Analysis Summary to Store Response (aqiController.js)

The Android app now shows AQI records count and a short analysis summary in the health dialog.

**In `controllers/aqiController.js`, `storeAqiData`:**

After storing/updating the AQI record, also return the user's total record count and a brief analysis:
```javascript
const totalRecords = await AQIData.countDocuments({ user: req.user._id || req.user.id });

// Simple analysis based on current AQI
let analysisSummary = '';
if (aqius <= 50) analysisSummary = 'Air quality is good. Safe for outdoor activities.';
else if (aqius <= 100) analysisSummary = 'Moderate air quality. Sensitive individuals should limit prolonged outdoor exertion.';
else if (aqius <= 150) analysisSummary = 'Unhealthy for sensitive groups. Consider reducing outdoor activities.';
else if (aqius <= 200) analysisSummary = 'Unhealthy. Everyone may begin to experience health effects.';
else analysisSummary = 'Very unhealthy. Avoid prolonged outdoor activities.';

res.status(200).json({
  success: true,
  message: existingRecord ? 'AQI data updated' : 'AQI data stored',
  recordCount: totalRecords,
  analysisSummary: analysisSummary
});
```

---

## 9. Add Health Score to Overall Analysis Prompt (prompts.js)

The Android app now displays `healthScore` from the overall analysis.

**In `utils/prompts.js`, `OVERALL_HEALTH_ANALYSIS_PROMPT`:**

The prompt already asks for a `score` field (0-100). Rename it to `healthScore` in the prompt to be explicit:
```
{
  "headline": "max 60 chars summary",
  "summary": "2-3 sentence overall assessment",
  "topConcerns": ["concern1", "concern2"],
  "recommendations": ["rec1", "rec2"],
  "healthScore": 72
}
```

If the prompt currently uses `score`, update the field name to `healthScore` in the prompt instructions.

**In `controllers/healthAnalysisController.js`, `generateHealthAnalysis`:**
When extracting from overall result, the Android reads `overallParsed.optInt("healthScore", -1)`. No changes needed here if the prompt output field is `healthScore`.

---

## 10. Return Usage Info from Health Analysis Endpoints

**In `controllers/healthAnalysisController.js`, `getHealthAnalysis`:**

After building the response, also include the user's usage status so the Android app can display accurate usage badges:
```javascript
const { getUsageStatus } = require('../utils/usageTracker');
const analysisUsage = await getUsageStatus(userId, 'healthAnalysis');
```

Add to the response object:
```javascript
usageStatus: analysisUsage
```

---

## 11. Remove Data Insights as Standalone Section

The `/api/users/analysis` endpoint returns `dataInsights` (BMI category, BP category, etc.). These are no longer shown as a standalone section.

No backend deletion needed — keep the endpoint for backward compatibility. The Android app simply no longer displays the standalone section.

---

## 12. Add Relationship model import where needed

In `healthAnalysisController.js`, you'll need to import the Relationship model for the genetics feature:
```javascript
const Relationship = require('../models/Relationship');
```

Check what model name is used in the codebase for user relationships (might be `UserRelationship` or `Relationship`) and import accordingly.

---

## Summary of Files to Modify:

| File | Changes |
|------|---------|
| `utils/usageTracker.js` | Add monthly reset logic |
| `controllers/homeScreenController.js` | Add nutriCheck usage check, cache health screening |
| `services/ai.js` | Add Symptom model query to buildHealthContext |
| `models/User.js` | Remove `overallHealthAnalysis`, add `healthScreeningCache`, add `genetics` to cache/counters |
| `controllers/healthAnalysisController.js` | Remove refreshAnalysisHeadline, add genetics analysis, fix overallHealthAnalysis reads, add usage status |
| `controllers/doctorPortalController.js` | Fix overallHealthAnalysis reads |
| `controllers/aqiController.js` | Add recordCount + analysisSummary to store response |
| `utils/prompts.js` | Add GENETICS_ANALYSIS_PROMPT, rename score→healthScore in overall prompt |
| `utils/logUtils.js` | Add genetics counter to markHealthDataUpdate |
| Routes file | Remove refresh-headline route |

## Order of Changes (recommended):
1. User.js schema changes (add fields, remove field)
2. usageTracker.js (monthly reset)
3. prompts.js (genetics prompt, healthScore rename)
4. services/ai.js (buildHealthContext fix)
5. healthAnalysisController.js (remove headline, add genetics, fix reads, add usage)
6. homeScreenController.js (nutriCheck limits, screening cache)
7. aqiController.js (store response enhancement)
8. doctorPortalController.js (fix overallHealthAnalysis read)
9. logUtils.js (genetics counter)
10. Routes (remove refresh-headline)
