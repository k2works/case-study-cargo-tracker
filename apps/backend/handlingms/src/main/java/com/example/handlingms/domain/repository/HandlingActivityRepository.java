package com.example.handlingms.domain.repository;

import com.example.handlingms.domain.model.valueobjects.CargoBookingId;
import com.example.handlingms.domain.model.aggregates.HandlingActivity;
import com.example.handlingms.domain.model.valueobjects.HandlingType;
import java.time.Instant;
import java.util.List;

/** 荷役の記録の保存先（出力ポート）。 */
public interface HandlingActivityRepository {

    /**
     * 記録する。
     *
     * <p><strong>作成しか無い。</strong>荷役は実際に起きた作業の記録であり、あとから直す
     * ものではない。訂正が要るなら、それは<strong>訂正したという記録</strong>を足す操作で
     * ある（US15 の範囲外）。<strong>更新の分岐が無いことを明記する</strong>——書かないと、
     * 最初の更新のときに「常に INSERT する save」の形で壊れる。
     */
    HandlingActivity register(HandlingActivity activity);

    /**
     * 1 つの貨物に何が起きたかを、時系列で返す。
     *
     * @param limit 返す件数の上限。上限が無いと、件数が増えた日に一覧が開かなくなる
     */
    List<HandlingActivity> findByBookingId(CargoBookingId bookingId, int limit);

    /**
     * 同じ作業がすでに記録されているか。
     *
     * <p><strong>同じ内容の記録は、2 回起きた作業ではない。</strong>1 日数十件を打つ画面
     * では、送信の二度押しや戻る操作で同じ内容がもう一度届く。そのまま入れると履歴に
     * 同じ作業が 2 行並び、<strong>どちらが本物かを誰も判断できない</strong>。
     *
     * <p>見るのは予約番号・種別・作業場所・作業日時である。作業者は見ない——同じ作業を
     * 別の人が打ち直した場合も、作業自体は 1 回である。
     */
    boolean existsSameActivity(CargoBookingId bookingId, HandlingType type,
            String locationUnLocode, Instant completionTime);
}
