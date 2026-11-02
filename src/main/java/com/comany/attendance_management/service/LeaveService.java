package com.comany.attendance_management.service;

import com.comany.attendance_management.dto.LeaveResponse;
import com.comany.attendance_management.dto.SubmitLeaveRequest;

import java.util.List;

public interface LeaveService {

    LeaveResponse submitLeaveRequest(Long userId, SubmitLeaveRequest request);

    LeaveResponse submitLeaveRequest(SubmitLeaveRequest request);

    LeaveResponse approveLeaveRequest(Long requestId, Long approverId);

    LeaveResponse rejectLeaveRequest(Long requestId, Long approverId, String reason);

    LeaveResponse getLeaveRequestById(Long requestId);

    List<LeaveResponse> getLeaveRequestsByUserId(Long userId);

    List<LeaveResponse> getAllLeaveRequests();
}
