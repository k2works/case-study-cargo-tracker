package com.example.cargotracker.shared.domain.calendar;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.CountryCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.Map;
import java.util.Set;

/**
 * 港の所在国の休日カレンダー（domain-model.md 不変条件 4）。
 *
 * <p><b>営業日で数える。</b> 暦日で数えると、金曜に留置された貨物が月曜の朝には
 * もう「3 日超」になる。督促は人が動く話なので、動けない日を数えては早すぎる。</p>
 *
 * <p><b>共有カーネルに置く（IT13・[ADR-0015]）。</b> 移す条件は
 * {@code domain-model.md} の要素表が書いていた——「billingms が保管料を数え始めたら、
 * そのとき移す」。US21 で請求が留置営業日を調整の根拠にするので、条件が満たされた。
 * <b>同じ留置に対して handlingms の画面と billingms の請求が違う日数を出してはならない</b>
 * ——数え方が 2 か所にあると、片方だけ直る。</p>
 *
 * <p><b>カレンダーを持たない国は土日だけ休みとして数える。</b> 早く点くほうが、
 * 遅れて点かないより安全である（計画 R3）。</p>
 */
public final class HolidayCalendar {

    /**
     * 国ごとの休日（月日）。
     *
     * <p>年に依らない固定日だけを置く。移動する休日（振替・旧正月）まで正しく
     * 数えるには外部のカレンダーが要り、それは本 IT のスコープではない。
     * <b>足りないことは「早く点く」側に倒れる</b>ので、督促が手遅れにはならない。</p>
     */
    private static final Map<String, Set<java.time.MonthDay>> FIXED_HOLIDAYS = Map.of(
            "JP", Set.of(
                    java.time.MonthDay.of(Month.JANUARY, 1),
                    java.time.MonthDay.of(Month.FEBRUARY, 11),
                    java.time.MonthDay.of(Month.FEBRUARY, 23),
                    java.time.MonthDay.of(Month.APRIL, 29),
                    java.time.MonthDay.of(Month.MAY, 3),
                    java.time.MonthDay.of(Month.MAY, 4),
                    java.time.MonthDay.of(Month.MAY, 5),
                    java.time.MonthDay.of(Month.AUGUST, 11),
                    java.time.MonthDay.of(Month.NOVEMBER, 3),
                    java.time.MonthDay.of(Month.NOVEMBER, 23)),
            "US", Set.of(
                    java.time.MonthDay.of(Month.JANUARY, 1),
                    java.time.MonthDay.of(Month.JULY, 4),
                    java.time.MonthDay.of(Month.NOVEMBER, 11),
                    java.time.MonthDay.of(Month.DECEMBER, 25)));

    /** 週末。どの国でも休みとして数える（国別の休日は {@link #FIXED_HOLIDAYS}）。 */
    private static final Set<DayOfWeek> WEEKEND =
            java.util.EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    private final Set<java.time.MonthDay> holidays;

    private HolidayCalendar(Set<java.time.MonthDay> holidays) {
        this.holidays = holidays;
    }

    /** その国のカレンダーを取る。持たない国は土日だけ休みになる。 */
    public static HolidayCalendar of(CountryCode country) {
        if (country == null) {
            throw new BusinessRuleViolation("どの国の休日か決まらないので営業日を数えられません");
        }
        return new HolidayCalendar(FIXED_HOLIDAYS.getOrDefault(country.value(), Set.of()));
    }

    /**
     * {@code from} から {@code to} までの営業日数（{@code from} の当日は数えない）。
     *
     * <p>日付単位・業務タイムゾーンで数える（不変条件 4）。時刻を持ち込むと、
     * 同じ日の中で「3 日超」になったりならなかったりする。</p>
     */
    public int businessDaysBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessRuleViolation("営業日を数える範囲がありません");
        }
        if (to.isBefore(from)) {
            // **黙って 0 にしない。** 引数を逆に渡した呼び違いは、0 日として
            // 返すと「まだ 3 日経っていない」に化けて督促が永久に出ない。
            throw new BusinessRuleViolation("営業日を数える範囲が逆です: " + from + " → " + to);
        }
        int days = 0;
        for (LocalDate day = from.plusDays(1); !day.isAfter(to); day = day.plusDays(1)) {
            if (isBusinessDay(day)) {
                days++;
            }
        }
        return days;
    }

    private boolean isBusinessDay(LocalDate day) {
        if (WEEKEND.contains(day.getDayOfWeek())) {
            return false;
        }
        return !holidays.contains(java.time.MonthDay.from(day));
    }
}
