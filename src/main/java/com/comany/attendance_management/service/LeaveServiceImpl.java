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
import com.comany.attendance_management.repository.AttendanceRepository;
import com.comany.attendance_management.repository.LeaveBalanceRepository;
import com.comany.attendance_management.repository.LeaveRequestRepository;
import com.comany.attendance_management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class LeaveServiceImpl implements LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;

    @Override
    public LeaveResponse submitLeaveRequest(Long userId, SubmitLeaveRequest request) {
        Long targetUserId = userId != null ? userId : (request != null ? request.getUserId() : null);
        if (targetUserId == null) {
            throw new IllegalArgumentException("UserId không được để trống!");
        }

        if (request == null || request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("Ngày bắt đầu và ngày kết thúc không được để trống!");
        }

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("Ngày kết thúc không được trước ngày bắt đầu!");
        }

        if (request.getLeaveType() == null) {
            throw new IllegalArgumentException("Loại nghỉ phép (leaveType) không được để trống!");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên với ID: " + targetUserId));

        // Tính số ngày nghỉ (loại trừ Thứ 7, Chủ nhật)
        double totalDays = calculateWorkingDays(request.getStartDate(), request.getEndDate());
        if (totalDays <= 0) {
            throw new IllegalArgumentException("Khoảng thời gian đăng ký không có ngày làm việc nào (chỉ rơi vào cuối tuần)!");
        }

        // Kiểm tra nếu là nghỉ phép năm (ANNUAL) thì số ngày nghỉ không được vượt quá số dư trong LeaveBalance
        if (request.getLeaveType() == LeaveType.ANNUAL) {
            int year = request.getStartDate().getYear();
            LeaveBalance balance = leaveBalanceRepository.findByUserIdAndYear(targetUserId, year)
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy thông tin quỹ phép năm " + year + " của nhân viên!"));

            Double remainingDays = balance.getRemainingDays() != null ? balance.getRemainingDays() : 0.0;
            if (totalDays > remainingDays) {
                throw new IllegalArgumentException(
                        "Số ngày nghỉ (" + totalDays + " ngày) vượt quá số ngày phép còn lại (" + remainingDays + " ngày)!");
            }
        }

        // Lưu đơn với trạng thái PENDING
        LeaveRequest leaveRequest = LeaveRequest.builder()
                .user(user)
                .leaveType(request.getLeaveType())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .totalDays(totalDays)
                .reason(request.getReason())
                .attachmentUrl(request.getAttachmentUrl())
                .status(LeaveStatus.PENDING)
                .build();

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        return LeaveResponse.fromEntity(saved);
    }

    @Override
    public LeaveResponse submitLeaveRequest(SubmitLeaveRequest request) {
        return submitLeaveRequest(request != null ? request.getUserId() : null, request);
    }

    @Override
    public LeaveResponse approveLeaveRequest(Long requestId, Long approverId) {
        if (requestId == null || approverId == null) {
            throw new IllegalArgumentException("RequestId và ApproverId không được để trống!");
        }

        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn nghỉ phép với ID: " + requestId));

        if (leaveRequest.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalStateException("Đơn nghỉ phép này đã được xử lý (trạng thái: " + leaveRequest.getStatus() + ")!");
        }

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người duyệt với ID: " + approverId));

        // Trừ quỹ phép trong LeaveBalance nếu là ANNUAL
        if (leaveRequest.getLeaveType() == LeaveType.ANNUAL) {
            int year = leaveRequest.getStartDate().getYear();
            LeaveBalance balance = leaveBalanceRepository.findByUserIdAndYear(leaveRequest.getUser().getId(), year)
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy quỹ phép năm " + year + " của nhân viên!"));

            Double remainingDays = balance.getRemainingDays() != null ? balance.getRemainingDays() : 0.0;
            if (remainingDays < leaveRequest.getTotalDays()) {
                throw new IllegalStateException("Số ngày phép còn lại không đủ để duyệt đơn!");
            }

            double newUsedDays = (balance.getUsedDays() != null ? balance.getUsedDays() : 0.0) + leaveRequest.getTotalDays();
            double totalQuota = balance.getTotalDays() != null ? balance.getTotalDays() : 0.0;
            balance.setUsedDays(newUsedDays);
            balance.setRemainingDays(totalQuota - newUsedDays);
            leaveBalanceRepository.save(balance);
        }

        // Tự động tạo/cập nhật bản ghi Attendance các ngày đó với status ON_LEAVE
        LocalDate date = leaveRequest.getStartDate();
        while (!date.isAfter(leaveRequest.getEndDate())) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                final LocalDate currentDate = date;
                Attendance attendance = attendanceRepository.findByUserIdAndWorkDate(leaveRequest.getUser().getId(), currentDate)
                        .orElseGet(() -> Attendance.builder()
                                .user(leaveRequest.getUser())
                                .workDate(currentDate)
                                .build());

                attendance.setStatus(AttendanceStatus.ON_LEAVE);
                attendance.setWorkHours(0.0);
                String note = "Nghỉ phép: " + leaveRequest.getLeaveType();
                if (attendance.getNotes() != null && !attendance.getNotes().isBlank()) {
                    attendance.setNotes(attendance.getNotes() + " | " + note);
                } else {
                    attendance.setNotes(note);
                }
                attendanceRepository.save(attendance);
            }
            date = date.plusDays(1);
        }

        // Đổi trạng thái sang APPROVED
        leaveRequest.setStatus(LeaveStatus.APPROVED);
        leaveRequest.setApprover(approver);

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        return LeaveResponse.fromEntity(saved);
    }

    @Override
    public LeaveResponse rejectLeaveRequest(Long requestId, Long approverId, String reason) {
        if (requestId == null || approverId == null) {
            throw new IllegalArgumentException("RequestId và ApproverId không được để trống!");
        }

        // Bắt buộc có reason
        if (reason == null || reason.trim().isBlank()) {
            throw new IllegalArgumentException("Lý do từ chối (reason) là bắt buộc!");
        }

        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn nghỉ phép với ID: " + requestId));

        if (leaveRequest.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalStateException("Đơn nghỉ phép này đã được xử lý (trạng thái: " + leaveRequest.getStatus() + ")!");
        }

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người duyệt với ID: " + approverId));

        // Đổi trạng thái sang REJECTED
        leaveRequest.setStatus(LeaveStatus.REJECTED);
        leaveRequest.setApprover(approver);
        leaveRequest.setRejectionReason(reason.trim());

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        return LeaveResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public LeaveResponse getLeaveRequestById(Long requestId) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn nghỉ phép với ID: " + requestId));
        return LeaveResponse.fromEntity(leaveRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveResponse> getLeaveRequestsByUserId(Long userId) {
        return leaveRequestRepository.findByUserId(userId).stream()
                .map(LeaveResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveResponse> getAllLeaveRequests() {
        return leaveRequestRepository.findAll().stream()
                .map(LeaveResponse::fromEntity)
                .toList();
    }

    private double calculateWorkingDays(LocalDate startDate, LocalDate endDate) {
        long workingDays = 0;
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            DayOfWeek dow = current.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                workingDays++;
            }
            current = current.plusDays(1);
        }
        return (double) workingDays;
    }
}
