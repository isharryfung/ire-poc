package org.hkust.ire.common.constant;

/**
 * Constants for waterfall matching tiers and confidence thresholds.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
public final class MatchTierConstant {

    /** TIER-1: Deterministic exact-ID match */
    public static final String TIER_1 = "TIER_1";

    /** TIER-2: Probabilistic composite scoring */
    public static final String TIER_2 = "TIER_2";

    /** TIER-3: Insufficient evidence, routes to manual review */
    public static final String TIER_3 = "TIER_3";

    /** Confidence threshold for automatic merge decision */
    public static final double AUTO_MERGE_THRESHOLD = 0.85;

    /** Lower bound for manual review consideration */
    public static final double TIER_2_THRESHOLD = 0.50;

    private MatchTierConstant() {
    }
}
