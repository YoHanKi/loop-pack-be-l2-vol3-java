package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MemberService {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,16}$"
    );

    private final MemberRepository memberRepository;
    private final PasswordHasher passwordHasher;

    @Transactional
    public MemberModel register(String memberId, String rawPassword, String email, String birthDate, String name, Gender gender) {
        validatePassword(rawPassword);
        validatePasswordNotContainsBirthDate(rawPassword, birthDate);
        validateGender(gender);

        if (memberRepository.existsByMemberId(new MemberId(memberId))) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 가입된 ID 입니다.");
        }

        String hashedPassword = passwordHasher.hash(rawPassword);
        MemberModel member = new MemberModel(memberId, hashedPassword, email, birthDate, name, gender);

        return memberRepository.save(member);
    }

    private void validateGender(Gender gender) {
        if (gender == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "성별은 필수입니다.");
        }
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null || !PASSWORD_PATTERN.matcher(rawPassword).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다.");
        }
    }

    private void validatePasswordNotContainsBirthDate(String rawPassword, String birthDate) {
        if (birthDate == null || birthDate.isBlank()) {
            return;
        }

        // yyyy-MM-dd 형식에서 다양한 형태의 생년월일 추출
        String cleanBirthDate = birthDate.replace("-", "");
        String monthDay = cleanBirthDate.substring(4);
        String year = cleanBirthDate.substring(0, 4);

        if (rawPassword.contains(cleanBirthDate) ||
            rawPassword.contains(monthDay) ||
            rawPassword.contains(year)) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }
}
