package Models;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a purchasable subscription plan fetched from /api/payment/plans.
 * Falls back to hardcoded static data if the network call fails.
 */
public class PlanOption {

    private int planId;           // 1, 2, or 3
    private String tierKey;       // "plus", "pro", "ultra"
    private String name;          // "RichHealth Basic"
    private double price;         // actual charge amount (INR)
    private double originalPrice; // before discount (0 if no discount)
    private int discountPercent;  // 0 if no discount
    private String discountMessage;
    private int durationMonths;
    private boolean isMostPopular;
    private List<String> features;

    public PlanOption() {
        features = new ArrayList<>();
    }

    // ── JSON parsing ─────────────────────────────────────────────────────────

    public static PlanOption fromJson(JSONObject json) {
        PlanOption p = new PlanOption();
        p.planId          = json.optInt("planId", 1);
        p.tierKey         = json.optString("tierKey", "plus");
        p.name            = json.optString("name", "RichHealth Plan");
        p.price           = json.optDouble("price", 0);
        p.originalPrice   = json.optDouble("originalPrice", 0);
        p.discountPercent = json.optInt("discountPercent", 0);
        p.discountMessage = json.optString("discountMessage", "");
        p.durationMonths  = json.optInt("durationMonths", 3);
        p.isMostPopular   = json.optBoolean("isMostPopular", false);

        JSONArray fa = json.optJSONArray("features");
        if (fa != null) {
            for (int i = 0; i < fa.length(); i++) {
                p.features.add(fa.optString(i, ""));
            }
        }
        return p;
    }

    // ── Static fallback ───────────────────────────────────────────────────────
    // Used when /api/payment/plans is unreachable. Keep in sync with config/plans.js.

    public static List<PlanOption> getFallbackPlans() {
        List<PlanOption> list = new ArrayList<>();

        PlanOption basic = new PlanOption();
        basic.planId = 1;
        basic.tierKey = "plus";
        basic.name = "RichHealth Plus";
        basic.price = 999;
        basic.originalPrice = 1249;
        basic.discountPercent = 20;
        basic.discountMessage = "20% off — launch pricing";
        basic.durationMonths = 3;
        basic.isMostPopular = false;
        basic.features.add("5 medical report uploads per month");
        basic.features.add("5 health analyses per month");
        basic.features.add("15 NutriCheck meal analyses per month");
        basic.features.add("Richie AI chat (standard models, 25 messages/session)");
        basic.features.add("Full health & symptom tracking");
        basic.features.add("AQI monitoring");
        basic.features.add("Medication tracking");
        list.add(basic);

        PlanOption pro = new PlanOption();
        pro.planId = 2;
        pro.tierKey = "pro";
        pro.name = "RichHealth Pro";
        pro.price = 2499;
        pro.originalPrice = 3599;
        pro.discountPercent = 30;
        pro.discountMessage = "30% off — most popular";
        pro.durationMonths = 3;
        pro.isMostPopular = true;
        pro.features.add("10 medical report uploads per month");
        pro.features.add("10 health analyses per month");
        pro.features.add("Unlimited NutriCheck meal analyses");
        pro.features.add("Premium AI models (GPT-5.3, Claude 4.5)");
        pro.features.add("Up to 2 dependents (child/family profiles)");
        pro.features.add("Doctor connections");
        pro.features.add("50 messages per chat session");
        list.add(pro);

        PlanOption ultra = new PlanOption();
        ultra.planId = 3;
        ultra.tierKey = "ultra";
        ultra.name = "RichHealth Ultra";
        ultra.price = 4999;
        ultra.originalPrice = 9999;
        ultra.discountPercent = 50;
        ultra.discountMessage = "50% off — best value";
        ultra.durationMonths = 12;
        ultra.isMostPopular = false;
        ultra.features.add("Unlimited AI report analyses");
        ultra.features.add("Up to 5 family members");
        ultra.features.add("All premium AI models");
        ultra.features.add("Doctor AI analysis reviews");
        ultra.features.add("Custom podcast requests");
        ultra.features.add("100 messages per chat session");
        ultra.features.add("Dedicated health insights");
        list.add(ultra);

        return list;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean hasDiscount() {
        return discountPercent > 0 && originalPrice > 0 && originalPrice > price;
    }

    public String getDurationLabel() {
        return durationMonths == 12 ? "Valid for 12 months" : "Valid for " + durationMonths + " months";
    }

    public String getShortSummary() {
        if (features.isEmpty()) return "";
        // First feature makes a good one-liner summary
        return features.get(0);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int getPlanId()          { return planId; }
    public String getTierKey()      { return tierKey; }
    public String getName()         { return name; }
    public double getPrice()        { return price; }
    public double getOriginalPrice(){ return originalPrice; }
    public int getDiscountPercent() { return discountPercent; }
    public String getDiscountMessage() { return discountMessage; }
    public int getDurationMonths()  { return durationMonths; }
    public boolean isMostPopular()  { return isMostPopular; }
    public List<String> getFeatures() { return features; }
}
