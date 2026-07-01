package com.recipemanager.api.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "recipes")
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    // Scalar FK column — no relationship navigation, just the raw UUID.
    // Use this when you only ever need the ID, not the full Household object.
    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal servings;

    @Column(name = "cook_time_minutes")
    private Integer cookTimeMinutes;

    @Column(name = "prep_time_minutes")
    private Integer prepTimeMinutes;

    private String complexity;

    // PostgreSQL text[] — Hibernate 6 maps String[] natively to the array column type.
    // C# equivalent: a string[] property with a HasConversion() value converter in EF Core.
    @Column(columnDefinition = "text[]")
    private String[] occasions;

    private String cuisine;

    @Column(name = "diet_tags", columnDefinition = "text[]")
    private String[] dietTags;

    @Column(name = "parsed_at")
    private OffsetDateTime parsedAt;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RecipeStep> steps = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getHouseholdId() { return householdId; }
    public UUID getCreatedBy() { return createdBy; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getSourceUrl() { return sourceUrl; }
    public String getImageUrl() { return imageUrl; }
    public BigDecimal getServings() { return servings; }
    public Integer getCookTimeMinutes() { return cookTimeMinutes; }
    public Integer getPrepTimeMinutes() { return prepTimeMinutes; }
    public String getComplexity() { return complexity; }
    public String[] getOccasions() { return occasions; }
    public String getCuisine() { return cuisine; }
    public String[] getDietTags() { return dietTags; }
    public OffsetDateTime getParsedAt() { return parsedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public List<RecipeIngredient> getIngredients() { return ingredients; }
    public List<RecipeStep> getSteps() { return steps; }

    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setServings(BigDecimal servings) { this.servings = servings; }
    public void setCookTimeMinutes(Integer cookTimeMinutes) { this.cookTimeMinutes = cookTimeMinutes; }
    public void setPrepTimeMinutes(Integer prepTimeMinutes) { this.prepTimeMinutes = prepTimeMinutes; }
    public void setComplexity(String complexity) { this.complexity = complexity; }
    public void setOccasions(String[] occasions) { this.occasions = occasions; }
    public void setCuisine(String cuisine) { this.cuisine = cuisine; }
    public void setDietTags(String[] dietTags) { this.dietTags = dietTags; }
    public void setParsedAt(OffsetDateTime parsedAt) { this.parsedAt = parsedAt; }
}
