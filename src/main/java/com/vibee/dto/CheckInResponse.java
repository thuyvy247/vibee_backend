package com.vibee.dto;

import java.time.Instant;

public record CheckInResponse(String ticketCode, String eventTitle, String ticketTypeName,
                              String attendeeName, Instant checkedInAt) {}
