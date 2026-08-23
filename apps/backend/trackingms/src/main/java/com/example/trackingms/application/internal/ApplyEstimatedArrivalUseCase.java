package com.example.trackingms.application.internal;

import com.example.trackingms.application.port.TrackingActivityRepository;
import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingNumber;
import java.time.LocalDate;

/**
 * 到着の見込みを持つ（US18-2・[ADR-024] 決定 4）。
 *
 * <p>trackingms は旅程を持たない。bookingms が旅程から日付 1 つを出し、それを受け取る。
 *
 * <p><strong>知らない追跡番号では止まらない。</strong>経路が決まるのと追跡が作られるのは
 * 別のイベントであり、届く順は入れ替わりうる。ここで例外にすると、原因が直るまで
 * 後続のイベントも進まなくなる（{@code AdvanceTrackingUseCase} と同じ立場）。
 */
public class ApplyEstimatedArrivalUseCase {

    private final TrackingActivityRepository activities;

    public ApplyEstimatedArrivalUseCase(TrackingActivityRepository activities) {
        this.activities = activities;
    }

    /**
     * 到着の見込みを反映する。
     *
     * <p><strong>同じ内容の更新で行を触らない。</strong>集約は変わらないとき同じものを
     * 返すので、それを見て判断する。無条件に書くと、再配送のたびに {@code updated_at} が
     * 動き、「いつ変わったか」が読めなくなる。
     */
    public void apply(String trackingNumber, LocalDate estimatedArrival) {
        activities.findByTrackingNumber(TrackingNumber.of(trackingNumber))
                .ifPresent(current -> saveIfChanged(current,
                        current.withEstimatedArrival(estimatedArrival)));
    }

    private void saveIfChanged(TrackingActivity current, TrackingActivity updated) {
        if (updated == current) {
            return;
        }
        activities.updateStatus(updated);
    }
}
