package com.example.cargotracker.tracking.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.tracking.infrastructure.query.NoticeQueries;
import com.example.cargotracker.tracking.infrastructure.query.NoticeQueryHandler;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 荷主への知らせの入口（US37 §受入基準 1・3・4）。 */
class NoticeControllerTest {

    private final AtomicLong readTo = new AtomicLong(-1);
    private String askedFor;

    private NoticeController controller() {
        NoticeQueryHandler notices = new NoticeQueryHandler(null, null) {

            @Override
            public NoticeQueries.NoticeListView findUnread(String shipperId) {
                askedFor = shipperId;
                return new NoticeQueries.NoticeListView(List.of(), 7L);
            }

            @Override
            public void markRead(String shipperId, long sequenceNo) {
                askedFor = shipperId;
                readTo.set(sequenceNo);
            }
        };
        return new NoticeController(notices);
    }

    @Test
    @DisplayName("US37 §4: 絞りに使う荷主 ID はヘッダから受ける（本文やクエリで名乗らせない）")
    void scopesByTheHeaderShipperId() {
        var response = controller().unread("SHP-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(askedFor)
                .as("**要求の本文から受けると、他社の荷主 ID を名乗れてしまう**")
                .isEqualTo("SHP-1");
    }

    @Test
    @DisplayName("US37 §3: 既読はサーバが返した位置を送り返す")
    void marksReadAtTheServersPosition() {
        var response = controller().markRead("SHP-1",
                new NoticeController.ReadRequest(7L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(readTo.get()).isEqualTo(7L);
    }

    @Test
    @DisplayName("紐付けの無い利用者には返さない（全件に倒さない）")
    void refusesWithoutTheShipperLink() {
        // **「荷主 ID が無いなら全件」に倒すと、ヘッダを落とすだけで他社の
        // 知らせが見える。** 読みも書きも同じ守りを通す。
        assertThatThrownBy(() -> controller().unread(null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        assertThatThrownBy(() -> controller().unread("   "))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller().markRead(null,
                new NoticeController.ReadRequest(1L)))
                .isInstanceOf(ResponseStatusException.class);
    }
}
