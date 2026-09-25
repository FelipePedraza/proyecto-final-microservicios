package com.example.vacaciones.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.vacaciones.domain.Vacation;

public interface VacationRepository extends JpaRepository<Vacation, String> {

    @Query(value = "select nextval('vacaciones_id_seq')", nativeQuery = true)
    long nextNumber();

    @Query(
            "select v from Vacation v " +
                    "where v.employeeId = :employeeId " +
                    "and v.status in ('PROGRAMADA','EN_CURSO') " +
                    "and v.startDate <= :endDate " +
                    "and v.endDate >= :startDate"
    )
    List<Vacation> findActiveOverlaps(
            @Param("employeeId") String employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    List<Vacation> findByEmployeeIdOrderByStartDateAsc(String employeeId);
}
