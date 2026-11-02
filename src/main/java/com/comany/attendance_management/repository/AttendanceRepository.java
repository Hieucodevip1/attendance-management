package com.comany.attendance_management.repository;

import com.comany.attendance_management.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByUserIdAndWorkDate(Long userId, LocalDate workDate);

    boolean existsByUserIdAndWorkDate(Long userId, LocalDate workDate);

    List<Attendance> findByUserId(Long userId);

    List<Attendance> findByWorkDate(LocalDate workDate);
}

