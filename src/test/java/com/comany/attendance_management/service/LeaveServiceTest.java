package com.comany.attendance_management.service;

import com.comany.attendance_management.dto.LeaveResponse;
import com.comany.attendance_management.dto.SubmitLeaveRequest;
import com.comany.attendance_management.entity.Attendance;
import com.comany.attendance_management.entity.LeaveBalance;
import com.comany.attendance_management.entity.LeaveRequest;
import com.comany.attendance_management.entity.User;
import com.comany.attendance_management.enums.AttendanceStatus;
import com.comany.attendance_management.enums.LeaveStatus;
import com.comany.attendance_management.enums.LeaveType;
import com.comany.attendance_management.enums.Role;
import com.comany.attendance_management.repository.AttendanceRepository;
import com.comany.attendance_management.repository.LeaveBalanceRepository;
import com.comany.attendance_management.repository.LeaveRequestRepository;
import com.comany.attendance_management.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private LeaveServiceImpl leaveService;

    private User employee;
    private User manager;
    private LeaveBalance leaveBalance;

    @BeforeEach
    void setUp() {
        employee = User.builder()
                .id(1L)
                .fullName("Nguyen Van A")
                .email("vana@example.com")
                .role(Role.EMPLOYEE)
                .build();

        manager = User.builder()
                .id(2L)
                .fullName("Tran Van Quan Ly")
                .email("manager@example.com")
                .role(Role.MANAGER)
                .build();

        leaveBalance = LeaveBalance.builder()
                .id(10L)
                .user(employee)
                .year(2026)
                .totalDays(12.0)
                .usedDays(10.0)
                .remainingDays(2.0) // Chỉ còn 2 ngày phép
                .build();
    }

    @Test
    @DisplayName("Kiểm thử nộp đơn phép năm vượt quá số ngày phép còn lại -> Ném IllegalArgumentException")
    void submitLeaveRequest_exceedRemainingDays_shouldThrowIllegalArgumentException() {
        // Arrange: Đăng ký nghỉ từ Thứ Hai đến Thứ Năm (4 ngày làm việc), trong khi quỹ chỉ còn 2 ngày
        LocalDate monday = LocalDate.of(2026, 11, 2);   // Thứ 2
        LocalDate thursday = LocalDate.of(2026, 11, 5); // Thứ 5

        SubmitLeaveRequest request = SubmitLeaveRequest.builder()
                .userId(1L)
                .leaveType(LeaveType.ANNUAL)
                .startDate(monday)
                .endDate(thursday)
                .reason("Đi du lịch nghỉ dưỡng")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveBalanceRepository.findByUserIdAndYear(1L, 2026)).thenReturn(Optional.of(leaveBalance));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> leaveService.submitLeaveRequest(1L, request)
        );

        assertTrue(exception.getMessage().contains("vượt quá số ngày phép còn lại"));
        verify(leaveRequestRepository, never()).save(any(LeaveRequest.class));
    }

    @Test
    @DisplayName("Kiểm thử nộp đơn thành công và tự động loại trừ Thứ 7, Chủ nhật khỏi số ngày tính")
    void submitLeaveRequest_success_shouldExcludeWeekendsAndSavePending() {
        // Arrange: Từ Thứ Sáu (06/11/2026) đến Thứ Ba (10/11/2026) -> Thứ 6, T7, CN, T2, T3 -> Đúng 3 ngày làm việc
        LocalDate friday = LocalDate.of(2026, 11, 6);
        LocalDate tuesday = LocalDate.of(2026, 11, 10);

        leaveBalance.setRemainingDays(5.0); // Đủ ngày

        SubmitLeaveRequest request = SubmitLeaveRequest.builder()
                .userId(1L)
                .leaveType(LeaveType.ANNUAL)
                .startDate(friday)
                .endDate(tuesday)
                .reason("Giải quyết việc gia đình")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveBalanceRepository.findByUserIdAndYear(1L, 2026)).thenReturn(Optional.of(leaveBalance));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest req = invocation.getArgument(0);
            req.setId(100L);
            return req;
        });

        // Act
        LeaveResponse response = leaveService.submitLeaveRequest(1L, request);

        // Assert
        assertNotNull(response);
        assertEquals(3.0, response.getTotalDays()); // Loại trừ Thứ 7 và Chủ nhật
        assertEquals(LeaveStatus.PENDING, response.getStatus());
        assertEquals("Giải quyết việc gia đình", response.getReason());

        verify(leaveRequestRepository, times(1)).save(any(LeaveRequest.class));
    }

    @Test
    @DisplayName("Kiểm thử duyệt đơn nghỉ phép -> Trừ quỹ phép và tạo bản ghi Attendance với trạng thái ON_LEAVE")
    void approveLeaveRequest_success_shouldDeductBalanceAndCreateAttendanceRecords() {
        // Arrange: Đơn nghỉ 2 ngày (Thứ 2, Thứ 3)
        LocalDate monday = LocalDate.of(2026, 11, 2);
        LocalDate tuesday = LocalDate.of(2026, 11, 3);

        LeaveRequest pendingRequest = LeaveRequest.builder()
                .id(200L)
                .user(employee)
                .leaveType(LeaveType.ANNUAL)
                .startDate(monday)
                .endDate(tuesday)
                .totalDays(2.0)
                .status(LeaveStatus.PENDING)
                .build();

        leaveBalance.setTotalDays(12.0);
        leaveBalance.setUsedDays(4.0);
        leaveBalance.setRemainingDays(8.0);

        when(leaveRequestRepository.findById(200L)).thenReturn(Optional.of(pendingRequest));
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(leaveBalanceRepository.findByUserIdAndYear(1L, 2026)).thenReturn(Optional.of(leaveBalance));
        when(attendanceRepository.findByUserIdAndWorkDate(anyLong(), any(LocalDate.class))).thenReturn(Optional.empty());
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        LeaveResponse response = leaveService.approveLeaveRequest(200L, 2L);

        // Assert
        assertNotNull(response);
        assertEquals(LeaveStatus.APPROVED, response.getStatus());
        assertEquals(2L, response.getApproverId());

        // Kiểm tra quỹ phép bị trừ 2 ngày
        assertEquals(6.0, leaveBalance.getUsedDays());
        assertEquals(6.0, leaveBalance.getRemainingDays());
        verify(leaveBalanceRepository, times(1)).save(leaveBalance);

        // Kiểm tra tạo bản ghi Attendance cho 2 ngày làm việc với status ON_LEAVE
        ArgumentCaptor<Attendance> attendanceCaptor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository, times(2)).save(attendanceCaptor.capture());

        for (Attendance att : attendanceCaptor.getAllValues()) {
            assertEquals(AttendanceStatus.ON_LEAVE, att.getStatus());
            assertEquals(0.0, att.getWorkHours());
            assertTrue(att.getNotes().contains("Nghỉ phép: ANNUAL"));
        }
    }

    @Test
    @DisplayName("Kiểm thử từ chối đơn bắt buộc phải có reason -> Ném ngoại lệ khi thiếu reason")
    void rejectLeaveRequest_withoutReason_shouldThrowIllegalArgumentException() {
        // Arrange & Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> leaveService.rejectLeaveRequest(200L, 2L, "  ")
        );

        assertTrue(exception.getMessage().contains("Lý do từ chối (reason) là bắt buộc"));
        verify(leaveRequestRepository, never()).save(any(LeaveRequest.class));
    }

    @Test
    @DisplayName("Kiểm thử từ chối đơn thành công khi có reason hợp lệ")
    void rejectLeaveRequest_success_shouldMarkRejected() {
        // Arrange
        LeaveRequest pendingRequest = LeaveRequest.builder()
                .id(200L)
                .user(employee)
                .leaveType(LeaveType.ANNUAL)
                .startDate(LocalDate.of(2026, 11, 2))
                .endDate(LocalDate.of(2026, 11, 3))
                .totalDays(2.0)
                .status(LeaveStatus.PENDING)
                .build();

        when(leaveRequestRepository.findById(200L)).thenReturn(Optional.of(pendingRequest));
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        LeaveResponse response = leaveService.rejectLeaveRequest(200L, 2L, "Dự án đang trong giai đoạn bàn giao");

        // Assert
        assertNotNull(response);
        assertEquals(LeaveStatus.REJECTED, response.getStatus());
        assertEquals(2L, response.getApproverId());
        assertEquals("Dự án đang trong giai đoạn bàn giao", response.getRejectionReason());

        verify(leaveRequestRepository, times(1)).save(pendingRequest);
        verify(attendanceRepository, never()).save(any(Attendance.class));
        verify(leaveBalanceRepository, never()).save(any(LeaveBalance.class));
    }
}
