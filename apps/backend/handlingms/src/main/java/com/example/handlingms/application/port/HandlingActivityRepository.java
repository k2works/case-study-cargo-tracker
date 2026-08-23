package com.example.handlingms.application.port;

import com.example.handlingms.domain.model.CargoBookingId;
import com.example.handlingms.domain.model.HandlingActivity;
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
}
