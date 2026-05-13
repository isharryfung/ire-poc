package org.hkust.ire.db.persistence.domain;

import javax.persistence.*;
import java.util.Date;

/**
 * DAO representing source credibility configuration for each source system.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Entity
@Table(name = "SOURCE_CREDIBILITY")
public class SourceCredibilityDAO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SOURCE_SYSTEM", nullable = false, unique = true)
    private String sourceSystem;

    @Column(name = "CREDIBILITY_SCORE", nullable = false)
    private Double credibilityScore;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "CREATED_DATE")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;

    @Column(name = "UPDATED_DATE")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedDate;

    /** Default constructor. */
    public SourceCredibilityDAO() {
    }

    /**
     * Parameterized constructor.
     *
     * @param sourceSystem     source system name
     * @param credibilityScore credibility weight (0.0 to 1.0)
     */
    public SourceCredibilityDAO(String sourceSystem, Double credibilityScore) {
        this.sourceSystem = sourceSystem;
        this.credibilityScore = credibilityScore;
        this.status = "ACTIVE";
        this.createdDate = new Date();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }

    public Double getCredibilityScore() { return credibilityScore; }
    public void setCredibilityScore(Double credibilityScore) { this.credibilityScore = credibilityScore; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getCreatedDate() { return createdDate; }
    public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }

    public Date getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(Date updatedDate) { this.updatedDate = updatedDate; }
}
