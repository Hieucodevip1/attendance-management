package com.comany.attendance_management.service;

import com.comany.attendance_management.dto.AttendanceResponse;
import com.comany.attendance_management.dto.CheckInRequest;
import com.comany.attendance_management.dto.CheckOutRequest;
import com.comany.attendance_management.entity.Attendance;
import com.comany.attendance_management.entity.User;
import com.comany.attendance_management.enums.AttendanceStatus;
import com.comany.attendance_management.repository.AttendanceRepository;
import com.comany.attendance_management.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@Transactional
public class AttendanceServiceImpl implements AttendanceService {

    private static final LocalTime LATE_THRESHOLD = LocalTime.of(8, 30);
    private static final LocalTime LUNCH_START = LocalTime.of(12, 0);
    private static final LocalTime LUNCH_END = LocalTime.of(13, 0);

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public AttendanceServiceImpl(AttendanceRepository attendanceRepository,
                                 UserRepository userRepository,
                                 @Autowired(required = false) Clock clock) {
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    @Override
    public AttendanceResponse checkIn(CheckInRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new IllegalArgumentException("UserId không được để trống!");
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên với ID: " + request.getUserId()));

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        LocalTime currentTime = now.toLocalTime();

        // Chặn nếu đã check-in trong ngày
        if (attendanceRepository.existsByUserIdAndWorkDate(user.getId(), today)) {
            throw new IllegalStateException("Nhân viên đã check-in trong ngày hôm nay: " + today);
        }

        AttendanceStatus status = AttendanceStatus.PRESENT;
        StringBuilder notesBuilder = new StringBuilder();

        // Nếu sau 08:30 sáng thì đánh dấu LATE và tính số phút đi muộn
        if (currentTime.isAfter(LATE_THRESHOLD)) {
            status = AttendanceStatus.LATE;
            long lateMinutes = Duration.between(LATE_THRESHOLD, currentTime).toMinutes();
            notesBuilder.append("Đi muộn ").append(lateMinutes).append(" phút");
        }

        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            if (!notesBuilder.isEmpty()) {
                notesBuilder.append(" - ");
            }
            notesBuilder.append(request.getNotes().trim());
        }

        Attendance attendance = Attendance.builder()
                .user(user)
                .workDate(today)
                .checkInTime(now)
                .status(status)
                .notes(notesBuilder.isEmpty() ? null : notesBuilder.toString())
                .build();

        Attendance savedAttendance = attendanceRepository.save(attendance);
        return AttendanceResponse.fromEntity(savedAttendance);
    }

    @Override
    public AttendanceResponse checkIn(Long userId) {
        return checkIn(CheckInRequest.builder().userId(userId).build());
    }

    @Override
    public AttendanceResponse checkOut(CheckOutRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new IllegalArgumentException("UserId không được để trống!");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        // Chặn nếu chưa từng check-in trong ngày
        Attendance attendance = attendanceRepository.findByUserIdAndWorkDate(request.getUserId(), today)
                .orElseThrow(() -> new IllegalStateException("Chưa từng check-in trong ngày hôm nay!"));

        if (attendance.getCheckInTime() == null) {
            throw new IllegalStateException("Chưa từng check-in trong ngày hôm nay!");
        }

        LocalDateTime checkInTime = attendance.getCheckInTime();
        LocalDateTime checkOutTime = now;
        attendance.setCheckOutTime(checkOutTime);

        // Tính tổng số giờ làm (trừ 1 tiếng nghỉ trưa nếu làm qua trưa)
        long totalMinutes = Duration.between(checkInTime, checkOutTime).toMinutes();

        boolean workedThroughLunch = !checkInTime.toLocalTime().isAfter(LUNCH_START) 
                && checkOutTime.toLocalTime().isAfter(LUNCH_END);
        if (workedThroughLunch) {
            totalMinutes = Math.max(0, totalMinutes - 60);
        }

        double workHours = Math.round((totalMinutes / 60.0) * 100.0) / 100.0;
        attendance.setWorkHours(workHours);

        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            String existingNotes = attendance.getNotes();
            if (existingNotes != null && !existingNotes.isBlank()) {
                attendance.setNotes(existingNotes + " | " + request.getNotes().trim());
            } else {
                attendance.setNotes(request.getNotes().trim());
            }
        }

        Attendance savedAttendance = attendanceRepository.save(attendance);
        return AttendanceResponse.fromEntity(savedAttendance);
    }

    @Override
    public AttendanceResponse checkOut(Long userId) {
        return checkOut(CheckOutRequest.builder().userId(userId).build());
    }
}

