package org.hkust.ire.common.constant;

/**
 * Constants for source system identifiers and credibility weights.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
public final class SourceSystemConstant {

    /** CRM - master data, highest credibility */
    public static final String CRM = "CRM";

    /** ADMS - admissions system */
    public static final String ADMS = "ADMS";

    /** Attendance system */
    public static final String ATTENDANCE = "ATTENDANCE";

    /** Event system */
    public static final String EVENT_SYSTEM = "EVENT_SYSTEM";

    /** Third-party forms */
    public static final String THIRD_PARTY = "THIRD_PARTY";

    /** IAM (Midpoint) */
    public static final String IAM = "IAM";

    /** CRM credibility weight - 100% trusted */
    public static final double CREDIBILITY_CRM = 1.0;

    /** ADMS/Attendance credibility weight - 90% trusted */
    public static final double CREDIBILITY_ADMS = 0.9;

    /** Third-party credibility weight - 70% trusted */
    public static final double CREDIBILITY_THIRD_PARTY = 0.7;

    private SourceSystemConstant() {
    }
}
