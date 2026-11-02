package com.comany.attendance_management.dto;

import com.comany.attendance_management.entity.LeaveRequest;
import com.comany.attendance_management.enums.LeaveStatus;
import com.comany.attendance_management.enums.LeaveType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveResponse {
    private Long id;
    private Long userId;
    private String userName;
    private LeaveType leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Double totalDays;
    private String reason;
    private String attachmentUrl;
    private LeaveStatus status;
    private Long approverId;
    private String approverName;
    private String rejectionReason;

    public static LeaveResponse fromEntity(LeaveRequest entity) {
        if (entity == null) {
            return null;
        }
        return LeaveResponse.builder()
                .id(entity.getId())
                .userId(entity.getUser() != null ? entity.getUser().getId() : null)
                .userName(entity.getUser() != null ? entity.getUser().getFullName() : null)
                .leaveType(entity.getLeaveType())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .totalDays(entity.getTotalDays())
                .reason(entity.getReason())
                .attachmentUrl(entity.getAttachmentUrl())
                .status(entity.getStatus())
                .approverId(entity.getApprover() != null ? entity.getApprover().getId() : null)
                .approverName(entity.getApprover() != null ? entity.getApprover().getFullName() : null)
                .rejectionReason(entity.getRejectionReason())
                .build();
    }
}
