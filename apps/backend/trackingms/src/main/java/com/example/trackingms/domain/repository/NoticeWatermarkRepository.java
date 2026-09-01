package com.example.trackingms.domain.repository;

import com.example.trackingms.domain.model.valueobjects.NoticeWatermark;

/** 利用者ごとの「どこまで読んだか」の保存先（US39）。 */
public interface NoticeWatermarkRepository {

    /** まだ何も読んでいなければ {@link NoticeWatermark#unread()} を返す。 */
    NoticeWatermark find(String username);

    void save(String username, NoticeWatermark watermark);
}
