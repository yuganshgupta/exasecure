package com.examsystem.server;

import com.examsystem.application.ports.ExamAttemptRepository;
import com.examsystem.application.ports.ProctorRepository;
import com.examsystem.application.ports.ProctorRepository.ProctorEventRecord;
import com.examsystem.domain.models.ExamAttempt;
import com.examsystem.domain.models.User;
import com.examsystem.server.http.controllers.StudentExamController;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentExamDurationThresholdTest {

    private static final Instant BASE_TIME = Instant.parse("2026-09-05T12:00:00.000Z");
    private static final int ATTEMPT_ID = 1001;
    private static final int EVENT_ID = 2001;
    private static final int STUDENT_ID = 101;

    private Context createContext(int attemptId, int eventId, AtomicInteger statusHolder) {
        return (Context) Proxy.newProxyInstance(
                Context.class.getClassLoader(),
                new Class<?>[]{Context.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("pathParam".equals(name)) {
                        if ("attemptId".equals(args[0])) return String.valueOf(attemptId);
                        if ("eventId".equals(args[0])) return String.valueOf(eventId);
                    }
                    if ("attribute".equals(name) && "user".equals(args[0])) {
                        return new User(STUDENT_ID, "student1", "Student One", "EN101", "A", User.Role.STUDENT);
                    }
                    if ("status".equals(name) && args != null && args.length == 1 && args[0] instanceof Integer statusCode) {
                        statusHolder.set(statusCode);
                        return proxy;
                    }
                    return null;
                }
        );
    }

    private ExamAttemptRepository createAttemptRepository() {
        return new ExamAttemptRepository() {
            @Override
            public Optional<ExamAttempt> findById(int id) {
                if (id == ATTEMPT_ID) {
                    return Optional.of(new ExamAttempt(ATTEMPT_ID, 1, STUDENT_ID, BASE_TIME, null, null, false));
                }
                return Optional.empty();
            }

            @Override
            public void calculateAndSetScore(int attemptId, int focusLostCount, int score, Instant submittedAt) {}

            @Override
            public ExamAttempt startOrResumeAttempt(int studentId, int examId, Instant now, Duration timeLimit) {
                return null;
            }
        };
    }

    private ProctorRepository createProctorRepository(Instant recordedAt, Instant returnedAt, AtomicBoolean markedLongAbsence) {
        return new ProctorRepository() {
            @Override
            public int saveEvent(int attemptId, String eventType, Instant timestamp) { return EVENT_ID; }
            @Override
            public void saveEvidence(int eventId, byte[] screenshot, String contentType, Instant timestamp) {}
            @Override
            public boolean eventBelongsToAttempt(int eventId, int attemptId) {
                return eventId == EVENT_ID && attemptId == ATTEMPT_ID;
            }
            @Override
            public int countEvents(int attemptId, String eventType) { return 1; }
            @Override
            public Optional<ProctorEventRecord> findEventById(int eventId) {
                if (eventId == EVENT_ID) {
                    Long awayDuration = returnedAt != null
                            ? java.time.Duration.between(recordedAt, returnedAt).toMillis()
                            : null;
                    return Optional.of(new ProctorEventRecord(
                            EVENT_ID, ATTEMPT_ID, "FOCUS_LOST", recordedAt, returnedAt, awayDuration, false
                    ));
                }
                return Optional.empty();
            }
            @Override
            public void markLongAbsence(int eventId) {
                if (eventId == EVENT_ID) {
                    markedLongAbsence.set(true);
                }
            }
        };
    }

    @Test
    @DisplayName("4999 ms unreturned incident is rejected with BadRequestResponse (400)")
    void unreturnedIncident_4999ms_rejectedWithBadRequestResponse() {
        AtomicBoolean markedLongAbsence = new AtomicBoolean(false);
        AtomicInteger statusHolder = new AtomicInteger(0);

        Clock clock = Clock.fixed(BASE_TIME.plusMillis(4999), ZoneOffset.UTC);
        StudentExamController controller = new StudentExamController(
                null, null, null, null,
                createAttemptRepository(),
                createProctorRepository(BASE_TIME, null, markedLongAbsence),
                null, null, null, clock
        );

        Context ctx = createContext(ATTEMPT_ID, EVENT_ID, statusHolder);

        assertThatThrownBy(() -> controller.recordLongAbsence(ctx))
                .isInstanceOf(BadRequestResponse.class)
                .hasMessageContaining("4999ms")
                .hasMessageContaining("not existed for >= 5 seconds");

        assertThat(markedLongAbsence.get()).isFalse();
        assertThat(statusHolder.get()).isZero();
    }

    @Test
    @DisplayName("5000 ms unreturned incident is accepted and marked as long absence (200)")
    void unreturnedIncident_5000ms_accepted() {
        AtomicBoolean markedLongAbsence = new AtomicBoolean(false);
        AtomicInteger statusHolder = new AtomicInteger(0);

        Clock clock = Clock.fixed(BASE_TIME.plusMillis(5000), ZoneOffset.UTC);
        StudentExamController controller = new StudentExamController(
                null, null, null, null,
                createAttemptRepository(),
                createProctorRepository(BASE_TIME, null, markedLongAbsence),
                null, null, null, clock
        );

        Context ctx = createContext(ATTEMPT_ID, EVENT_ID, statusHolder);
        controller.recordLongAbsence(ctx);

        assertThat(statusHolder.get()).isEqualTo(200);
        assertThat(markedLongAbsence.get()).isTrue();
    }

    @Test
    @DisplayName("5001 ms unreturned incident is accepted and marked as long absence (200)")
    void unreturnedIncident_5001ms_accepted() {
        AtomicBoolean markedLongAbsence = new AtomicBoolean(false);
        AtomicInteger statusHolder = new AtomicInteger(0);

        Clock clock = Clock.fixed(BASE_TIME.plusMillis(5001), ZoneOffset.UTC);
        StudentExamController controller = new StudentExamController(
                null, null, null, null,
                createAttemptRepository(),
                createProctorRepository(BASE_TIME, null, markedLongAbsence),
                null, null, null, clock
        );

        Context ctx = createContext(ATTEMPT_ID, EVENT_ID, statusHolder);
        controller.recordLongAbsence(ctx);

        assertThat(statusHolder.get()).isEqualTo(200);
        assertThat(markedLongAbsence.get()).isTrue();
    }

    @Test
    @DisplayName("4999 ms returned incident is rejected with BadRequestResponse (400)")
    void returnedIncident_4999ms_rejectedWithBadRequestResponse() {
        AtomicBoolean markedLongAbsence = new AtomicBoolean(false);
        AtomicInteger statusHolder = new AtomicInteger(0);

        Instant returnedAt = BASE_TIME.plusMillis(4999);
        Clock clock = Clock.fixed(BASE_TIME.plusMillis(10000), ZoneOffset.UTC); // Clock is later, but returnedAt is authoritative
        StudentExamController controller = new StudentExamController(
                null, null, null, null,
                createAttemptRepository(),
                createProctorRepository(BASE_TIME, returnedAt, markedLongAbsence),
                null, null, null, clock
        );

        Context ctx = createContext(ATTEMPT_ID, EVENT_ID, statusHolder);

        assertThatThrownBy(() -> controller.recordLongAbsence(ctx))
                .isInstanceOf(BadRequestResponse.class)
                .hasMessageContaining("4999ms")
                .hasMessageContaining("not existed for >= 5 seconds");

        assertThat(markedLongAbsence.get()).isFalse();
    }

    @Test
    @DisplayName("5000 ms returned incident is accepted and marked as long absence (200)")
    void returnedIncident_5000ms_accepted() {
        AtomicBoolean markedLongAbsence = new AtomicBoolean(false);
        AtomicInteger statusHolder = new AtomicInteger(0);

        Instant returnedAt = BASE_TIME.plusMillis(5000);
        Clock clock = Clock.fixed(BASE_TIME.plusMillis(10000), ZoneOffset.UTC);
        StudentExamController controller = new StudentExamController(
                null, null, null, null,
                createAttemptRepository(),
                createProctorRepository(BASE_TIME, returnedAt, markedLongAbsence),
                null, null, null, clock
        );

        Context ctx = createContext(ATTEMPT_ID, EVENT_ID, statusHolder);
        controller.recordLongAbsence(ctx);

        assertThat(statusHolder.get()).isEqualTo(200);
        assertThat(markedLongAbsence.get()).isTrue();
    }

    @Test
    @DisplayName("5001 ms returned incident is accepted and marked as long absence (200)")
    void returnedIncident_5001ms_accepted() {
        AtomicBoolean markedLongAbsence = new AtomicBoolean(false);
        AtomicInteger statusHolder = new AtomicInteger(0);

        Instant returnedAt = BASE_TIME.plusMillis(5001);
        Clock clock = Clock.fixed(BASE_TIME.plusMillis(10000), ZoneOffset.UTC);
        StudentExamController controller = new StudentExamController(
                null, null, null, null,
                createAttemptRepository(),
                createProctorRepository(BASE_TIME, returnedAt, markedLongAbsence),
                null, null, null, clock
        );

        Context ctx = createContext(ATTEMPT_ID, EVENT_ID, statusHolder);
        controller.recordLongAbsence(ctx);

        assertThat(statusHolder.get()).isEqualTo(200);
        assertThat(markedLongAbsence.get()).isTrue();
    }
}
