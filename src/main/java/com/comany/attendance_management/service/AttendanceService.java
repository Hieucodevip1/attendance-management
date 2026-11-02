package com.comany.attendance_management.service;

import com.comany.attendance_management.dto.AttendanceResponse;
import com.comany.attendance_management.dto.CheckInRequest;
import com.comany.attendance_management.dto.CheckOutRequest;

public interface AttendanceService {

    AttendanceResponse checkIn(CheckInRequest request);

    AttendanceResponse checkIn(Long userId);

    AttendanceResponse checkOut(CheckOutRequest request);

    AttendanceResponse checkOut(Long userId);
}

