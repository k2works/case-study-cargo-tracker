package com.example.bookingms.domain.model.aggregates;

import static com.example.bookingms.domain.model.aggregates.CargoFixtures.ROUTE;
import static com.example.bookingms.domain.model.aggregates.CargoFixtures.specification;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shared.domain.model.Location;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.example.bookingms.domain.model.valueobjects.CargoItinerary;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.Leg;
import com.example.bookingms.domain.model.valueobjects.TrackingNumber;
import com.example.bookingms.domain.model.valueobjects.VoyageNumber;

/**
 * 述語と操作が食い違わないことを検査する（IT7 返済枠 0.7 の裏取り）。
 *
 * <p>応答に載せた「行える操作」は、この述語から導かれる。<strong>述語が「できる」と答えた
 * のに操作が断る（あるいはその逆）と、画面はボタンを出すのに押すと 409 になる。</strong>
 *
 * <p>述語と操作を<strong>ひとつずつ手で確かめない</strong>。手で並べると、操作を足したときに
 * 並べ忘れる。ここでは<strong>予約が通る道筋の各段で、8 つの操作すべて</strong>を
 * 突き合わせる。
 */
@DisplayName("述語と操作の一致")
class CargoTransitionConsistencyTest {

    private static final ZoneId LA = ZoneId.of("America/Los_Angeles");
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-23T00:00:00Z"), ZoneId.of("Asia/Tokyo"));

    /** 予約が通る道筋の各段。ここに段を足せば、8 つの操作すべてが自動的に確かめられる。 */
    private static List<Cargo> everyStage() {
        Cargo booked = Cargo.book(1L, specification(CargoType.GENERAL, null, null), ROUTE);
        Cargo requested = booked.requestRouting();
        Cargo consulted = requested.requestConsultation();
        Cargo routed = requested.assignItinerary(itinerary(), LA);
        Cargo notified = routed.notifyShipper(Instant.parse("2026-08-22T02:00:00Z"), "sales01");
        Cargo confirmed = notified.confirm();
        Cargo issued = confirmed.issueTrackingNumber(TrackingNumber.of("TRK-20260823-0001"));
        Cargo returned = notified.returnToRouting();
        return List.of(booked, requested, consulted, routed, notified, confirmed, issued,
                returned);
    }

    private static CargoItinerary itinerary() {
        return CargoItinerary.of(List.of(Leg.of(VoyageNumber.of("V0100"),
                Location.of("JPTYO", "Tokyo"), Location.of("USLAX", "Los Angeles"),
                Instant.parse("2026-09-02T09:00:00Z"),
                Instant.parse("2026-09-15T09:00:00Z"))));
    }

    /**
     * 述語と操作の組。
     *
     * @param name 操作の名前（落ちたときに読む）
     * @param can 述語
     * @param operate 操作。状態が許さなければ {@link IllegalStateException} を投げる
     */
    private record Transition(String name, Predicate<Cargo> can, Consumer<Cargo> operate) {
    }

    private static List<Transition> transitions() {
        return List.of(
                new Transition("requestRouting", Cargo::canRequestRouting, Cargo::requestRouting),
                new Transition("assignItinerary", Cargo::canAssignItinerary,
                        cargo -> cargo.assignItinerary(itinerary(), LA)),
                new Transition("requestConsultation", Cargo::canRequestConsultation,
                        Cargo::requestConsultation),
                new Transition("notifyShipper", Cargo::canNotifyShipper,
                        cargo -> cargo.notifyShipper(Instant.parse("2026-08-22T02:00:00Z"),
                                "sales01")),
                new Transition("confirm", Cargo::canConfirm, Cargo::confirm),
                new Transition("returnToRouting", Cargo::canReturnToRouting,
                        Cargo::returnToRouting),
                new Transition("issueTrackingNumber", Cargo::canIssueTrackingNumber,
                        cargo -> cargo.issueTrackingNumber(
                                TrackingNumber.of("TRK-20260823-0002"))),
                new Transition("reviseSchedule", Cargo::canReviseSchedule,
                        cargo -> cargo.reviseSchedule(LocalDate.of(2026, Month.SEPTEMBER, 1),
                                LocalDate.of(2026, Month.SEPTEMBER, 20), LA, CLOCK)));
    }

    /**
     * <strong>述語が「できない」と答えたら、操作は必ず断る。</strong>
     *
     * <p>ここがずれると、画面はボタンを出さないのに API は通す——認可の外側で
     * 「画面にないから安全」という思い込みができる。
     */
    @Test
    @DisplayName("できないと答えた操作は、必ず断られる")
    void everyFalsePredicateIsRefused() {
        for (Cargo cargo : everyStage()) {
            for (Transition transition : transitions()) {
                if (transition.can().test(cargo)) {
                    continue;
                }
                // ラムダの中で例外を投げうる呼び出しを 1 つにする。複数あると、
                // どちらが投げたのか分からないまま緑になりうる
                Consumer<Cargo> operate = transition.operate();
                assertThatThrownBy(() -> operate.accept(cargo))
                        .as("%s は「できない」と答えたのに、%s の状態で通った",
                                transition.name(), cargo.bookingStatus())
                        .isInstanceOf(IllegalStateException.class);
            }
        }
    }

    /**
     * <strong>述語が「できる」と答えたら、状態を理由に断らない。</strong>
     *
     * <p>ずれると、画面はボタンを出すのに押すと 409 になる。利用者は「押せるのにできない」を
     * 毎回学び直すことになる。
     */
    @Test
    @DisplayName("できると答えた操作は、状態を理由に断られない")
    void everyTruePredicateIsAccepted() {
        for (Cargo cargo : everyStage()) {
            for (Transition transition : transitions()) {
                if (!transition.can().test(cargo)) {
                    continue;
                }
                Consumer<Cargo> operate = transition.operate();
                assertThatCode(() -> operate.accept(cargo))
                        .as("%s は「できる」と答えたのに、%s の状態で断られた",
                                transition.name(), cargo.bookingStatus())
                        .doesNotThrowAnyException();
            }
        }
    }

    /** 組が 1 つも無ければ、この検査は何も守らない。 */
    @Test
    @DisplayName("道筋と操作を実際に並べている")
    void actuallyCoversTheTransitions() {
        assertThat(everyStage()).hasSizeGreaterThan(5);
        assertThat(transitions()).hasSize(8);
    }
}
