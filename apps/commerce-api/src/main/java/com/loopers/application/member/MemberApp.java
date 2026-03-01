package com.loopers.application.member;

import com.loopers.domain.member.Gender;
import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@RequiredArgsConstructor
@Component
public class MemberApp {
    private final MemberService memberService;

    @Transactional
    public MemberInfo register(String memberId, String password, String email,
                               String birthDate, String name, Gender gender) {
        MemberModel member = memberService.register(
            memberId, password, email, birthDate, name, gender
        );
        return MemberInfo.from(member);
    }

    @Transactional(readOnly = true)
    public MemberInfo authenticate(String loginId, String loginPw) {
        MemberModel member = memberService.authenticate(loginId, loginPw);
        return MemberInfo.from(member);
    }

    @Transactional
    public void changePassword(String loginId, String loginPw,
                               String currentPassword, String newPassword) {
        memberService.changePassword(loginId, loginPw, currentPassword, newPassword);
    }
}
