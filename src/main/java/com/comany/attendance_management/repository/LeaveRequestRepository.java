package com.comany.attendance_management.repository;

import com.comany.attendance_management.entity.LeaveRequest;
import com.comany.attendance_management.enums.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByUserId(Long userId);

    List<LeaveRequest> findByStatus(LeaveStatus status);

    List<LeaveRequest> findByUserIdAndStatus(Long userId, LeaveStatus status);
}
