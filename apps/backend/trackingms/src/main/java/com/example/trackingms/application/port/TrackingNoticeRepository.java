package com.example.trackingms.application.port;

import com.example.trackingms.domain.model.TrackingNotice;
import com.example.trackingms.domain.model.TrackingNumber;
import java.util.List;

/** 通知した事実の保存先（[ADR-024] 決定 9）。 */
public interface TrackingNoticeRepository {

    void save(TrackingNumber trackingNumber, TrackingNotice notice);

    /**
     * 1 つの貨物への通知を、新しい順に返す。
     *
     * <p><strong>新しい順である。</strong>経過（古い順）とは逆——お知らせは
     * 「いま何が起きているか」を先に読むものである。
     */
    List<TrackingNotice> findByTrackingNumber(TrackingNumber trackingNumber, int limit);
}
