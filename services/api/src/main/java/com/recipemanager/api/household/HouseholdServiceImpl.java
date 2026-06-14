package com.recipemanager.api.household;

import com.recipemanager.api.domain.Household;
import com.recipemanager.api.domain.HouseholdMember;
import com.recipemanager.api.domain.HouseholdMemberId;
import com.recipemanager.api.domain.User;
import com.recipemanager.api.exception.ApiException;
import com.recipemanager.api.repository.HouseholdMemberRepository;
import com.recipemanager.api.repository.HouseholdRepository;
import com.recipemanager.api.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// @Transactional at the class level wraps every public method in a DB transaction.
// Individual methods can override with @Transactional(readOnly = true) for SELECT-only paths —
// the DB can optimize read-only transactions (no dirty-checking, no flush).
// C# equivalent: TransactionScope or EF Core's SaveChanges() — but here it's declarative.
@Service
@Transactional
public class HouseholdServiceImpl implements HouseholdService {

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository memberRepository;
    private final UserRepository userRepository;

    public HouseholdServiceImpl(HouseholdRepository householdRepository,
                                HouseholdMemberRepository memberRepository,
                                UserRepository userRepository) {
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    @Override
    public HouseholdResponse createHousehold(UUID userId, String name) {
        List<HouseholdMember> exist = memberRepository.findById_UserId(userId);
        if (!exist.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "User already belongs to a household");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        Household household = new Household();
        household.setName(name);
        household.setInviteCode(generateInviteCode());

        HouseholdMember member = new HouseholdMember(household, user, "owner");
        household.getMembers().add(member);
        Household saved = householdRepository.save(household);

        return HouseholdResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public HouseholdResponse getMyHousehold(UUID householdId) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new EntityNotFoundException("Household not found: " + householdId));
        return HouseholdResponse.from(household);
    }

    @Override
    public HouseholdResponse updateHousehold(UUID householdId, UUID callerId, String name) {
        requireOwner(householdId, callerId);

        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new EntityNotFoundException("Household not found: " + householdId));

        household.setName(name);
        Household saved = householdRepository.save(household);
        return HouseholdResponse.from(saved);
    }

    @Override
    public String regenerateInviteCode(UUID householdId, UUID callerId) {
        requireOwner(householdId, callerId);

        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new EntityNotFoundException("Household not found: " + householdId));

        String inviteCode = generateInviteCode();
        household.setInviteCode(inviteCode);
        householdRepository.save(household);

        return inviteCode;
    }

    @Override
    public HouseholdResponse joinHousehold(UUID userId, String inviteCode) {
        Household household = householdRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new EntityNotFoundException("Invalid invite code"));

        if (memberRepository.findById(new HouseholdMemberId(household.getId(), userId)).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Already a member of this household");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        HouseholdMember member = new HouseholdMember(household, user, "member");
        household.getMembers().add(member);
        Household saved = householdRepository.save(household);

        return HouseholdResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(UUID householdId) {
        // .stream() converts List<HouseholdMember> into a processing pipeline.
        // .map(MemberResponse::from) transforms each element — method reference shorthand
        //   for m -> MemberResponse.from(m). C# equivalent: .Select(MemberResponse.From)
        // .toList() terminates the stream and collects into List<MemberResponse>.
        return memberRepository.findByHousehold_Id(householdId)
                .stream().map(MemberResponse::from).toList();
    }

    @Override
    public void removeMember(UUID householdId, UUID callerId, UUID targetUserId) {
        requireOwner(householdId, callerId);

        if (callerId.equals(targetUserId))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot remove yourself from the household");

        HouseholdMember member = memberRepository.findById(new HouseholdMemberId(householdId, targetUserId))
                .orElseThrow(() -> new EntityNotFoundException("Member not found"));
        memberRepository.delete(member);
    }

    // Loads the caller's membership and asserts the "owner" role.
    // Shared by updateHousehold, regenerateInviteCode, and removeMember.
    private void requireOwner(UUID householdId, UUID callerId) {
         HouseholdMember member = memberRepository.findById(new HouseholdMemberId(householdId, callerId))
                 .orElseThrow(() -> new EntityNotFoundException("Membership not found"));
        if (!member.getRole().equals("owner"))
            throw new ApiException(HttpStatus.FORBIDDEN, "Owner only");
    }

    private String generateInviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
