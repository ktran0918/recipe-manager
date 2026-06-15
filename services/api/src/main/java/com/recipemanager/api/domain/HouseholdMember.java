package com.recipemanager.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "household_members")
public class HouseholdMember {

    // @EmbeddedId tells JPA this entity's primary key is a composite, defined by the
    // @Embeddable class. JPA reads the @Column annotations inside HouseholdMemberId
    // to know which columns form the key.
    @EmbeddedId
    private HouseholdMemberId id;

    // @MapsId("householdId") links this FK field to the householdId field inside the
    // embedded key. Without it, JPA would try to manage two separate columns for the same
    // household_id — one for the key and one for the FK.
    // @JoinColumn declares the actual FK column name in the table.
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("householdId")
    @JoinColumn(name = "household_id")
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String role;

    @Column(name = "joined_at", updatable = false)
    private OffsetDateTime joinedAt;

    protected HouseholdMember() {}

    public HouseholdMember(Household household, User user, String role) {
        this.id = new HouseholdMemberId(household.getId(), user.getId());
        this.household = household;
        this.user = user;
        this.role = role;
    }

    @PrePersist
    protected void onCreate() {
        joinedAt = OffsetDateTime.now();
    }

    public HouseholdMemberId getId() { return id; }
    public Household getHousehold() { return household; }
    public User getUser() { return user; }
    public String getRole() { return role; }
    public OffsetDateTime getJoinedAt() { return joinedAt; }

    public void setRole(String role) { this.role = role; }
}
