package com.university.ire.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "identity_graph")
public class IdentityGraph {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "from_identity_id")
    private Identity fromIdentity;

    @ManyToOne(optional = false)
    @JoinColumn(name = "to_identity_id")
    private Identity toIdentity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RelationshipType relationshipType;

    @Column(nullable = false)
    private double strength;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Identity getFromIdentity() { return fromIdentity; }
    public void setFromIdentity(Identity fromIdentity) { this.fromIdentity = fromIdentity; }
    public Identity getToIdentity() { return toIdentity; }
    public void setToIdentity(Identity toIdentity) { this.toIdentity = toIdentity; }
    public RelationshipType getRelationshipType() { return relationshipType; }
    public void setRelationshipType(RelationshipType relationshipType) { this.relationshipType = relationshipType; }
    public double getStrength() { return strength; }
    public void setStrength(double strength) { this.strength = strength; }
    public Instant getCreatedAt() { return createdAt; }
}
