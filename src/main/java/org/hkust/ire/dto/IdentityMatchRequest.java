package org.hkust.ire.dto;

/**
 * DTO representing an identity match request.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
public class IdentityMatchRequest {

    private String hkid;
    private String staffId;
    private String studentId;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private String sourceSystem;

    /** Default constructor. */
    public IdentityMatchRequest() {
    }

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

    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
}
