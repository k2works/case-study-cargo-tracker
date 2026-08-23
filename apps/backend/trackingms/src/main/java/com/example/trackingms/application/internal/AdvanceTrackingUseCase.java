package com.example.trackingms.application.internal;

import com.example.trackingms.application.port.TrackingActivityRepository;
import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingNumber;

/**
 * 荷役の記録に応じて追跡を進める（US15-4・[ADR-023] 決定 5）。
 *
 * <p><strong>知らない追跡番号では止まらない。</strong>荷役の記録は実際に起きた作業であり、
 * こちらに追跡が無いことはある（発行のイベントを取りこぼした場合など）。ここで例外にすると
 * イベントがデッドレターへ回り、<strong>原因が直るまで後続の荷役も進まなくなる</strong>。
 * 取りこぼしは運用の照会（`dev:k8s:events:missing`）が拾う。
 */
public class AdvanceTrackingUseCase {

    private final TrackingActivityRepository activities;

    public AdvanceTrackingUseCase(TrackingActivityRepository activities) {
        this.activities = activities;
    }

    /**
     * 追跡を進める。
     *
     * @param trackingNumber 追跡番号
     * @param handlingType 荷役の種別の名前
     * @param locationUnLocode 作業場所
     */
    public void advance(String trackingNumber, String handlingType, String locationUnLocode) {
        activities.findByTrackingNumber(TrackingNumber.of(trackingNumber))
                .ifPresent(current -> saveIfChanged(current,
                        current.afterHandling(handlingType, locationUnLocode)));
    }

    /**
     * 進んだときだけ書き込む。
     *
     * <p><strong>同じ内容の更新で行を触らない。</strong>集約は進まないとき同じものを返すので、
     * それを見て判断する。無条件に書くと、再配送のたびに {@code updated_at} が動き、
     * 「いつ状態が変わったか」が読めなくなる。
     */
    private void saveIfChanged(TrackingActivity current, TrackingActivity advanced) {
        if (advanced == current) {
            return;
        }
        activities.updateStatus(advanced);
    }
}
