package org.hkust.ire.db.persistence.domain;

import javax.persistence.*;
import java.util.Date;

/**
 * DAO representing an audit log entry for tracking all IRE operations.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Entity
@Table(name = "AUDIT_LOGS")
public class AuditLogDAO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ACTION", nullable = false)
    private String action;

    @Column(name = "ENTITY_TYPE")
    private String entityType;

    @Column(name = "ENTITY_ID")
    private String entityId;

    @Column(name = "DETAILS", columnDefinition = "TEXT")
    private String details;

    @Column(name = "PERFORMED_BY")
    private String performedBy;

    @Column(name = "SOURCE_SYSTEM")
    private String sourceSystem;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "CREATED_DATE")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;

    /** Default constructor. */
    public AuditLogDAO() {
    }

    /**
     * Parameterized constructor.
     *
     * @param action     the action performed
     * @param entityType type of entity affected
     * @param entityId   ID of the affected entity
     */
    public AuditLogDAO(String action, String entityType, String entityId) {
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.createdDate = new Date();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }

    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getCreatedDate() { return createdDate; }
    public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }
}
