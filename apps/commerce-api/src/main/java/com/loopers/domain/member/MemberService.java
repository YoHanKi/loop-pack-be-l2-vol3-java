package com.loopers.domain.member;

import com.loopers.domain.member.vo.MemberId;
import com.loopers.security.PasswordHasher;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordHasher passwordHasher;

    public MemberModel register(String memberId, String rawPassword, String email, String birthDate, String name, Gender gender) {
        if (memberRepository.existsByMemberId(new MemberId(memberId))) {
            throw new CoreException(ErrorType.CONFLICT, "이미 가입된 ID 입니다.");
        }

        MemberModel member = MemberModel.create(memberId, rawPassword, email, birthDate, name, gender, passwordHasher);
        return memberRepository.save(member);
    }

    public MemberModel authenticate(String loginId, String loginPw) {
        MemberModel member = memberRepository.findByMemberId(new MemberId(loginId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 회원이 존재하지 않습니다."));
        if (!member.verifyPassword(passwordHasher, loginPw)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "비밀번호가 일치하지 않습니다.");
        }
        return member;
    }

    public void changePassword(String loginId, String loginPw,
                               String currentPassword, String newPassword) {
        MemberModel member = memberRepository.findByMemberId(new MemberId(loginId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 회원이 존재하지 않습니다."));
        if (!member.verifyPassword(passwordHasher, loginPw)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "비밀번호가 일치하지 않습니다.");
        }
        member.changePassword(currentPassword, newPassword, passwordHasher);
        memberRepository.save(member);
    }
}
