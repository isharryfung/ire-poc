package com.university.ire.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "source_credibility")
public class SourceCredibility {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String sourceSystem;

    @Column(nullable = false)
    private double multiplier;

    @Column(length = 500)
    private String description;

    public Long getId() { return id; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public double getMultiplier() { return multiplier; }
    public void setMultiplier(double multiplier) { this.multiplier = multiplier; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
