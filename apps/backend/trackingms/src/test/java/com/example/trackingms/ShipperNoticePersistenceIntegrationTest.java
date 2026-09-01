package com.example.trackingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.trackingms.domain.model.valueobjects.NoticeWatermark;
import com.example.trackingms.domain.model.valueobjects.ShipperNotice;
import com.example.trackingms.domain.model.valueobjects.TrackingNotice;
import com.example.trackingms.domain.model.valueobjects.TrackingBookingId;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.shared.domain.model.Location;
import com.example.trackingms.domain.repository.TrackingActivityRepository;
import com.example.trackingms.domain.repository.TrackingNoticeRepository;
import com.example.trackingms.infrastructure.repositories.MyBatisShipperNoticeRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 読んだ位置が<strong>実 DB でも戻らない</strong>ことを見る（US39）。
 *
 * <p>同じ規則をドメイン（{@code NoticeWatermark#advanceTo}）と SQL の両方に置いている。
 * <strong>片方だけを確かめると、もう片方が緩んでも緑のまま</strong>になる。
 */
@DisplayName("荷主のお知らせの永続化")
class ShipperNoticePersistenceIntegrationTest extends TrackingIntegrationTestBase {

    @Autowired
    private MyBatisShipperNoticeRepository repository;

    @Autowired
    private TrackingNoticeRepository notices;

    @Autowired
    private TrackingActivityRepository activities;

    @Test
    @DisplayName("読んだ位置は、古い値では戻らない")
    void watermarkNeverMovesBackwards() {
        String username = "shipper-persist-01";

        repository.save(username, NoticeWatermark.of(5L));
        assertThat(repository.find(username)).isEqualTo(NoticeWatermark.of(5L));

        // **後着の古い値で上書きされない。**画面を 2 つ開くだけで起きる形である
        repository.save(username, NoticeWatermark.of(2L));
        assertThat(repository.find(username)).as("古い位置で戻ってしまった")
                .isEqualTo(NoticeWatermark.of(5L));

        repository.save(username, NoticeWatermark.of(9L));
        assertThat(repository.find(username)).isEqualTo(NoticeWatermark.of(9L));
    }

    @Test
    @DisplayName("まだ何も読んでいない利用者は、未読の位置から始まる")
    void unknownUserStartsUnread() {
        assertThat(repository.find("shipper-persist-unknown"))
                .isEqualTo(NoticeWatermark.unread());
    }

    @Test
    @DisplayName("知らせは古い順に、指定した位置より新しいものだけが返る")
    void readsOnlyNewerInOrder() {
        TrackingNumber number = startTracking("TRK-20260901-0011");
        notices.save(number, new TrackingNotice(Instant.parse("2026-09-01T00:00:00Z"), "1 番目"));
        notices.save(number, new TrackingNotice(Instant.parse("2026-09-01T01:00:00Z"), "2 番目"));
        notices.save(number, new TrackingNotice(Instant.parse("2026-09-01T02:00:00Z"), "3 番目"));

        List<ShipperNotice> all = repository.findNewerThan(List.of(number), 0L, 10);
        assertThat(all).extracting(ShipperNotice::message)
                .containsExactly("1 番目", "2 番目", "3 番目");

        long first = all.getFirst().id();
        assertThat(repository.findNewerThan(List.of(number), first, 10))
                .extracting(ShipperNotice::message)
                .containsExactly("2 番目", "3 番目");
    }

    /** <strong>空で問い合わせない。</strong>{@code IN ()} は構文として成り立たない。 */
    @Test
    @DisplayName("貨物が 1 件も無ければ、問い合わせずに空を返す")
    void emptyTrackingNumbersDoesNotQuery() {
        assertThat(repository.findNewerThan(List.of(), 0L, 10)).isEmpty();
    }

    private TrackingNumber startTracking(String value) {
        TrackingNumber number = TrackingNumber.of(value);
        activities.saveIfAbsent(com.example.trackingms.domain.model.aggregates.TrackingActivity.start(
                number, TrackingBookingId.of("BKG-2026000099"),
                Location.of("JPTYO", "東京"), Location.of("USLAX", "ロサンゼルス"),
                LocalDate.of(2026, java.time.Month.DECEMBER, 31)));
        return number;
    }
}
