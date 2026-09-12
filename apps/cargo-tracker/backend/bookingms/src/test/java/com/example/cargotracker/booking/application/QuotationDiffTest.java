package com.example.cargotracker.booking.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.Dimensions;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.Weight;
import com.example.cargotracker.booking.infrastructure.persistence.QuotationMapper;
import com.example.cargotracker.shared.domain.location.Location;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 見積と突き合わせられないときの振る舞い（US01）。
 *
 * <p><b>予約は通す。</b> 違いを知らせられないだけで、予約そのものは既に
 * 受け付けてある——番号の打ち間違いや投影の遅れで業務が止まるほうが重い。</p>
 *
 * <p>違いの数え方そのものは {@code QuotationDiffTest}（値オブジェクト側）が見る。
 * ここは<b>読めなかったときの扱い</b>だけを見る。</p>
 */
class QuotationDiffTest {

    private static BookCargoCommand booking() {
        return new BookCargoCommand("B-1", "SHP-000001",
                new CargoSpecification(CargoType.GENERAL, Weight.ofKilograms("1200"),
                        new Dimensions(new BigDecimal("120"), new BigDecimal("80"),
                                new BigDecimal("100")),
                        10, "自動車部品", null, null),
                new RouteSpecification(Location.of("JPTYO"), Location.of("USNYC"),
                        LocalDate.of(2026, 12, 1)),
                "sales01");
    }

    @Test
    @DisplayName("見積番号が無ければ違いも無い（見積を経ない予約のほうが多い）")
    void reportsNothingWithoutAQuotationId() {
        var diff = new QuotationDiff(Mockito.mock(QuotationMapper.class));

        assertThat(diff.differences(null, booking())).isEmpty();
        assertThat(diff.differences("  ", booking())).isEmpty();
    }

    @Test
    @DisplayName("見積が見つからなければ違いも無い（打ち間違いで予約を止めない）")
    void reportsNothingWhenTheQuotationIsMissing() {
        var quotations = Mockito.mock(QuotationMapper.class);
        Mockito.when(quotations.find("Q-1")).thenReturn(null);

        assertThat(new QuotationDiff(quotations).differences("Q-1", booking())).isEmpty();
    }

    @Test
    @DisplayName("見積を読めなくても予約は通す（例外を呼び出し元へ投げ返さない）")
    void survivesALookupFailure() {
        var quotations = Mockito.mock(QuotationMapper.class);
        Mockito.when(quotations.find("Q-1"))
                .thenThrow(new IllegalStateException("読み取りモデルに繋がりません"));

        assertThat(new QuotationDiff(quotations).differences("Q-1", booking()))
                .as("投げ返すと、予約の応答そのものが 500 になる")
                .isEmpty();
    }
}
