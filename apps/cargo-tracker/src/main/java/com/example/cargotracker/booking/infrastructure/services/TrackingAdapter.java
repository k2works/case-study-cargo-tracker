package com.example.cargotracker.booking.infrastructure.services;

import com.example.cargotracker.booking.application.internal.outboundservices.acl.TrackingPort;
import com.example.cargotracker.tracking.application.internal.commandservices.IssueTrackingNumberCommand;
import com.example.cargotracker.tracking.application.internal.commandservices.TrackingCommandService;
import org.springframework.stereotype.Component;

@Component
public class TrackingAdapter implements TrackingPort {

    private final TrackingCommandService trackingCommandService;

    public TrackingAdapter(TrackingCommandService trackingCommandService) {
        this.trackingCommandService = trackingCommandService;
    }

    @Override
    public String issueTrackingNumber(String bookingId) {
        var command = new IssueTrackingNumberCommand(bookingId);
        return trackingCommandService.issueTrackingNumber(command).value();
    }
}
