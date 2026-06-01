package com.recipemanager.api.repository;

import com.recipemanager.api.domain.Household;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface HouseholdRepository extends JpaRepository<Household, UUID> {

    Optional<Household> findByInviteCode(String inviteCode);
}
