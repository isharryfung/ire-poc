package com.university.ire.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "verified_identities")
public class VerifiedIdentity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String subject;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
