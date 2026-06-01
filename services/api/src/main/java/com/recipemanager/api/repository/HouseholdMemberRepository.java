package com.recipemanager.api.repository;

import com.recipemanager.api.domain.HouseholdMember;
import com.recipemanager.api.domain.HouseholdMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, HouseholdMemberId> {

    List<HouseholdMember> findById_UserId(UUID userId);

    List<HouseholdMember> findByHousehold_Id(UUID householdId);
}
