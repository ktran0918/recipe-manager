package com.recipemanager.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

// @Embeddable marks this as a value type that can be embedded inside an entity.
// Used here as a composite primary key for HouseholdMember (household_id + user_id).
// C# equivalent: In EF Core you'd use HasKey(x => new { x.HouseholdId, x.UserId }) in
// OnModelCreating — there's no separate key class required. Java requires one.
//
// Must implement Serializable — JPA uses Java serialization internally for caching and
// second-level cache keys. equals() and hashCode() are required so JPA can compare and
// deduplicate key instances (e.g., to check if an entity is already in the session cache).
@Embeddable
public class HouseholdMemberId implements Serializable {

    @Column(name = "household_id")
    private UUID householdId;

    @Column(name = "user_id")
    private UUID userId;

    protected HouseholdMemberId() {}

    public HouseholdMemberId(UUID householdId, UUID userId) {
        this.householdId = householdId;
        this.userId = userId;
    }

    public UUID getHouseholdId() { return householdId; }
    public UUID getUserId() { return userId; }

    // Implement equals() and hashCode() using both householdId and userId.
    // In IntelliJ: ⌘N (Mac) / Alt+Insert (Windows) → equals() and hashCode() → select both fields.
    // Why this matters: JPA compares key objects to decide if two entity instances represent
    // the same DB row. Without equals/hashCode, every HouseholdMemberId is "different" even
    // if they hold the same IDs — cache misses, duplicate inserts, wrong merge behavior.

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        HouseholdMemberId that = (HouseholdMemberId) o;
        return Objects.equals(householdId, that.householdId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(householdId, userId);
    }
}
