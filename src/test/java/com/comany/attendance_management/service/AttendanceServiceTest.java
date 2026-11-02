package com.comany.attendance_management.service;

import com.comany.attendance_management.dto.AttendanceResponse;
import com.comany.attendance_management.dto.CheckInRequest;
import com.comany.attendance_management.dto.CheckOutRequest;
import com.comany.attendance_management.entity.Attendance;
import com.comany.attendance_management.entity.User;
import com.comany.attendance_management.enums.AttendanceStatus;
import com.comany.attendance_management.enums.Role;
import com.comany.attendance_management.repository.AttendanceRepository;
import com.comany.attendance_management.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private UserRepository userRepository;

    private User sampleUser;
    private final ZoneId zoneId = ZoneId.of("Asia/Ho_Chi_Minh");

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .fullName("Nguyen Van A")
                .email("vana@example.com")
                .department("IT")
                .role(Role.EMPLOYEE)
                .build();
    }

    private AttendanceService createServiceWithFixedClock(LocalDateTime fixedTime) {
        Clock fixedClock = Clock.fixed(fixedTime.atZone(zoneId).toInstant(), zoneId);
        return new AttendanceServiceImpl(attendanceRepository, userRepository, fixedClock);
    }

    @Test
    @DisplayName("Kiểm thử check-in đúng giờ (trước 08:30) -> Trạng thái PRESENT")
    void checkIn_onTime_shouldMarkPresent() {
        // Arrange: 08:15 sáng
        LocalDateTime fixedTime = LocalDateTime.of(2026, 11, 2, 8, 15, 0);
        AttendanceService service = createServiceWithFixedClock(fixedTime);
        LocalDate today = fixedTime.toLocalDate();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(attendanceRepository.existsByUserIdAndWorkDate(1L, today)).thenReturn(false);
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
            Attendance att = invocation.getArgument(0);
            att.setId(100L);
            return att;
        });

        CheckInRequest request = CheckInRequest.builder()
                .userId(1L)
                .notes("Đến công ty bằng xe buýt")
                .build();

        // Act
        AttendanceResponse response = service.checkIn(request);

        // Assert
        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(1L, response.getUserId());
        assertEquals("Nguyen Van A", response.getUserName());
        assertEquals(AttendanceStatus.PRESENT, response.getStatus());
        assertEquals(today, response.getWorkDate());
        assertEquals(fixedTime, response.getCheckInTime());
        assertEquals("Đến công ty bằng xe buýt", response.getNotes());

        verify(attendanceRepository, times(1)).save(any(Attendance.class));
    }

    @Test
    @DisplayName("Kiểm thử check-in muộn (sau 08:30) -> Trạng thái LATE và tính số phút đi muộn")
    void checkIn_late_shouldMarkLateAndCalculateLateMinutes() {
        // Arrange: 08:45 sáng (muộn 15 phút so với mốc 08:30)
        LocalDateTime fixedTime = LocalDateTime.of(2026, 11, 2, 8, 45, 0);
        AttendanceService service = createServiceWithFixedClock(fixedTime);
        LocalDate today = fixedTime.toLocalDate();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(attendanceRepository.existsByUserIdAndWorkDate(1L, today)).thenReturn(false);
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
            Attendance att = invocation.getArgument(0);
            att.setId(101L);
            return att;
        });

        CheckInRequest request = CheckInRequest.builder()
                .userId(1L)
                .notes("Kẹt xe cầu Sài Gòn")
                .build();

        // Act
        AttendanceResponse response = service.checkIn(request);

        // Assert
        assertNotNull(response);
        assertEquals(AttendanceStatus.LATE, response.getStatus());
        assertNotNull(response.getNotes());
        assertTrue(response.getNotes().contains("Đi muộn 15 phút"));
        assertTrue(response.getNotes().contains("Kẹt xe cầu Sài Gòn"));

        verify(attendanceRepository, times(1)).save(any(Attendance.class));
    }

    @Test
    @DisplayName("Kiểm thử chặn check-in 2 lần trong cùng một ngày")
    void checkIn_alreadyCheckedIn_shouldThrowIllegalStateException() {
        // Arrange
        LocalDateTime fixedTime = LocalDateTime.of(2026, 11, 2, 9, 0, 0);
        AttendanceService service = createServiceWithFixedClock(fixedTime);
        LocalDate today = fixedTime.toLocalDate();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(attendanceRepository.existsByUserIdAndWorkDate(1L, today)).thenReturn(true);

        CheckInRequest request = CheckInRequest.builder()
                .userId(1L)
                .build();

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.checkIn(request));
        assertTrue(exception.getMessage().contains("đã check-in trong ngày hôm nay"));

        verify(attendanceRepository, never()).save(any(Attendance.class));
    }

    @Test
    @DisplayName("Kiểm thử check-out thành công -> Tính giờ làm và trừ 1 tiếng nghỉ trưa")
    void checkOut_success_shouldDeductLunchHour() {
        // Arrange: Check-in lúc 08:00 và Check-out lúc 17:00 (tổng 9 tiếng, làm qua trưa nên còn 8 tiếng)
        LocalDateTime checkInTime = LocalDateTime.of(2026, 11, 2, 8, 0, 0);
        LocalDateTime checkOutTime = LocalDateTime.of(2026, 11, 2, 17, 0, 0);
        AttendanceService service = createServiceWithFixedClock(checkOutTime);
        LocalDate today = checkOutTime.toLocalDate();

        Attendance existingAttendance = Attendance.builder()
                .id(100L)
                .user(sampleUser)
                .workDate(today)
                .checkInTime(checkInTime)
                .status(AttendanceStatus.PRESENT)
                .build();

        when(attendanceRepository.findByUserIdAndWorkDate(1L, today))
                .thenReturn(Optional.of(existingAttendance));
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CheckOutRequest request = CheckOutRequest.builder()
                .userId(1L)
                .notes("Hoàn thành công việc trong ngày")
                .build();

        // Act
        AttendanceResponse response = service.checkOut(request);

        // Assert
        assertNotNull(response);
        assertEquals(checkOutTime, response.getCheckOutTime());
        assertEquals(8.0, response.getWorkHours());
        assertTrue(response.getNotes().contains("Hoàn thành công việc trong ngày"));

        verify(attendanceRepository, times(1)).save(existingAttendance);
    }

    @Test
    @DisplayName("Kiểm thử chặn check-out khi chưa từng check-in trong ngày")
    void checkOut_withoutCheckIn_shouldThrowIllegalStateException() {
        // Arrange
        LocalDateTime checkOutTime = LocalDateTime.of(2026, 11, 2, 17, 0, 0);
        AttendanceService service = createServiceWithFixedClock(checkOutTime);
        LocalDate today = checkOutTime.toLocalDate();

        when(attendanceRepository.findByUserIdAndWorkDate(1L, today)).thenReturn(Optional.empty());

        CheckOutRequest request = CheckOutRequest.builder()
                .userId(1L)
                .build();

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.checkOut(request));
        assertTrue(exception.getMessage().contains("Chưa từng check-in trong ngày hôm nay"));

        verify(attendanceRepository, never()).save(any(Attendance.class));
    }
}

