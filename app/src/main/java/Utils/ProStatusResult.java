package Utils;

/**
 * Container class for Pro status data returned from the API
 *//**
 * Container class for Pro status data returned from the API
 */
public class ProStatusResult {
    private boolean isPro;
    private long expiryDate;
    private String plan;
    private String transactionId;
    private long upgradeDate;
    private String paymentUrl;
    private double amount;

    // New fields for subscription plans
    private int planType;
    private int totalReports;
    private int reportsUsed;
    private int reportsRemaining;
    private boolean isSubscriptionActive;
    private long startDate;
    private long endDate;
    private int familyMembersCount;
    private int maxFamilyMembers;

    // For family subscription members
    private String ownerId;
    private String ownerName;
    private String ownerEmail;
    private int personalReportsUsed;

    // Family plan fields
    private boolean isFamilyPlanOwner;
    private boolean isGrantedPro;
    private String proGrantedBy;
    private java.util.List<String> familyProMembers = new java.util.ArrayList<>();

    // RazorPay fields
    private String razorpayOrderId;
    private String razorpayKeyId;
    private String razorpayPaymentId;
    private String razorpaySignature;

    public ProStatusResult() {
        this.isPro = false;
        this.expiryDate = 0;
        this.plan = "";
        this.transactionId = "";
        this.upgradeDate = 0;
        this.paymentUrl = "";
        this.amount = 0.0;
        this.planType = 0;
        this.totalReports = 0;
        this.reportsUsed = 0;
        this.reportsRemaining = 0;
        this.isSubscriptionActive = false;
        this.startDate = 0;
        this.endDate = 0;
        this.familyMembersCount = 0;
        this.maxFamilyMembers = 0;
        this.ownerId = "";
        this.ownerName = "";
        this.ownerEmail = "";
        this.personalReportsUsed = 0;
        this.isFamilyPlanOwner = false;
        this.isGrantedPro = false;
        this.proGrantedBy = null;
        this.familyProMembers = new java.util.ArrayList<>();
        this.razorpayOrderId = "";
        this.razorpayKeyId = "";
        this.razorpayPaymentId = "";
        this.razorpaySignature = "";
    }

    public boolean isPro() {
        return isPro;
    }

    public void setPro(boolean pro) {
        isPro = pro;
    }

    public long getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(long expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public long getUpgradeDate() {
        return upgradeDate;
    }

    public void setUpgradeDate(long upgradeDate) {
        this.upgradeDate = upgradeDate;
    }

    public String getPaymentUrl() {
        return paymentUrl;
    }

    public void setPaymentUrl(String paymentUrl) {
        this.paymentUrl = paymentUrl;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    // New getters and setters

    public int getPlanType() {
        return planType;
    }

    public void setPlanType(int planType) {
        this.planType = planType;
    }

    public int getTotalReports() {
        return totalReports;
    }

    public void setTotalReports(int totalReports) {
        this.totalReports = totalReports;
    }

    public int getReportsUsed() {
        return reportsUsed;
    }

    public void setReportsUsed(int reportsUsed) {
        this.reportsUsed = reportsUsed;
    }

    public int getReportsRemaining() {
        return reportsRemaining;
    }

    public void setReportsRemaining(int reportsRemaining) {
        this.reportsRemaining = reportsRemaining;
    }

    public boolean isSubscriptionActive() {
        return isSubscriptionActive;
    }

    public void setSubscriptionActive(boolean subscriptionActive) {
        isSubscriptionActive = subscriptionActive;
    }

    public long getStartDate() {
        return startDate;
    }

    public void setStartDate(long startDate) {
        this.startDate = startDate;
    }

    public long getEndDate() {
        return endDate;
    }

    public void setEndDate(long endDate) {
        this.endDate = endDate;
    }

    public int getFamilyMembersCount() {
        return familyMembersCount;
    }

    public void setFamilyMembersCount(int familyMembersCount) {
        this.familyMembersCount = familyMembersCount;
    }

    public int getMaxFamilyMembers() {
        return maxFamilyMembers;
    }

    public void setMaxFamilyMembers(int maxFamilyMembers) {
        this.maxFamilyMembers = maxFamilyMembers;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public int getPersonalReportsUsed() {
        return personalReportsUsed;
    }

    public void setPersonalReportsUsed(int personalReportsUsed) {
        this.personalReportsUsed = personalReportsUsed;
    }

    public boolean isFamilyPlanOwner() { return isFamilyPlanOwner; }
    public void setFamilyPlanOwner(boolean familyPlanOwner) { isFamilyPlanOwner = familyPlanOwner; }

    public boolean isGrantedPro() { return isGrantedPro; }
    public void setGrantedPro(boolean grantedPro) { isGrantedPro = grantedPro; }

    public String getProGrantedBy() { return proGrantedBy; }
    public void setProGrantedBy(String proGrantedBy) { this.proGrantedBy = proGrantedBy; }

    public java.util.List<String> getFamilyProMembers() { return familyProMembers; }
    public void setFamilyProMembers(java.util.List<String> familyProMembers) { this.familyProMembers = familyProMembers; }

    public int getFamilyProMemberCount() { return familyProMembers != null ? familyProMembers.size() : 0; }

    public String getUserTier() {
        if (!isPro) return "free";
        if ("ultra".equals(plan)) return "ultra";
        if ("family".equals(plan)) return "family";
        if ("family_member".equals(plan)) return "family_member";
        if ("plus".equals(plan)) return "plus";
        return "pro";
    }

    // RazorPay getters and setters
    public String getRazorpayOrderId() {
        return razorpayOrderId;
    }

    public void setRazorpayOrderId(String razorpayOrderId) {
        this.razorpayOrderId = razorpayOrderId;
    }

    public String getRazorpayKeyId() {
        return razorpayKeyId;
    }

    public void setRazorpayKeyId(String razorpayKeyId) {
        this.razorpayKeyId = razorpayKeyId;
    }

    public String getRazorpayPaymentId() {
        return razorpayPaymentId;
    }

    public void setRazorpayPaymentId(String razorpayPaymentId) {
        this.razorpayPaymentId = razorpayPaymentId;
    }

    public String getRazorpaySignature() {
        return razorpaySignature;
    }

    public void setRazorpaySignature(String razorpaySignature) {
        this.razorpaySignature = razorpaySignature;
    }

    /**
     * Get the subscription plan name based on planType
     */
    public String getSubscriptionPlanName() {
        switch (planType) {
            case 1: return "RichHealth Plus";
            case 2: return "RichHealth Pro";
            case 3: return "RichHealth Family";
            default: return "Unknown Plan";
        }
    }

    /**
     * Get subscription details as a string
     */
    public String getSubscriptionDetails() {
        StringBuilder details = new StringBuilder();

        if (planType > 0) {
            details.append(getSubscriptionPlanName()).append("\n");
            details.append("Reports: ").append(reportsUsed).append("/").append(totalReports).append("\n");

            if (planType == 3) {
                details.append("Family Members: ").append(familyMembersCount).append("/").append(maxFamilyMembers);
            }
        } else if (ownerId != null && !ownerId.isEmpty()) {
            details.append("Family Plan Member\n");
            details.append("Owner: ").append(ownerName).append("\n");
            details.append("Personal Reports: ").append(personalReportsUsed);
        }

        return details.toString();
    }
}