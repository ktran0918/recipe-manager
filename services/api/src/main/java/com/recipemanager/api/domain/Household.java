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
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "households")
public class Household {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "invite_code", nullable = false, unique = true)
    private String inviteCode;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    // @OneToMany + mappedBy = the inverse side of the relationship.
    // mappedBy = "household" refers to the field named 'household' in HouseholdMember.
    // cascade = ALL means persist/delete/merge on Household propagate to its members.
    // orphanRemoval removes a HouseholdMember row when it's removed from this list.
    // FetchType.LAZY: members are NOT loaded from DB until accessed. Always prefer LAZY
    // on collections — EAGER loads the entire collection on every Household query (N+1 trap).
    // C# equivalent: ICollection<HouseholdMember> with HasMany(...).WithOne(...) in OnModelCreating.
    @OneToMany(mappedBy = "household", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<HouseholdMember> members = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getInviteCode() { return inviteCode; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public List<HouseholdMember> getMembers() { return members; }

    public void setName(String name) { this.name = name; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }
    public void setMembers(List<HouseholdMember> members) { this.members = members; }
}
