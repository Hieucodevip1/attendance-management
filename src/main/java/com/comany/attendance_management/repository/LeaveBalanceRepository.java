package com.comany.attendance_management.repository;

import com.comany.attendance_management.entity.LeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    Optional<LeaveBalance> findByUserIdAndYear(Long userId, Integer year);

    List<LeaveBalance> findByUserId(Long userId);
}
