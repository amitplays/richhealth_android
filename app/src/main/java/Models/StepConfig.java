package Models;

import androidx.annotation.DrawableRes;

import java.util.List;

/**
 * Data-driven configuration for a single onboarding card-selection step.
 *
 * To add a new onboarding question: create a new StepConfig (or add a
 * SectionConfig to an existing one) in OnboardingActivity.initCardStepConfigs().
 * No new Fragment class needed — CardStepFragment renders any StepConfig.
 */
public class StepConfig {

    /**
     * Writes a section's selected value(s) into the shared OnboardingData.
     * Single-select receives Object; multi-select receives List<Object>.
     */
    public interface DataWriter {
        void write(OnboardingData data, Object selectedValue);
    }

    // ── Section — one question block within a step ───────────────────────────

    public static class SectionConfig {
        /** Question/header text shown above the card grid. Null = no header. */
        public final String sectionTitle;
        /** Optional "why we're asking" sub-heading rendered under sectionTitle. */
        public final String whySubtitle;
        public final List<SelectableOption> options;
        public final boolean multiSelect;
        /** If true, validate() will reject this step unless at least one card is tapped. */
        public final boolean required;
        /** Number of columns in the card grid. */
        public final int spanCount;
        /** Writes the selected value(s) into OnboardingData. */
        public final DataWriter dataWriter;
        /**
         * Index of the option that, when selected, clears all others (e.g. "None").
         * -1 = disabled.
         */
        public final int clearOthersPosition;

        // Legacy ctor — no why subtitle, no clearOthers
        public SectionConfig(String sectionTitle, List<SelectableOption> options,
                             boolean multiSelect, boolean required, int spanCount,
                             DataWriter dataWriter) {
            this(sectionTitle, null, options, multiSelect, required, spanCount, dataWriter, -1);
        }

        // Legacy ctor — no why subtitle, with clearOthers
        public SectionConfig(String sectionTitle, List<SelectableOption> options,
                             boolean multiSelect, boolean required, int spanCount,
                             DataWriter dataWriter, int clearOthersPosition) {
            this(sectionTitle, null, options, multiSelect, required, spanCount, dataWriter, clearOthersPosition);
        }

        // New ctor — with why subtitle, no clearOthers
        public SectionConfig(String sectionTitle, String whySubtitle,
                             List<SelectableOption> options,
                             boolean multiSelect, boolean required, int spanCount,
                             DataWriter dataWriter) {
            this(sectionTitle, whySubtitle, options, multiSelect, required, spanCount, dataWriter, -1);
        }

        // Full ctor
        public SectionConfig(String sectionTitle, String whySubtitle,
                             List<SelectableOption> options,
                             boolean multiSelect, boolean required, int spanCount,
                             DataWriter dataWriter, int clearOthersPosition) {
            this.sectionTitle = sectionTitle;
            this.whySubtitle = whySubtitle;
            this.options = options;
            this.multiSelect = multiSelect;
            this.required = required;
            this.spanCount = spanCount;
            this.dataWriter = dataWriter;
            this.clearOthersPosition = clearOthersPosition;
        }
    }

    // ── Step ─────────────────────────────────────────────────────────────────

    public final String heroEmoji;
    @DrawableRes
    public final int heroIconRes;
    public final String title;
    public final String subtitle;
    public final List<SectionConfig> sections;

    // Legacy emoji-based constructor
    public StepConfig(String heroEmoji, String title, String subtitle,
                      List<SectionConfig> sections) {
        this(heroEmoji, 0, title, subtitle, sections);
    }

    // Icon-based constructor
    public StepConfig(@DrawableRes int heroIconRes, String title, String subtitle,
                      List<SectionConfig> sections) {
        this("", heroIconRes, title, subtitle, sections);
    }

    public StepConfig(String heroEmoji, @DrawableRes int heroIconRes,
                      String title, String subtitle, List<SectionConfig> sections) {
        this.heroEmoji = heroEmoji;
        this.heroIconRes = heroIconRes;
        this.title = title;
        this.subtitle = subtitle;
        this.sections = sections;
    }

    public boolean hasHeroIcon() {
        return heroIconRes != 0;
    }
}
