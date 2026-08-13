package com.example.cargotracker.demo;

import static com.example.cargotracker.demo.DemoActors.ACTOR;
import static com.example.cargotracker.demo.DemoActors.APPROVER;
import static com.example.cargotracker.demo.DemoActors.require;

import com.example.cargotracker.booking.application.internal.commandservices
        .CancelBookingApprovalCommandService;
import com.example.cargotracker.booking.application.internal.queryservices.CancellationQueryService;
import com.example.cargotracker.booking.application.internal.queryservices.CancellationView;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 輸送中のキャンセルを申請し、承認して荷降し手配を残す（マニュアル 07.6 / 08.5）。
 *
 * <p><strong>申請した本人は承認できない</strong>（US30）。
 */
@ConditionalOnProperty(name = "cargo-tracker.demo.install", havingValue = "true")
@Component
class DemoCancellationSteps {

    private final CancelBookingApprovalCommandService cancellation;
    private final CancellationQueryService cancellations;

    DemoCancellationSteps(
            CancelBookingApprovalCommandService cancellation,
            CancellationQueryService cancellations) {
        this.cancellation = cancellation;
        this.cancellations = cancellations;
    }

    /** 申請だけして承認しない（マニュアル 07.6 の承認待ち一覧）。 */
    void request(BookingId id) {
        var requested = cancellation.request(
                id.value().toString(), "荷主の都合により輸送を中止します", ACTOR);
        require(requested.outcome() == CancelBookingApprovalCommandService.Outcome.SUCCEEDED,
                "キャンセルを申請できませんでした: " + requested.reason());
    }

    void requestAndApprove(BookingId id) {
        String bookingId = id.value().toString();
        request(id);

        // **陸揚げ地は候補の中からしか選べない。** 画面と同じく候補の先頭を選ぶ
        Optional<Location> discharge = cancellation.findCargo(bookingId)
                .map(cancellation::candidatesFor)
                .filter(candidates -> !candidates.isEmpty())
                .map(candidates -> candidates.get(0));
        Optional<Long> requestId = cancellations.findByBookingId(bookingId)
                .stream().map(CancellationView::id).findFirst();
        require(discharge.isPresent() && requestId.isPresent(),
                "キャンセルの申請または陸揚げ地の候補が作れませんでした");

        var approved = cancellation.approve(
                requestId.orElseThrow(), discharge.orElseThrow().unlocode(), APPROVER);
        require(approved.outcome() == CancelBookingApprovalCommandService.Outcome.SUCCEEDED,
                "キャンセルを承認できませんでした: " + approved.reason());
    }
}
