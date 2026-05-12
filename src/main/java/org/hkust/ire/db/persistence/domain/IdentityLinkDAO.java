package org.hkust.ire.db.persistence.domain;

import javax.persistence.*;
import java.util.Date;

/**
 * DAO representing a link between a golden identity and a source system record.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Entity
@Table(name = "IDENTITY_LINKS")
public class IdentityLinkDAO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "GOLDEN_ID", nullable = false)
    private String goldenId;

    @Column(name = "SOURCE_SYSTEM", nullable = false)
    private String sourceSystem;

    @Column(name = "SOURCE_ID", nullable = false)
    private String sourceId;

    @Column(name = "CREDIBILITY_SCORE")
    private Double credibilityScore;

    @Column(name = "MATCH_TIER")
    private String matchTier;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "CREATED_DATE")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;

    @Column(name = "UPDATED_DATE")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedDate;

    /** Default constructor. */
    public IdentityLinkDAO() {
    }

    /**
     * Parameterized constructor.
     *
     * @param goldenId     golden identity ID
     * @param sourceSystem source system name
     * @param sourceId     source record ID
     */
    public IdentityLinkDAO(String goldenId, String sourceSystem, String sourceId) {
        this.goldenId = goldenId;
        this.sourceSystem = sourceSystem;
        this.sourceId = sourceId;
        this.createdDate = new Date();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getGoldenId() { return goldenId; }
    public void setGoldenId(String goldenId) { this.goldenId = goldenId; }

    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public Double getCredibilityScore() { return credibilityScore; }
    public void setCredibilityScore(Double credibilityScore) { this.credibilityScore = credibilityScore; }

    public String getMatchTier() { return matchTier; }
    public void setMatchTier(String matchTier) { this.matchTier = matchTier; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getCreatedDate() { return createdDate; }
    public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }

    public Date getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(Date updatedDate) { this.updatedDate = updatedDate; }
}
