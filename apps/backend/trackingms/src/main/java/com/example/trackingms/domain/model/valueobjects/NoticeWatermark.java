package com.example.trackingms.domain.model.valueobjects;

/**
 * その利用者が<strong>どこまで知らせを読んだか</strong>。
 *
 * <p>お知らせは「まだ見ていないものだけ」をポップアップで出す。既読をブラウザ側に
 * 持つと、別の端末で同じ知らせがもう一度出る——荷主は自宅の PC と現場の端末を
 * 使い分けるため、<strong>覚える場所はサーバでなければならない</strong>。
 *
 * @param lastNoticeId 読み終えた知らせの番号。0 は「まだ何も読んでいない」
 */
public record NoticeWatermark(long lastNoticeId) {

    public NoticeWatermark {
        if (lastNoticeId < 0) {
            throw new IllegalArgumentException("読んだ位置に負の値は置けません");
        }
    }

    /** まだ何も読んでいない状態。 */
    public static NoticeWatermark unread() {
        return new NoticeWatermark(0L);
    }

    public static NoticeWatermark of(long lastNoticeId) {
        return new NoticeWatermark(lastNoticeId);
    }

    public boolean isUnread(long noticeId) {
        return noticeId > lastNoticeId;
    }

    /**
     * 読んだ位置を進める。<strong>戻すことはできない。</strong>
     *
     * <p>戻ると、一度消したはずの知らせがもう一度出る。画面を 2 つ開いているとき、
     * 古い方の応答が後に届くだけでそうなる——利用者の操作は正しいのに、
     * <strong>知らせだけが蘇る</strong>。
     */
    public NoticeWatermark advanceTo(long noticeId) {
        return noticeId > lastNoticeId ? new NoticeWatermark(noticeId) : this;
    }
}
