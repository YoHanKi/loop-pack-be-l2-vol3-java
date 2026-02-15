package com.loopers.application.member;

import com.loopers.domain.member.MemberModel;


public record MemberInfo(
    Long id,
    String memberId,
    String email,
    String birthDate,
    String name,
    String gender
) {

    public static MemberInfo from(MemberModel member) {
        return new MemberInfo(
            member.getId(),
            member.getMemberId().value(),
            member.getEmail().address(),
            member.getBirthDate().asString(),
            member.getName().value(),
            member.getGender().name()
        );
    }
}
