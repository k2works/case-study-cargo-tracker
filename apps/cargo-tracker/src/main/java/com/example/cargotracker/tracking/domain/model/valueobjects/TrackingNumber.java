package com.example.cargotracker.tracking.domain.model.valueobjects;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * 追跡番号（US14）。
 *
 * <p>形式は {@code TRK-YYYYMMDD-NNNN} である（{@code ui_design.md}「荷役作業登録」）。
 *
 * <p><strong>日付は業務のタイムゾーンで決める。</strong> サーバの標準時で採番すると、
 * 時差の分だけ現場の日付と食い違う番号が出る。日中しか動かさなければ気づかない。
 *
 * <p><strong>連番はシーケンスから受け取る。</strong> 「その日の最大値 + 1」で採番すると、
 * 2 人が同時に発行したとき両者が同じ値を読む（IT1 持ち越しで荷主コードに起きた問題と同型）。
 *
 * @param value 追跡番号
 */
public record TrackingNumber(String value) {

    private static final Pattern FORMAT = Pattern.compile("^TRK-\\d{8}-\\d{4}$");

    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 連番の桁数。桁あふれは形式の検査で落ちる。 */
    private static final int SEQUENCE_DIGITS = 4;

    public TrackingNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("追跡番号は必須です");
        }
        value = value.strip();
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "追跡番号の形式が正しくありません（TRK-YYYYMMDD-NNNN）: " + value);
        }
    }

    /**
     * 業務日付と連番から採番する。
     *
     * @param clock    業務のタイムゾーンを持つ時計
     * @param sequence シーケンスが払い出した連番
     */
    public static TrackingNumber issue(Clock clock, long sequence) {
        if (clock == null) {
            throw new IllegalArgumentException("時計は必須です");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("連番は 0 以上です: " + sequence);
        }
        String date = LocalDate.now(clock).format(DATE_PART);
        // 連番はその日ごとに 0 に戻さない。**日付は「いつ発行したか」であり、
        // 一意性はシーケンスが担う。** 日ごとに戻すと日をまたぐ処理で衝突する。
        //
        // **桁あふれは丸めない。** 4 桁に収まらない連番を剰余で丸めると、
        // 同じ日に発行済みの番号と重複した番号を、何事も無かったように返す。
        // 形式の検査で落として気づける形にする（上限に達したら桁を増やす判断が要る）。
        String number = "%0" + SEQUENCE_DIGITS + "d";
        return new TrackingNumber("TRK-%s-%s".formatted(date, number.formatted(sequence)));
    }

    @Override
    public String toString() {
        return value;
    }
}
