package com.example.trackingms.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("既読の位置")
class NoticeWatermarkTest {

    @Test
    @DisplayName("まだ何も読んでいなければ、すべてが未読になる")
    void nothingReadYet() {
        assertThat(NoticeWatermark.unread().isUnread(1L)).isTrue();
    }

    @Test
    @DisplayName("読んだ位置より新しいものだけが未読になる")
    void onlyNewerIsUnread() {
        NoticeWatermark watermark = NoticeWatermark.of(10L);

        assertThat(watermark.isUnread(11L)).isTrue();
        assertThat(watermark.isUnread(10L)).isFalse();
        assertThat(watermark.isUnread(9L)).isFalse();
    }

    /**
     * <strong>戻さない。</strong>読んだ位置が戻ると、一度消したはずの知らせが
     * もう一度出る。画面を 2 つ開いているとき、古い方の応答が後に届くだけで起きる。
     */
    @Test
    @DisplayName("古い位置で上書きしようとしても、読んだ位置は戻らない")
    void neverMovesBackwards() {
        NoticeWatermark watermark = NoticeWatermark.of(10L);

        assertThat(watermark.advanceTo(5L)).isEqualTo(NoticeWatermark.of(10L));
        assertThat(watermark.advanceTo(10L)).isEqualTo(NoticeWatermark.of(10L));
        assertThat(watermark.advanceTo(12L)).isEqualTo(NoticeWatermark.of(12L));
    }

    @Test
    @DisplayName("読んだ位置に負の値は置けない")
    void rejectsNegative() {
        assertThatThrownBy(() -> NoticeWatermark.of(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
