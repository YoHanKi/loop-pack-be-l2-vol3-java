package com.loopers.domain.member;


public interface MemberRepository {
    MemberModel save(MemberModel member);
    boolean existsByMemberId(MemberId memberId);
}
