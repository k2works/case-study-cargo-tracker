package com.example.cargotracker.shared.domain.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.CountryCode;
import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 港の所在国の休日カレンダー（domain-model.md 不変条件 4）。
 *
 * <p><b>営業日で数える。</b> 暦日で数えると、金曜に留置された貨物が月曜の朝には
 * もう「3 日超」になる。督促は人が動く話なので、動けない日を数えては早すぎる。</p>
 */
class HolidayCalendarTest {

    private static final CountryCode JP = new CountryCode("JP");
    private static final CountryCode US = new CountryCode("US");
    /** カレンダーを持たない国。**土日だけ休みとして数える**（R3 の既定）。 */
    private static final CountryCode UNKNOWN = new CountryCode("ZZ");

    @Test
    @DisplayName("同じ日なら 0 日")
    void countsZeroForTheSameDay() {
        LocalDate monday = LocalDate.of(2026, Month.OCTOBER, 5);
        assertThat(HolidayCalendar.of(JP).businessDaysBetween(monday, monday)).isZero();
    }

    @Test
    @DisplayName("平日だけを数える（月曜から金曜で 4 日）")
    void countsWeekdays() {
        assertThat(HolidayCalendar.of(UNKNOWN).businessDaysBetween(
                LocalDate.of(2026, Month.OCTOBER, 5), LocalDate.of(2026, Month.OCTOBER, 9)))
                .isEqualTo(4);
    }

    @Test
    @DisplayName("土日はまたいでも数えない（金曜から月曜で 1 日）")
    void skipsWeekends() {
        // **暦日なら 3 日。** 金曜に留置された貨物が月曜には「3 日超」になってしまう。
        assertThat(HolidayCalendar.of(UNKNOWN).businessDaysBetween(
                LocalDate.of(2026, Month.OCTOBER, 9), LocalDate.of(2026, Month.OCTOBER, 12)))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("その国の休日は数えない（日本の 11/3 文化の日）")
    void skipsNationalHolidays() {
        // 2026-11-02(月) → 2026-11-04(水)。11/3(火) は休日なので営業日は 1 日。
        assertThat(HolidayCalendar.of(JP).businessDaysBetween(
                LocalDate.of(2026, Month.NOVEMBER, 2), LocalDate.of(2026, Month.NOVEMBER, 4)))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("休日は国ごとに違う（同じ 11/3 でも米国は営業日）")
    void holidaysDifferPerCountry() {
        // **国ごとに違うから、国コードと対で持つ。** 1 つの表にすると、
        // どこかの港の休日が別の国の判定に混ざる。
        assertThat(HolidayCalendar.of(US).businessDaysBetween(
                LocalDate.of(2026, Month.NOVEMBER, 2), LocalDate.of(2026, Month.NOVEMBER, 4)))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("カレンダーを持たない国は土日だけ休みとして数える")
    void unknownCountryFallsBackToWeekends() {
        // **早く点くほうが安全。** 遅れて点くと督促そのものが手遅れになる（R3）。
        assertThat(HolidayCalendar.of(UNKNOWN).businessDaysBetween(
                LocalDate.of(2026, Month.NOVEMBER, 2), LocalDate.of(2026, Month.NOVEMBER, 4)))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("未来から過去へは数えない（呼び違いを黙って 0 にしない）")
    void refusesReversedRange() {
        assertThatThrownBy(() -> HolidayCalendar.of(JP).businessDaysBetween(
                LocalDate.of(2026, Month.OCTOBER, 9), LocalDate.of(2026, Month.OCTOBER, 5)))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("範囲の片方が無ければ数えない（起点も終点も必須）")
    void refusesMissingRange() {
        // **黙って 0 にしない。** 留置日時を持たない申告で呼ばれたら、0 日は
        // 「まだ 3 日経っていない」に化けて督促が永久に出ない。
        HolidayCalendar calendar = HolidayCalendar.of(JP);
        LocalDate day = LocalDate.of(2026, Month.OCTOBER, 5);
        assertThatThrownBy(() -> calendar.businessDaysBetween(null, day))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> calendar.businessDaysBetween(day, null))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("国コードが無ければ作れない（どの国の休日か決まらない）")
    void requiresACountry() {
        assertThatThrownBy(() -> HolidayCalendar.of(null))
                .isInstanceOf(BusinessRuleViolation.class);
    }
}
