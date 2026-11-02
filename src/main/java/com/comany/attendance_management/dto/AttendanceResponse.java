package com.comany.attendance_management.dto;

import com.comany.attendance_management.entity.Attendance;
import com.comany.attendance_management.enums.AttendanceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceResponse {
    private Long id;
    private Long userId;
    private String userName;
    private LocalDate workDate;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private Double workHours;
    private AttendanceStatus status;
    private String notes;

    public static AttendanceResponse fromEntity(Attendance attendance) {
        if (attendance == null) {
            return null;
        }
        return AttendanceResponse.builder()
                .id(attendance.getId())
                .userId(attendance.getUser() != null ? attendance.getUser().getId() : null)
                .userName(attendance.getUser() != null ? attendance.getUser().getFullName() : null)
                .workDate(attendance.getWorkDate())
                .checkInTime(attendance.getCheckInTime())
                .checkOutTime(attendance.getCheckOutTime())
                .workHours(attendance.getWorkHours())
                .status(attendance.getStatus())
                .notes(attendance.getNotes())
                .build();
    }
}

