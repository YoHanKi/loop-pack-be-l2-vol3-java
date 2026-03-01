package com.loopers.domain.member;

import com.loopers.domain.member.vo.MemberId;

import java.util.Optional;

public interface MemberRepository {
    MemberModel save(MemberModel member);
    boolean existsByMemberId(MemberId memberId);
    Optional<MemberModel> findByMemberId(MemberId memberId);
}
