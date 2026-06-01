package com.recipemanager.api.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

// @Entity registers this class with JPA's persistence context.
// @Table maps it to the exact DB table name.
// C# equivalent: an EF Core model class decorated with [Table("users")].
@Entity
@Table(name = "users")
public class User {

    // @Id marks the primary key. @GeneratedValue(UUID) delegates to the DB default (gen_random_uuid()).
    // C# equivalent: [Key] + .ValueGeneratedOnAdd() in EF Core.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    // @Column(name=...) is only needed when the Java field name differs from the DB column name.
    // JPA defaults to snake_case mapping in Spring Boot — but being explicit avoids surprises.
    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "oauth_provider", nullable = false)
    private String oauthProvider;

    @Column(name = "oauth_id", nullable = false)
    private String oauthId;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    // @PrePersist runs just before INSERT. C# equivalent: overriding SaveChanges() or using
    // an EF Core interceptor to set timestamp fields automatically.
    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getOauthProvider() { return oauthProvider; }
    public String getOauthId() { return oauthId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setEmail(String email) { this.email = email; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public void setOauthProvider(String oauthProvider) { this.oauthProvider = oauthProvider; }
    public void setOauthId(String oauthId) { this.oauthId = oauthId; }
}