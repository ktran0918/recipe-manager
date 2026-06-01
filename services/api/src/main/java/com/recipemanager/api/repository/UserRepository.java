package com.recipemanager.api.repository;

import com.recipemanager.api.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

// JpaRepository<Entity, PrimaryKeyType> gives you findById, save, delete, findAll, etc.
// for free — Spring Data generates the implementation at startup from this interface alone.
// C# equivalent: DbSet<User> in EF Core, but without needing to write any repository class.
public interface UserRepository extends JpaRepository<User, UUID> {

    // Spring Data parses method names into JPQL automatically.
    // "findBy" + field name + no suffix → SELECT u FROM User u WHERE u.email = :email
    // C# equivalent: dbContext.Users.FirstOrDefault(u => u.Email == email)
    Optional<User> findByEmail(String email);

    Optional<User> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);
}
