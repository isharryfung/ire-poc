package org.hkust.ire.db.persistence.domain;

import javax.persistence.*;
import java.util.Date;

/**
 * DAO representing a relationship edge in the identity graph.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Entity
@Table(name = "IDENTITY_GRAPH")
public class IdentityGraphDAO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SOURCE_GOLDEN_ID", nullable = false)
    private String sourceGoldenId;

    @Column(name = "TARGET_GOLDEN_ID", nullable = false)
    private String targetGoldenId;

    @Column(name = "RELATIONSHIP_TYPE")
    private String relationshipType;

    @Column(name = "RELATIONSHIP_STRENGTH")
    private Double relationshipStrength;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "CREATED_DATE")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;

    /** Default constructor. */
    public IdentityGraphDAO() {
    }

    /**
     * Parameterized constructor.
     *
     * @param sourceGoldenId   source identity ID
     * @param targetGoldenId   target identity ID
     * @param relationshipType type of relationship
     */
    public IdentityGraphDAO(String sourceGoldenId, String targetGoldenId, String relationshipType) {
        this.sourceGoldenId = sourceGoldenId;
        this.targetGoldenId = targetGoldenId;
        this.relationshipType = relationshipType;
        this.createdDate = new Date();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSourceGoldenId() { return sourceGoldenId; }
    public void setSourceGoldenId(String sourceGoldenId) { this.sourceGoldenId = sourceGoldenId; }

    public String getTargetGoldenId() { return targetGoldenId; }
    public void setTargetGoldenId(String targetGoldenId) { this.targetGoldenId = targetGoldenId; }

    public String getRelationshipType() { return relationshipType; }
    public void setRelationshipType(String relationshipType) { this.relationshipType = relationshipType; }

    public Double getRelationshipStrength() { return relationshipStrength; }
    public void setRelationshipStrength(Double relationshipStrength) { this.relationshipStrength = relationshipStrength; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getCreatedDate() { return createdDate; }
    public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }
}
