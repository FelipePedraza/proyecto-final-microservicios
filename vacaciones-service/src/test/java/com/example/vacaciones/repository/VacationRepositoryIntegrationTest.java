package com.example.vacaciones.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import com.example.vacaciones.domain.Vacation;
import com.example.vacaciones.domain.VacationStatus;
import com.example.vacaciones.dto.VacationRequest;
import com.example.vacaciones.dto.VacationResponse;
import com.example.vacaciones.exception.VacationConflictException;
import com.example.vacaciones.messaging.EmployeeReplica;
import com.example.vacaciones.messaging.EmployeeReplicaRepository;
import com.example.vacaciones.service.VacationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * Integración contra un PostgreSQL 17 real (Testcontainers) inicializado con el mismo script que usa
 * docker-compose ({@code database/vacaciones/001-schema.sql}), de modo que también se ejercita la
 * restricción {@code EXCLUDE USING gist} que impide solapamientos a nivel de base de datos.
 * RabbitMQ no participa: el {@link RabbitTemplate} está simulado y los listeners no arrancan.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "vacaciones.scheduler.cron=-"
})
class VacationRepositoryIntegrationTest {

    private static final Path SCHEMA = Path.of("..", "database", "vacaciones", "001-schema.sql");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(SCHEMA),
                    "/docker-entrypoint-initdb.d/001-schema.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private VacationRepository repository;
    @Autowired
    private EmployeeReplicaRepository employees;
    @Autowired
    private VacationService service;

    private static final LocalDate BASE = LocalDate.now(ZoneOffset.UTC).plusDays(30);

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
        employees.deleteAll();
    }

    // ---- findActiveOverlaps: semántica real del solapamiento ----------------------------------------

    @Test
    void detectsPartialOverlapOnEitherSide() {
        save("V-1", "E001", BASE.plusDays(5), BASE.plusDays(10), VacationStatus.PROGRAMADA);

        assertEquals(1, overlaps("E001", BASE.plusDays(8), BASE.plusDays(15)).size());
        assertEquals(1, overlaps("E001", BASE.plusDays(1), BASE.plusDays(6)).size());
    }

    @Test
    void detectsContainedAndContainingRanges() {
        save("V-1", "E001", BASE.plusDays(5), BASE.plusDays(15), VacationStatus.PROGRAMADA);

        assertEquals(1, overlaps("E001", BASE.plusDays(7), BASE.plusDays(9)).size(), "contenido");
        assertEquals(1, overlaps("E001", BASE, BASE.plusDays(30)).size(), "que lo contiene");
    }

    @Test
    void sharedBoundaryDayCountsAsOverlapBecauseDatesAreInclusive() {
        save("V-1", "E001", BASE.plusDays(5), BASE.plusDays(10), VacationStatus.PROGRAMADA);

        assertEquals(1, overlaps("E001", BASE.plusDays(10), BASE.plusDays(12)).size(), "fin = inicio");
        assertEquals(1, overlaps("E001", BASE.plusDays(1), BASE.plusDays(5)).size(), "inicio = fin");
        assertEquals(1, overlaps("E001", BASE.plusDays(5), BASE.plusDays(5)).size(), "mismo día");
    }

    @Test
    void consecutiveDaysWithoutSharedDayDoNotOverlap() {
        save("V-1", "E001", BASE.plusDays(5), BASE.plusDays(10), VacationStatus.PROGRAMADA);

        assertTrue(overlaps("E001", BASE.plusDays(11), BASE.plusDays(15)).isEmpty(), "día siguiente al fin");
        assertTrue(overlaps("E001", BASE, BASE.plusDays(4)).isEmpty(), "día anterior al inicio");
    }

    @Test
    void ignoresOtherEmployees() {
        save("V-1", "E001", BASE.plusDays(5), BASE.plusDays(10), VacationStatus.PROGRAMADA);

        assertTrue(overlaps("E002", BASE.plusDays(5), BASE.plusDays(10)).isEmpty());
    }

    @Test
    void onlyProgrammedAndInProgressVacationsBlockNewOnes() {
        save("V-PROG", "E001", BASE, BASE.plusDays(2), VacationStatus.PROGRAMADA);
        save("V-CURSO", "E001", BASE.plusDays(10), BASE.plusDays(12), VacationStatus.EN_CURSO);
        save("V-FIN", "E001", BASE.plusDays(20), BASE.plusDays(22), VacationStatus.FINALIZADA);
        save("V-CANC", "E001", BASE.plusDays(30), BASE.plusDays(32), VacationStatus.CANCELADA);

        assertEquals(1, overlaps("E001", BASE, BASE.plusDays(2)).size());
        assertEquals(1, overlaps("E001", BASE.plusDays(10), BASE.plusDays(12)).size());
        assertTrue(overlaps("E001", BASE.plusDays(20), BASE.plusDays(22)).isEmpty(), "FINALIZADA no bloquea");
        assertTrue(overlaps("E001", BASE.plusDays(30), BASE.plusDays(32)).isEmpty(), "CANCELADA no bloquea");
    }

    // ---- secuencia e identificadores -----------------------------------------------------------------

    @Test
    void nextNumberIsStrictlyIncreasing() {
        long first = repository.nextNumber();
        long second = repository.nextNumber();

        assertEquals(first + 1, second);
    }

    // ---- consultas del scheduler y por empleado -------------------------------------------------------

    @Test
    void findsVacationsDueToStartAndDueToFinish() {
        LocalDate today = LocalDate.of(2026, 10, 5);
        save("V-HOY", "E001", today, today.plusDays(3), VacationStatus.PROGRAMADA);
        save("V-FUTURA", "E002", today.plusDays(1), today.plusDays(3), VacationStatus.PROGRAMADA);
        save("V-VENCIDA", "E003", today.minusDays(5), today.minusDays(1), VacationStatus.EN_CURSO);
        save("V-ULTIMO-DIA", "E004", today.minusDays(5), today, VacationStatus.EN_CURSO);

        assertEquals(List.of("V-HOY"), ids(repository.findByStatusAndStartDateLessThanEqual(
                VacationStatus.PROGRAMADA, today)));
        assertEquals(List.of("V-VENCIDA"), ids(repository.findByStatusAndEndDateBefore(
                VacationStatus.EN_CURSO, today)), "el último día todavía cuenta como EN_CURSO");
    }

    @Test
    void listsAnEmployeesVacationsOrderedByStartDate() {
        save("V-B", "E001", BASE.plusDays(20), BASE.plusDays(22), VacationStatus.PROGRAMADA);
        save("V-A", "E001", BASE, BASE.plusDays(2), VacationStatus.PROGRAMADA);
        save("V-OTRO", "E002", BASE, BASE.plusDays(2), VacationStatus.PROGRAMADA);

        assertEquals(List.of("V-A", "V-B"), ids(repository.findByEmployeeIdOrderByStartDateAsc("E001")));
        assertEquals(List.of("V-A", "V-B"),
                ids(repository.findByEmployeeIdAndStatus("E001", VacationStatus.PROGRAMADA)).stream().sorted().toList());
    }

    // ---- restricción de la base de datos (última línea de defensa) --------------------------------------

    @Test
    void databaseRejectsOverlappingActiveVacationsEvenIfTheApplicationDoesNot() {
        save("V-1", "E001", BASE, BASE.plusDays(5), VacationStatus.PROGRAMADA);

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(vacation("V-2", "E001", BASE.plusDays(3), BASE.plusDays(8),
                        VacationStatus.PROGRAMADA)));
    }

    @Test
    void databaseAllowsOverlappingWhenTheExistingVacationIsCancelled() {
        save("V-1", "E001", BASE, BASE.plusDays(5), VacationStatus.CANCELADA);

        repository.saveAndFlush(vacation("V-2", "E001", BASE.plusDays(3), BASE.plusDays(8),
                VacationStatus.PROGRAMADA));

        assertEquals(2, repository.count());
    }

    // ---- VacationService sobre la base real ---------------------------------------------------------------

    @Test
    void serviceCreatesAndPersistsVacationAndRejectsAnOverlappingOne() {
        employees.save(new EmployeeReplica("E001", "Ana", "ana@empresa.com", "ACTIVO", Instant.now()));

        VacationResponse created = service.create(new VacationRequest("E001", BASE, BASE.plusDays(6)));

        assertTrue(created.id().startsWith("V-" + LocalDate.now(ZoneOffset.UTC).getYear() + "-"));
        assertEquals(VacationStatus.PROGRAMADA, repository.findById(created.id()).orElseThrow().getStatus());
        verify(rabbitTemplate).convertAndSend(eq("empleados_exchange"), eq(""), org.mockito.ArgumentMatchers.<Object>any());

        assertThrows(VacationConflictException.class,
                () -> service.create(new VacationRequest("E001", BASE.plusDays(6), BASE.plusDays(9))));
        assertEquals(1, repository.count());
    }

    @Test
    void serviceAllowsABookingRightAfterAnotherEnds() {
        employees.save(new EmployeeReplica("E001", "Ana", "ana@empresa.com", "ACTIVO", Instant.now()));
        service.create(new VacationRequest("E001", BASE, BASE.plusDays(6)));

        service.create(new VacationRequest("E001", BASE.plusDays(7), BASE.plusDays(10)));

        assertEquals(2, repository.count());
    }

    // ---- utilidades ---------------------------------------------------------------------------------------

    private List<Vacation> overlaps(String employeeId, LocalDate start, LocalDate end) {
        return repository.findActiveOverlaps(employeeId, start, end);
    }

    private static List<String> ids(List<Vacation> vacations) {
        return vacations.stream().map(Vacation::getId).toList();
    }

    private void save(String id, String employeeId, LocalDate start, LocalDate end, VacationStatus status) {
        repository.saveAndFlush(vacation(id, employeeId, start, end, status));
    }

    private static Vacation vacation(String id, String employeeId, LocalDate start, LocalDate end,
                                     VacationStatus status) {
        Vacation vacation = new Vacation(id, employeeId, start, end, Instant.now());
        switch (status) {
            case EN_CURSO -> vacation.start();
            case FINALIZADA -> {
                vacation.start();
                vacation.finish();
            }
            case CANCELADA -> vacation.cancel();
            case PROGRAMADA -> {
            }
        }
        return vacation;
    }
}
