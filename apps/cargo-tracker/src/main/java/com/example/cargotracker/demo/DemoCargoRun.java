package com.example.cargotracker.demo;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageNumber;
import com.example.cargotracker.shared.domain.model.valueobjects.ShipperId;

/**
 * デモモードが進めている貨物 1 件。
 *
 * <p><strong>どこまで進んだかを持つ。</strong> デモモードは複数の貨物を並行して
 * 少しずつ進めるため、<strong>それぞれが今どの手順にいるか</strong>を覚えていなければ
 * 次の 1 手を選べない。
 *
 * <p><strong>触るのは 1 つのスレッドだけである。</strong> 進める処理は単一スレッドで
 * 動かしており（{@code DemoModeService}）、画面へ渡すときは値を写して渡す。
 * 可変のまま共有すると、読んでいる最中に書き換わる。
 */
final class DemoCargoRun {

    private final DemoScenario scenario;
    private DemoStep nextStep = DemoStep.REGISTER_VOYAGE;
    private boolean finished;
    private String failureReason;

    // 手順を進めるうちに決まっていくもの
    private ShipperId shipperId;
    private BookingId bookingId;
    private VoyageNumber voyage;
    private String trackingNumber;

    DemoCargoRun(DemoScenario scenario) {
        this.scenario = scenario;
    }

    DemoScenario scenario() {
        return scenario;
    }

    /** 次に踏む手順。<strong>終わっていれば意味を持たない。</strong> */
    DemoStep nextStep() {
        return nextStep;
    }

    /** 最後まで通ったか、途中で止まったか。 */
    boolean finished() {
        return finished;
    }

    /** 止まった理由（止まっていなければ {@code null}）。 */
    String failureReason() {
        return failureReason;
    }

    ShipperId shipperId() {
        return shipperId;
    }

    BookingId bookingId() {
        return bookingId;
    }

    VoyageNumber voyage() {
        return voyage;
    }

    String trackingNumber() {
        return trackingNumber;
    }

    void shipperId(ShipperId value) {
        this.shipperId = value;
    }

    void bookingId(BookingId value) {
        this.bookingId = value;
    }

    void voyage(VoyageNumber value) {
        this.voyage = value;
    }

    void trackingNumber(String value) {
        this.trackingNumber = value;
    }

    /** 1 手進んだ。<strong>最後の手順を終えたら、この貨物は完了である。</strong> */
    void advance() {
        if (nextStep.isLast()) {
            finished = true;
            return;
        }
        nextStep = nextStep.next();
    }

    /** 途中で止まった。<strong>止まった貨物はもう進めない。</strong> */
    void fail(String reason) {
        this.failureReason = reason;
        this.finished = true;
    }
}
