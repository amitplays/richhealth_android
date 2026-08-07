package Utils;

import android.content.Context;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

public class UploadedFile {
    private Uri uri;
    private String name;
    private String reportType;
    private String reportId;
    private String status;
    private String analysisSummary;
    private String fileType;
    private String fileUrl;
    private boolean hasAnalysis;
    private String aiAnalysisSummary;
    private String aiAnalysisDetailed;
    private String detailedSummary;
    private String aiOpinion;
    private String riskLevel;
    private String urgency;
    private String reportTypeDetected;
    private String analysisStatus;
    private String statusMessage;
    private List<String> recommendations = new ArrayList<>();
    private List<String> followUpTests = new ArrayList<>();
    private List<String> lifestyleAdvice = new ArrayList<>();
    private List<PossibleCondition> possibleConditions = new ArrayList<>();
    private boolean canAnalyze;
    private boolean shareWithFamily;
    private boolean includeInChat = true;
    private List<KeyFinding> keyFindings = new ArrayList<>();
    // Effective report date (report metadata date, else upload date), epoch millis.
    // Used as the X axis when plotting a test's trend across reports.
    private long reportDateMillis = 0L;
    public Context context;

    public static class KeyFinding {
        private String parameter;
        private String value;
        private String unit;
        private String normalRange;
        private String status;
        // Normalized snake_case test key so the SAME test lines up across reports
        // for trend plotting. Numeric value (NaN when not a number) for the chart.
        private String canonicalKey;
        private double valueNumeric = Double.NaN;

        public KeyFinding(String parameter, String value, String unit, String normalRange, String status) {
            this.parameter = parameter;
            this.value = value;
            this.unit = unit;
            this.normalRange = normalRange;
            this.status = status;
        }

        public KeyFinding(String parameter, String value, String unit, String normalRange, String status,
                          String canonicalKey, double valueNumeric) {
            this(parameter, value, unit, normalRange, status);
            this.canonicalKey = canonicalKey;
            this.valueNumeric = valueNumeric;
        }

        public String getParameter() { return parameter; }
        public String getValue() { return value; }
        public String getUnit() { return unit; }
        public String getNormalRange() { return normalRange; }
        public String getStatus() { return status; }
        public String getCanonicalKey() { return canonicalKey; }
        public double getValueNumeric() { return valueNumeric; }
        public boolean hasNumericValue() { return !Double.isNaN(valueNumeric); }

        public int getStatusColor() {
            if (status == null) return 0xFF757575;
            switch (status.toLowerCase()) {
                case "normal": return 0xFF4CAF50;
                case "high": return 0xFFFF9800;
                case "low": return 0xFF2196F3;
                case "critical": return 0xFFF44336;
                default: return 0xFF757575;
            }
        }
    }

    // Update constructor to accept context
    public UploadedFile(Context context, Uri uri, String name) {
        this.context = context;
        this.uri = uri;
        this.name = name;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAnalysisSummary() {
        return analysisSummary;
    }

    public void setAnalysisSummary(String analysisSummary) {
        this.analysisSummary = analysisSummary;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public Uri getUri() { return uri; }
    public String getName() { return name; }

    // Updated getFileType method
    public String getFileType() {
        if (fileType == null) {
            // If context is available, try to get MIME type
            if (context != null && uri != null) {
                fileType = context.getContentResolver().getType(uri);
            }

            // Fallback: try to determine type from file extension
            if (fileType == null && name != null) {
                String lowercaseName = name.toLowerCase();
                if (lowercaseName.endsWith(".jpg") || lowercaseName.endsWith(".jpeg")) {
                    fileType = "image/jpeg";
                } else if (lowercaseName.endsWith(".png")) {
                    fileType = "image/png";
                } else if (lowercaseName.endsWith(".pdf")) {
                    fileType = "application/pdf";
                } else if (lowercaseName.endsWith(".doc")) {
                    fileType = "application/msword";
                } else if (lowercaseName.endsWith(".docx")) {
                    fileType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                }
            }
        }
        return fileType != null ? fileType : "application/octet-stream";
    }

    // Add method to set context later if needed
    public void setContext(Context context) {
        this.context = context;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public boolean hasAnalysis() { return hasAnalysis; }
    public void setHasAnalysis(boolean hasAnalysis) { this.hasAnalysis = hasAnalysis; }

    public String getAiAnalysisSummary() { return aiAnalysisSummary; }
    public void setAiAnalysisSummary(String aiAnalysisSummary) { this.aiAnalysisSummary = aiAnalysisSummary; }

    public String getAiAnalysisDetailed() { return aiAnalysisDetailed; }
    public void setAiAnalysisDetailed(String aiAnalysisDetailed) { this.aiAnalysisDetailed = aiAnalysisDetailed; }

    public boolean canAnalyze() { return canAnalyze; }
    public void setCanAnalyze(boolean canAnalyze) { this.canAnalyze = canAnalyze; }

    public boolean isShareWithFamily() { return shareWithFamily; }
    public void setShareWithFamily(boolean shareWithFamily) { this.shareWithFamily = shareWithFamily; }

    public boolean isIncludeInChat() { return includeInChat; }
    public void setIncludeInChat(boolean includeInChat) { this.includeInChat = includeInChat; }

    public List<KeyFinding> getKeyFindings() { return keyFindings; }
    public void setKeyFindings(List<KeyFinding> keyFindings) { this.keyFindings = keyFindings; }
    public void addKeyFinding(KeyFinding finding) { this.keyFindings.add(finding); }
    public void clearKeyFindings() { this.keyFindings.clear(); }

    public long getReportDateMillis() { return reportDateMillis; }
    public void setReportDateMillis(long reportDateMillis) { this.reportDateMillis = reportDateMillis; }

    public String getDetailedSummary() { return detailedSummary; }
    public void setDetailedSummary(String detailedSummary) { this.detailedSummary = detailedSummary; }

    public String getAiOpinion() { return aiOpinion; }
    public void setAiOpinion(String aiOpinion) { this.aiOpinion = aiOpinion; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }

    public String getReportTypeDetected() { return reportTypeDetected; }
    public void setReportTypeDetected(String reportTypeDetected) { this.reportTypeDetected = reportTypeDetected; }

    public String getAnalysisStatus() { return analysisStatus; }
    public void setAnalysisStatus(String analysisStatus) { this.analysisStatus = analysisStatus; }

    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }

    public boolean isAnalysisTrustworthy() {
        return analysisStatus == null
                || analysisStatus.isEmpty()
                || "ok".equalsIgnoreCase(analysisStatus)
                || "partial".equalsIgnoreCase(analysisStatus);
    }

    public List<String> getRecommendations() { return recommendations; }
    public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations != null ? recommendations : new ArrayList<>(); }

    public List<String> getFollowUpTests() { return followUpTests; }
    public void setFollowUpTests(List<String> followUpTests) { this.followUpTests = followUpTests != null ? followUpTests : new ArrayList<>(); }

    public List<String> getLifestyleAdvice() { return lifestyleAdvice; }
    public void setLifestyleAdvice(List<String> lifestyleAdvice) { this.lifestyleAdvice = lifestyleAdvice != null ? lifestyleAdvice : new ArrayList<>(); }

    public List<PossibleCondition> getPossibleConditions() { return possibleConditions; }
    public void setPossibleConditions(List<PossibleCondition> possibleConditions) { this.possibleConditions = possibleConditions != null ? possibleConditions : new ArrayList<>(); }

    public static class PossibleCondition {
        private final String name;
        private final String confidence;
        private final String rationale;

        public PossibleCondition(String name, String confidence, String rationale) {
            this.name = name;
            this.confidence = confidence;
            this.rationale = rationale;
        }

        public String getName() { return name; }
        public String getConfidence() { return confidence; }
        public String getRationale() { return rationale; }
    }
}