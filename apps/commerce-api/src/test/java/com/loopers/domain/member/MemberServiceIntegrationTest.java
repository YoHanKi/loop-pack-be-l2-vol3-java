package com.loopers.domain.member;

import com.loopers.infrastructure.member.MemberJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class MemberServiceIntegrationTest {
    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberJpaRepository memberJpaRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private MemberRepository spyMemberRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        Mockito.reset(spyMemberRepository);
    }

    @DisplayName("회원 가입 시,")
    @Nested
    class Post {
        private static final String VALID_MEMBER_ID = "testuser1";
        private static final String VALID_PASSWORD = "Test1234!";
        private static final String VALID_EMAIL = "test@example.com";
        private static final String VALID_BIRTH_DATE = "1995-05-20";
        private static final String VALID_NAME = "테스트유저";
        private static final Gender VALID_GENDER = Gender.MALE;

        @DisplayName("비밀번호는 암호화해야되며, 8~16자의 영문 대소문자, 숫자, 특수문자만 가능하다.")
        @Test
        void testPasswordValidation() {
            // arrange
            String invalidPasswordTooShort = "Test1!";
            String invalidPasswordNoUpperCase = "test1234!";
            String invalidPasswordNoLowerCase = "TEST1234!";
            String invalidPasswordNoDigit = "TestTest!";
            String invalidPasswordNoSpecialChar = "Test1234";

            String expectedMessage = "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다.";

            // act & assert - 너무 짧은 비밀번호
            CoreException shortPasswordException = assertThrows(CoreException.class,
                () -> memberService.register(VALID_MEMBER_ID, invalidPasswordTooShort, VALID_EMAIL, VALID_BIRTH_DATE, VALID_NAME, VALID_GENDER));
            assertAll(
                () -> assertThat(shortPasswordException.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(shortPasswordException.getMessage()).isEqualTo(expectedMessage)
            );

            // act & assert - 대문자 없는 비밀번호
            CoreException noUpperCaseException = assertThrows(CoreException.class,
                () -> memberService.register(VALID_MEMBER_ID, invalidPasswordNoUpperCase, VALID_EMAIL, VALID_BIRTH_DATE, VALID_NAME, VALID_GENDER));
            assertAll(
                () -> assertThat(noUpperCaseException.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(noUpperCaseException.getMessage()).isEqualTo(expectedMessage)
            );

            // act & assert - 소문자 없는 비밀번호
            CoreException noLowerCaseException = assertThrows(CoreException.class,
                () -> memberService.register(VALID_MEMBER_ID, invalidPasswordNoLowerCase, VALID_EMAIL, VALID_BIRTH_DATE, VALID_NAME, VALID_GENDER));
            assertAll(
                () -> assertThat(noLowerCaseException.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(noLowerCaseException.getMessage()).isEqualTo(expectedMessage)
            );

            // act & assert - 숫자 없는 비밀번호
            CoreException noDigitException = assertThrows(CoreException.class,
                () -> memberService.register(VALID_MEMBER_ID, invalidPasswordNoDigit, VALID_EMAIL, VALID_BIRTH_DATE, VALID_NAME, VALID_GENDER));
            assertAll(
                () -> assertThat(noDigitException.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(noDigitException.getMessage()).isEqualTo(expectedMessage)
            );

            // act & assert - 특수문자 없는 비밀번호
            CoreException noSpecialCharException = assertThrows(CoreException.class,
                () -> memberService.register(VALID_MEMBER_ID, invalidPasswordNoSpecialChar, VALID_EMAIL, VALID_BIRTH_DATE, VALID_NAME, VALID_GENDER));
            assertAll(
                () -> assertThat(noSpecialCharException.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(noSpecialCharException.getMessage()).isEqualTo(expectedMessage)
            );
        }

        @DisplayName("생년월일은 비밀번호 내에 포함될 수 없다.")
        @Test
        void testBirthDateValidation() {
            // arrange
            String birthDate = "2000-01-15";
            String passwordWithFullBirthDate = "Pass20000115!";
            String passwordWithYearMonthDay = "Pass0115Test!";
            String passwordWithYear = "Pass2000Test!";

            String expectedMessage = "비밀번호에 생년월일을 포함할 수 없습니다.";

            // act & assert - 생년월일 전체 포함 (yyyyMMdd)
            CoreException fullBirthDateException = assertThrows(CoreException.class,
                () -> memberService.register(VALID_MEMBER_ID, passwordWithFullBirthDate, VALID_EMAIL, birthDate, VALID_NAME, VALID_GENDER));
            assertAll(
                () -> assertThat(fullBirthDateException.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(fullBirthDateException.getMessage()).isEqualTo(expectedMessage)
            );

            // act & assert - 월일 포함 (MMdd)
            CoreException monthDayException = assertThrows(CoreException.class,
                () -> memberService.register(VALID_MEMBER_ID, passwordWithYearMonthDay, VALID_EMAIL, birthDate, VALID_NAME, VALID_GENDER));
            assertAll(
                () -> assertThat(monthDayException.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(monthDayException.getMessage()).isEqualTo(expectedMessage)
            );

            // act & assert - 년도 포함 (yyyy)
            CoreException yearException = assertThrows(CoreException.class,
                () -> memberService.register(VALID_MEMBER_ID, passwordWithYear, VALID_EMAIL, birthDate, VALID_NAME, VALID_GENDER));
            assertAll(
                () -> assertThat(yearException.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(yearException.getMessage()).isEqualTo(expectedMessage)
            );
        }

        @DisplayName("유저 저장이 정상적으로 이루어진다.")
        @Test
        void testUserSave() {
            // arrange & act
            MemberModel savedMember = memberService.register(VALID_MEMBER_ID, VALID_PASSWORD, VALID_EMAIL, VALID_BIRTH_DATE, VALID_NAME, VALID_GENDER);

            // assert - spy 객체를 통해 save 메서드 호출 검증
            verify(spyMemberRepository, times(1)).save(any(MemberModel.class));

            // assert - 저장된 회원 정보 검증
            assertAll(
                () -> assertThat(savedMember).isNotNull(),
                () -> assertThat(savedMember.getId()).isNotNull(),
                () -> assertThat(savedMember.getMemberId().value()).isEqualTo(VALID_MEMBER_ID),
                () -> assertThat(savedMember.getEmail().address()).isEqualTo(VALID_EMAIL),
                () -> assertThat(savedMember.getBirthDate().asString()).isEqualTo(VALID_BIRTH_DATE),
                () -> assertThat(savedMember.getName().value()).isEqualTo(VALID_NAME),
                () -> assertThat(savedMember.getGender()).isEqualTo(VALID_GENDER),
                // 비밀번호가 암호화되어 저장되었는지 검증
                () -> assertThat(savedMember.getPassword()).isNotEqualTo(VALID_PASSWORD),
                () -> assertThat(passwordHasher.matches(VALID_PASSWORD, savedMember.getPassword())).isTrue()
            );

            // DB에서 직접 조회하여 검증
            MemberModel foundMember = memberJpaRepository.findById(savedMember.getId()).orElseThrow();
            assertAll(
                () -> assertThat(foundMember.getMemberId().value()).isEqualTo(VALID_MEMBER_ID),
                () -> assertThat(foundMember.getEmail().address()).isEqualTo(VALID_EMAIL),
                () -> assertThat(passwordHasher.matches(VALID_PASSWORD, foundMember.getPassword())).isTrue()
            );
        }

        @DisplayName("이미 가입된 ID 로 회원가입 시도 시, 실패한다.")
        @Test
        void testDuplicateMemberId() {
            // arrange
            memberService.register(VALID_MEMBER_ID, VALID_PASSWORD, VALID_EMAIL, VALID_BIRTH_DATE, VALID_NAME, VALID_GENDER);

            // act
            CoreException exception = assertThrows(CoreException.class,
                () -> memberService.register(VALID_MEMBER_ID, VALID_PASSWORD, VALID_EMAIL, VALID_BIRTH_DATE, VALID_NAME, VALID_GENDER));
            assertAll(
                () -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(exception.getMessage()).isEqualTo("이미 가입된 ID 입니다.")
            );
        }
    }


    @TestConfiguration
    static class SpyConfig {
        @Bean
        @Primary
        public MemberRepository spyMemberRepository(MemberJpaRepository memberJpaRepository) {
            return Mockito.spy(new MemberRepository() {
                @Override
                public MemberModel save(MemberModel member) {
                    return memberJpaRepository.save(member);
                }

                @Override
                public boolean existsByMemberId(MemberId memberId) {
                    return memberJpaRepository.existsByMemberId(memberId);
                }
            });
        }
    }
}
