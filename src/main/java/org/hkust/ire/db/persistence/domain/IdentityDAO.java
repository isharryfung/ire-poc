package org.hkust.ire.db.persistence.domain;

import javax.persistence.*;
import java.util.Date;

/**
 * DAO representing the golden (master) identity record in the IRE system.
 *
 * <p>Stores the consolidated, deduplicated identity information aggregated
 * from all source systems (CRM, ADMS, Attendance, 3rd-party forms).</p>
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Entity
@Table(name = "IDENTITIES")
public class IdentityDAO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "GOLDEN_ID", nullable = false, unique = true)
    private String goldenId;

    @Column(name = "HKID")
    private String hkid;

    @Column(name = "STAFF_ID")
    private String staffId;

    @Column(name = "STUDENT_ID")
    private String studentId;

    @Column(name = "EMAIL", nullable = false)
    private String email;

    @Column(name = "FIRST_NAME")
    private String firstName;

    @Column(name = "LAST_NAME")
    private String lastName;

    @Column(name = "PHONE")
    private String phone;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "CONFIDENCE_SCORE")
    private Double confidenceScore;

    @Column(name = "PRIMARY_SOURCE")
    private String primarySource;

    @Column(name = "CREATED_BY")
    private String createdBy;

    @Column(name = "CREATED_DATE")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;

    @Column(name = "UPDATED_BY")
    private String updatedBy;

    @Column(name = "UPDATED_DATE")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedDate;

    /** Default constructor. */
    public IdentityDAO() {
    }

    /**
     * Parameterized constructor for core fields.
     *
     * @param goldenId the unique golden identity ID
     * @param email    the primary email
     * @param status   the status
     */
    public IdentityDAO(String goldenId, String email, String status) {
        this.goldenId = goldenId;
        this.email = email;
        this.status = status;
        this.createdDate = new Date();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getGoldenId() { return goldenId; }
    public void setGoldenId(String goldenId) { this.goldenId = goldenId; }

    public String getHkid() { return hkid; }
    public void setHkid(String hkid) { this.hkid = hkid; }

    public String getStaffId() { return staffId; }
    public void setStaffId(String staffId) { this.staffId = staffId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getPrimarySource() { return primarySource; }
    public void setPrimarySource(String primarySource) { this.primarySource = primarySource; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Date getCreatedDate() { return createdDate; }
    public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public Date getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(Date updatedDate) { this.updatedDate = updatedDate; }

    @Override
    public String toString() {
        return "IdentityDAO{goldenId='" + goldenId + "', email='" + email + "', status='" + status + "'}";
    }
}
