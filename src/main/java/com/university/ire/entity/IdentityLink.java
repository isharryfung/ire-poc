package com.university.ire.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "identity_links")
public class IdentityLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "identity_id")
    private Identity identity;

    @Column(nullable = false)
    private String sourceSystem;

    @Column(nullable = false)
    private String sourceRecordId;

    public Long getId() { return id; }
    public Identity getIdentity() { return identity; }
    public void setIdentity(Identity identity) { this.identity = identity; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getSourceRecordId() { return sourceRecordId; }
    public void setSourceRecordId(String sourceRecordId) { this.sourceRecordId = sourceRecordId; }
}
