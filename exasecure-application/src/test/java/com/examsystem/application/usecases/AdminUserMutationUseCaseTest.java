package com.examsystem.application.usecases;

import com.examsystem.application.commands.AdminCreateUserCommand;
import com.examsystem.application.commands.AdminUpdateUserCommand;
import com.examsystem.application.ports.AdminUserMutationPort;
import com.examsystem.application.ports.PasswordEncoder;
import com.examsystem.domain.models.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AdminUserMutationUseCaseTest {
    private final RecordingMutationPort port = new RecordingMutationPort();
    private final AdminUserMutationUseCase useCase = new AdminUserMutationUseCase(port, new StubEncoder());

    @Test
    void createUserNormalizesValuesAndPassesOnlyEncodedPasswordToPort() {
        int id = useCase.createUser(new AdminCreateUserCommand(
                " student3 ", "long-enough", " Student Three ", " E003 ", " B ", User.Role.STUDENT));

        assertThat(id).isEqualTo(41);
        assertThat(port.username).isEqualTo("student3");
        assertThat(port.encodedPassword).isEqualTo("encoded:long-enough");
        assertThat(port.fullName).isEqualTo("Student Three");
        assertThat(port.enrollmentNumber).isEqualTo("E003");
        assertThat(port.section).isEqualTo("B");
        assertThat(port.role).isEqualTo(User.Role.STUDENT);
    }

    @Test
    void updateWithBlankPasswordPreservesExistingHash() {
        boolean updated = useCase.updateUser(new AdminUpdateUserCommand(
                41, "student4", " ", "Student Four", null, null, User.Role.ADMIN));

        assertThat(updated).isTrue();
        assertThat(port.encodedPassword).isNull();
        assertThat(port.role).isEqualTo(User.Role.ADMIN);
    }

    @Test
    void rejectsInvalidInputBeforePersistence() {
        assertThatIllegalArgumentException().isThrownBy(() -> useCase.createUser(
                new AdminCreateUserCommand("user", "short", "Name", null, null, User.Role.STUDENT)));
        assertThatIllegalArgumentException().isThrownBy(() -> useCase.updateUser(
                new AdminUpdateUserCommand(0, "user", null, "Name", null, null, User.Role.STUDENT)));
        assertThat(port.calls).isZero();
    }

    @Test
    void deletesUserWhenIdValid() {
        assertThat(useCase.deleteUser(41)).isTrue();
        assertThat(port.calls).isEqualTo(1);
        assertThatIllegalArgumentException().isThrownBy(() -> useCase.deleteUser(0));
    }

    private static final class StubEncoder implements PasswordEncoder {
        @Override
        public String encode(String rawPassword) {
            return "encoded:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            return false;
        }
    }

    private static final class RecordingMutationPort implements AdminUserMutationPort {
        int calls;
        String username;
        String encodedPassword;
        String fullName;
        String enrollmentNumber;
        String section;
        User.Role role;

        @Override
        public int createUser(
                String username,
                String encodedPassword,
                String fullName,
                String enrollmentNumber,
                String section,
                User.Role role) {
            record(username, encodedPassword, fullName, enrollmentNumber, section, role);
            return 41;
        }

        @Override
        public boolean updateUser(
                int id,
                String username,
                String encodedPassword,
                String fullName,
                String enrollmentNumber,
                String section,
                User.Role role) {
            record(username, encodedPassword, fullName, enrollmentNumber, section, role);
            return id == 41;
        }

        @Override
        public boolean deactivateUser(int id) {
            calls++;
            return id == 41;
        }

        @Override
        public boolean reactivateUser(int id) {
            calls++;
            return id == 41;
        }

        @Override
        public boolean deleteUser(int id) {
            calls++;
            return id == 41;
        }

        private void record(
                String username,
                String encodedPassword,
                String fullName,
                String enrollmentNumber,
                String section,
                User.Role role) {
            calls++;
            this.username = username;
            this.encodedPassword = encodedPassword;
            this.fullName = fullName;
            this.enrollmentNumber = enrollmentNumber;
            this.section = section;
            this.role = role;
        }
    }
}
