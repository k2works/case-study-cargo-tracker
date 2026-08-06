package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.domain.model.BookingCommandType;
import com.example.cargotracker.booking.domain.model.BookingStatus;
import com.example.cargotracker.booking.domain.model.InvalidBookingStatusTransitionException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * BookingStatus の遷移規則を、遷移表の<strong>全セル</strong>で検証する。
 *
 * <p>正典は {@code docs/design/domain-model.md}「BookingStatus 状態遷移表」である。
 * 本テストは表を書き写すのではなく、**許可されるセルだけを列挙し、残りはすべて
 * 拒否側として自動生成する**。こうしておくと、状態やコマンドが増えたときに
 * 拒否側のテストを書き足し忘れることがない。
 *
 * <p>拒否側を網羅する理由は、**許可側だけを検証すると「何でも通る実装」でも緑になる**ため。
 * 状態遷移の価値は「進めること」ではなく「進ませないこと」にある。
 */
class BookingStatusTransitionTest {

    /**
     * 遷移表（正典）の許可セル。キーは（遷移元, コマンド）、値は遷移先。
     *
     * <p>{@code BookCargoCommand} は遷移元を持たない（新規作成）ため本表には含めず、
     * 独立したテストで扱う。
     */
    private static final Map<Cell, BookingStatus> ALLOWED = Map.ofEntries(
            // #2
            Map.entry(new Cell(BookingStatus.PRELIMINARY, BookingCommandType.ASSIGN_TO_ROUTING),
                    BookingStatus.ROUTE_PROPOSED),
            // #3 状態は変わらない（RoutingStatus のみが変わる）
            Map.entry(new Cell(BookingStatus.ROUTE_PROPOSED, BookingCommandType.ROUTE_CARGO),
                    BookingStatus.ROUTE_PROPOSED),
            // #4
            Map.entry(new Cell(BookingStatus.ROUTE_PROPOSED, BookingCommandType.CONFIRM_BOOKING),
                    BookingStatus.CONFIRMED),
            // #5
            Map.entry(new Cell(BookingStatus.CONFIRMED, BookingCommandType.ASSIGN_TRACKING_NUMBER),
                    BookingStatus.TRACKING_ISSUED),
            // #6
            Map.entry(new Cell(BookingStatus.TRACKING_ISSUED, BookingCommandType.START_TRANSPORT),
                    BookingStatus.IN_TRANSIT),
            // #7
            Map.entry(new Cell(BookingStatus.IN_TRANSIT, BookingCommandType.COMPLETE_DELIVERY),
                    BookingStatus.DELIVERED),
            // #8
            Map.entry(new Cell(BookingStatus.DELIVERED, BookingCommandType.SETTLE_BOOKING),
                    BookingStatus.SETTLED),
            // #9
            Map.entry(new Cell(BookingStatus.PRELIMINARY, BookingCommandType.CANCEL_BOOKING),
                    BookingStatus.CANCELLED),
            Map.entry(new Cell(BookingStatus.ROUTE_PROPOSED, BookingCommandType.CANCEL_BOOKING),
                    BookingStatus.CANCELLED),
            Map.entry(new Cell(BookingStatus.CONFIRMED, BookingCommandType.CANCEL_BOOKING),
                    BookingStatus.CANCELLED),
            Map.entry(new Cell(BookingStatus.TRACKING_ISSUED, BookingCommandType.CANCEL_BOOKING),
                    BookingStatus.CANCELLED),
            // #10 IN_TRANSIT からのキャンセルは承認を伴う（US30）が、状態遷移としては許可される
            Map.entry(new Cell(BookingStatus.IN_TRANSIT, BookingCommandType.CANCEL_BOOKING),
                    BookingStatus.CANCELLED));

    private record Cell(BookingStatus from, BookingCommandType command) {}

    /** 遷移元 × コマンドの全組み合わせ。8 状態 × 8 コマンド = 64 セル。 */
    private static Stream<Arguments> allCells() {
        return Arrays.stream(BookingStatus.values())
                .flatMap(from -> Arrays.stream(BookingCommandType.values())
                        .map(command -> Arguments.of(from, command)));
    }

    @ParameterizedTest(name = "{0} + {1}")
    @MethodSource("allCells")
    void 遷移表のとおりに遷移する(BookingStatus from, BookingCommandType command) {
        BookingStatus expected = ALLOWED.get(new Cell(from, command));

        if (expected == null) {
            assertThatThrownBy(() -> from.transitionBy(command))
                    .isInstanceOf(InvalidBookingStatusTransitionException.class);
        } else {
            assertThat(from.transitionBy(command)).isEqualTo(expected);
        }
    }

    @ParameterizedTest(name = "{0} + {1}")
    @MethodSource("allCells")
    void 遷移可能かの判定が遷移の成否と一致する(BookingStatus from, BookingCommandType command) {
        boolean allowed = ALLOWED.containsKey(new Cell(from, command));

        assertThat(from.canTransitionBy(command))
                .as("canTransitionBy が true なのに transitionBy が落ちる、"
                        + "またはその逆だと、画面のボタン出し分けと実際の可否がずれる")
                .isEqualTo(allowed);
    }

    /**
     * 終端状態はいかなるコマンドも受け付けない。
     *
     * <p>上の全セルテストに含まれてはいるが、**終端であることは表の読み取りではなく
     * 明示された不変条件**（domain-model.md）であるため、単独でも固定する。
     */
    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"SETTLED", "CANCELLED"})
    void 終端状態はいかなるコマンドも受け付けない(BookingStatus terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        for (BookingCommandType command : BookingCommandType.values()) {
            assertThatThrownBy(() -> terminal.transitionBy(command))
                    .isInstanceOf(InvalidBookingStatusTransitionException.class);
        }
    }

    @Test
    void 新規予約は仮受付から始まる() {
        assertThat(BookingStatus.initial()).isEqualTo(BookingStatus.PRELIMINARY);
    }

    /**
     * 遷移表に載っている状態がすべて実装されていること。
     *
     * <p>状態を減らすと拒否側のセルも一緒に消え、**網羅しているつもりで
     * 網羅していない状態**になる。件数を固定して気づけるようにする。
     */
    @Test
    void 状態は8つコマンドは8つである() {
        assertThat(BookingStatus.values()).hasSize(8);
        assertThat(BookingCommandType.values()).hasSize(8);
    }
}
