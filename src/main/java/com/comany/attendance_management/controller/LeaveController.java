package com.comany.attendance_management.controller;

import com.comany.attendance_management.dto.ApproveLeaveRequest;
import com.comany.attendance_management.dto.LeaveResponse;
import com.comany.attendance_management.dto.RejectLeaveRequest;
import com.comany.attendance_management.dto.SubmitLeaveRequest;
import com.comany.attendance_management.service.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leave-requests")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    @PostMapping
    public ResponseEntity<LeaveResponse> submitLeaveRequest(
            @RequestParam(required = false) Long userId,
            @RequestBody SubmitLeaveRequest request) {
        LeaveResponse response = leaveService.submitLeaveRequest(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<LeaveResponse> approveLeaveRequest(
            @PathVariable Long id,
            @RequestBody ApproveLeaveRequest request) {
        LeaveResponse response = leaveService.approveLeaveRequest(id, request.getApproverId());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<LeaveResponse> rejectLeaveRequest(
            @PathVariable Long id,
            @RequestBody RejectLeaveRequest request) {
        LeaveResponse response = leaveService.rejectLeaveRequest(id, request.getApproverId(), request.getReason());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeaveResponse> getLeaveRequestById(@PathVariable Long id) {
        LeaveResponse response = leaveService.getLeaveRequestById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<LeaveResponse>> getLeaveRequests(@RequestParam(required = false) Long userId) {
        List<LeaveResponse> responses = userId != null
                ? leaveService.getLeaveRequestsByUserId(userId)
                : leaveService.getAllLeaveRequests();
        return ResponseEntity.ok(responses);
    }
}
