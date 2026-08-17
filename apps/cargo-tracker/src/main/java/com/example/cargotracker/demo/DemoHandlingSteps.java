package com.example.cargotracker.demo;

import static com.example.cargotracker.demo.DemoActors.ACTOR;
import static com.example.cargotracker.demo.DemoActors.require;

import com.example.cargotracker.booking.application.internal.commandservices
        .IssueTrackingNumberCommandService;
import com.example.cargotracker.booking.application.internal.commandservices
        .RegisterConsigneeCommandService;
import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.Consignee;
import com.example.cargotracker.handling.application.internal.commandservices
        .CustomsDeclarationCommandService;
import com.example.cargotracker.handling.application.internal.commandservices
        .RegisterHandlingCommandService;
import com.example.cargotracker.handling.application.internal.queryservices.CustomsDeclarationView;
import com.example.cargotracker.handling.application.internal.queryservices.CustomsQueryService;
import com.example.cargotracker.handling.domain.model.valueobjects.CustomsStatus;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingType;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 追跡番号の発行から荷役・通関・引取まで（マニュアル 07 / 08 / 09）。
 *
 * <p><strong>順番そのものが業務のルールである。</strong> 通関が下りるまで引取は登録できず、
 * 引取が済むまで請求できない。<strong>ここで弾かれるなら、画面でも弾かれる。</strong>
 */
@ConditionalOnProperty(name = "cargo-tracker.demo.install", havingValue = "true")
@Component
class DemoHandlingSteps {

    private final IssueTrackingNumberCommandService issueTracking;
    private final RegisterConsigneeCommandService consignee;
    private final RegisterHandlingCommandService handling;
    private final CustomsDeclarationCommandService customs;
    private final CustomsQueryService customsQuery;
    private final BookingQueryService bookings;
    private final Clock clock;

    DemoHandlingSteps(
            IssueTrackingNumberCommandService issueTracking,
            RegisterConsigneeCommandService consignee,
            RegisterHandlingCommandService handling,
            CustomsDeclarationCommandService customs,
            CustomsQueryService customsQuery,
            BookingQueryService bookings,
            Clock clock) {
        this.issueTracking = issueTracking;
        this.consignee = consignee;
        this.handling = handling;
        this.customs = customs;
        this.customsQuery = customsQuery;
        this.bookings = bookings;
        this.clock = clock;
    }

    String issue(BookingId id) {
        var result = issueTracking.issue(id, ACTOR);
        require(result.isIssued(), "追跡番号を発行できませんでした: " + result.reason());
        return result.trackingNumber();
    }

    void receive(String trackingNumber, String location) {
        registerWork(trackingNumber, HandlingType.RECEIVE, location, null, null);
    }

    void receiveAndLoad(String trackingNumber, String location, String voyage) {
        receive(trackingNumber, location);
        registerWork(trackingNumber, HandlingType.LOAD, location, voyage, null);
    }

    /**
     * 引取まで通す。
     *
     * <p><strong>荷受人・通関・引取確認コードのどれが欠けても引取は登録できない。</strong>
     */
    void deliver(
            BookingId id, String trackingNumber, String voyage,
            String origin, String destination) {
        registerConsignee(id, CONSIGNEE_NAME);
        receiveAndLoad(trackingNumber, origin, voyage);
        work(trackingNumber, HandlingType.UNLOAD, destination, voyage);
        work(trackingNumber, HandlingType.CUSTOMS, destination, null);
        clearCustoms(trackingNumber);
        claim(id, trackingNumber, destination, CONSIGNEE_NAME);
    }

    // **一括で通す入口と、1 手ずつ進める入口の両方を置く**（{@code DemoBookingSteps} と同じ）。
    // 自動実行はどの荷役まで進んだかを画面に出すため、手順ごとに戻ってこられる必要がある。

    /** 既定の荷受人名。荷受人の登録と引取の申告で<strong>同じ名前でなければならない</strong>。 */
    static final String CONSIGNEE_NAME = "米国輸入商会";

    /** 荷受人を登録する（US36）。引取の前に済んでいなければならない。 */
    void registerConsignee(BookingId id, String name) {
        consignee.register(
                id, new Consignee(name, "Los Angeles, CA", "consignee-sample@example.com"), ACTOR);
    }

    /** 荷役を 1 件登録する（US15）。 */
    void work(String trackingNumber, HandlingType type, String location, String voyage) {
        registerWork(trackingNumber, type, location, voyage, null);
    }

    /**
     * 引き取る（US16）。
     *
     * <p><strong>引取確認コードは予約が持つ</strong>（US35）。画面と同じく、
     * 確定時に採番された値を引き直して使う。
     */
    void claim(BookingId id, String trackingNumber, String location, String consigneeName) {
        String claimCode = bookings.findById(id.value().toString())
                .map(view -> view.tracking().claimCode())
                .orElse(null);
        require(claimCode != null && !claimCode.isBlank(), "引取確認コードが採番されていません");
        registerWork(trackingNumber, HandlingType.CLAIM, location, null,
                new RegisterHandlingCommandService.Request.Claim(claimCode, consigneeName));
    }

    /** 通関申告を登録して通関済にする（US29）。 */
    void clearCustoms(String trackingNumber) {
        String declarationNumber = "DEC-" + trackingNumber;
        var declared = customs.declare(trackingNumber, declarationNumber, clock.instant());
        require(declared.outcome() == CustomsDeclarationCommandService.Outcome.ACCEPTED,
                "通関申告を登録できませんでした: " + declared.reason());
        // **申告 ID は登録の戻り値から取れない。** {@code Result.declarationId} は
        // 「受け付けたときの申告 ID」と書かれているが実装は常に null を返す。
        // 画面も一覧から引き直しており、ここでも同じようにする
        long declarationId = customsQuery.search(trackingNumber, null).stream()
                .filter(view -> declarationNumber.equals(view.declarationNumber()))
                .mapToLong(CustomsDeclarationView::id)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("登録した通関申告を引けません"));
        var cleared = customs.updateStatus(
                declarationId, CustomsStatus.CLEARED, "書類に不備なし", ACTOR);
        require(cleared.outcome() == CustomsDeclarationCommandService.Outcome.ACCEPTED,
                "通関を通せませんでした: " + cleared.reason());
    }

    private void registerWork(
            String trackingNumber, HandlingType type, String location,
            String voyage, RegisterHandlingCommandService.Request.Claim claim) {
        var result = handling.register(new RegisterHandlingCommandService.Request(
                trackingNumber, type,
                new RegisterHandlingCommandService.Request.Work(
                        clock.instant(), location, voyage, null, ACTOR),
                claim));
        require(result.isRegistered(),
                "荷役（%s）を登録できませんでした: %s".formatted(type, result.reason()));
    }
}
