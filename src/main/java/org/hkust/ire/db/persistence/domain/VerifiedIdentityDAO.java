package org.hkust.ire.db.persistence.domain;

import javax.persistence.*;
import java.util.Date;

/**
 * DAO representing a verified identity from IAM (Midpoint).
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Entity
@Table(name = "VERIFIED_IDENTITIES")
public class VerifiedIdentityDAO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "GOLDEN_ID", nullable = false)
    private String goldenId;

    @Column(name = "IAM_ID")
    private String iamId;

    @Column(name = "ITSC_ACCOUNT")
    private String itscAccount;

    @Column(name = "ROLES", columnDefinition = "TEXT")
    private String roles;

    @Column(name = "VERIFIED_STATUS")
    private String verifiedStatus;

    @Column(name = "LAST_SYNC_DATE")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastSyncDate;

    @Column(name = "CREATED_DATE")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;

    /** Default constructor. */
    public VerifiedIdentityDAO() {
    }

    /**
     * Parameterized constructor.
     *
     * @param goldenId golden identity ID
     * @param iamId    IAM system ID
     */
    public VerifiedIdentityDAO(String goldenId, String iamId) {
        this.goldenId = goldenId;
        this.iamId = iamId;
        this.verifiedStatus = "PENDING";
        this.createdDate = new Date();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getGoldenId() { return goldenId; }
    public void setGoldenId(String goldenId) { this.goldenId = goldenId; }

    public String getIamId() { return iamId; }
    public void setIamId(String iamId) { this.iamId = iamId; }

    public String getItscAccount() { return itscAccount; }
    public void setItscAccount(String itscAccount) { this.itscAccount = itscAccount; }

    public String getRoles() { return roles; }
    public void setRoles(String roles) { this.roles = roles; }

    public String getVerifiedStatus() { return verifiedStatus; }
    public void setVerifiedStatus(String verifiedStatus) { this.verifiedStatus = verifiedStatus; }

    public Date getLastSyncDate() { return lastSyncDate; }
    public void setLastSyncDate(Date lastSyncDate) { this.lastSyncDate = lastSyncDate; }

    public Date getCreatedDate() { return createdDate; }
    public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }
}
