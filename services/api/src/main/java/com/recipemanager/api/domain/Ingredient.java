package com.recipemanager.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "ingredients")
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    // This column has a UNIQUE constraint in the DB (see schema.md).
    // Spring Data's findByNormalizedName will use the index automatically.
    @Column(name = "normalized_name", nullable = false, unique = true)
    private String normalizedName;

    private String category;

    private String barcode;

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getNormalizedName() { return normalizedName; }
    public String getCategory() { return category; }
    public String getBarcode() { return barcode; }

    public void setName(String name) { this.name = name; }
    public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }
    public void setCategory(String category) { this.category = category; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
}
