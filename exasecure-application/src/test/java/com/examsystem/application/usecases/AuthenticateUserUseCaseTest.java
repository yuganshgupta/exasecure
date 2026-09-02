package com.examsystem.application.usecases;

import com.examsystem.application.ports.PasswordEncoder;
import com.examsystem.application.ports.UserRepository;
import com.examsystem.domain.models.User;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticateUserUseCaseTest {

    static class StubUserRepository implements UserRepository {
        @Override
        public Optional<User> findByUsername(String username) {
            if ("student1".equals(username)) {
                return Optional.of(new User(1, "student1", "Student One", "E1", "A", User.Role.STUDENT));
            }
            return Optional.empty();
        }

        @Override
        public Optional<String> getPasswordHash(String username) {
            if ("student1".equals(username)) return Optional.of("hashed123");
            return Optional.empty();
        }
    }

    static class StubPasswordEncoder implements PasswordEncoder {
        @Override
        public String encode(String rawPassword) {
            return "encoded:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            return "secret".equals(rawPassword) && "hashed123".equals(encodedPassword);
        }
    }

    @Test
    void authenticate_validCredentials_returnsUser() {
        AuthenticateUserUseCase useCase = new AuthenticateUserUseCase(new StubUserRepository(), new StubPasswordEncoder());
        Optional<User> result = useCase.authenticate("student1", "secret");
        
        assertThat(result).isPresent();
        assertThat(result.get().role()).isEqualTo(User.Role.STUDENT);
    }

    @Test
    void authenticate_invalidPassword_returnsEmpty() {
        AuthenticateUserUseCase useCase = new AuthenticateUserUseCase(new StubUserRepository(), new StubPasswordEncoder());
        Optional<User> result = useCase.authenticate("student1", "wrong");
        
        assertThat(result).isEmpty();
    }

    @Test
    void authenticate_unknownUser_returnsEmpty() {
        AuthenticateUserUseCase useCase = new AuthenticateUserUseCase(new StubUserRepository(), new StubPasswordEncoder());
        Optional<User> result = useCase.authenticate("nobody", "secret");
        
        assertThat(result).isEmpty();
    }
}
