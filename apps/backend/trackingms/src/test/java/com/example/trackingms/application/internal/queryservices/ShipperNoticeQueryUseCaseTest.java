package com.example.trackingms.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.trackingms.application.internal.outboundservices.acl.ShipperCargoSnapshotFinder;
import com.example.trackingms.application.internal.outboundservices.acl.UserShipperLinkFinder;
import com.example.trackingms.domain.model.valueobjects.NoticeWatermark;
import com.example.trackingms.domain.model.valueobjects.ShipperNotice;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.trackingms.domain.repository.NoticeWatermarkRepository;
import com.example.trackingms.domain.repository.ShipperNoticeRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("荷主が受け取る知らせ")
class ShipperNoticeQueryUseCaseTest {

    private static final TrackingNumber MINE = TrackingNumber.of("TRK-20260823-0001");
    private static final TrackingNumber OTHERS = TrackingNumber.of("TRK-20260823-0002");

    private final Map<String, NoticeWatermark> watermarks = new HashMap<>();

    @Test
    @DisplayName("読んだ位置より新しい知らせだけを返す")
    void returnsOnlyUnread() {
        watermarks.put("shipper01", NoticeWatermark.of(1L));
        ShipperNoticeQueryUseCase useCase = useCaseWith(
                new ShipperNotice(1L, MINE, Instant.parse("2026-09-01T00:00:00Z"), "積み込みました"),
                new ShipperNotice(2L, MINE, Instant.parse("2026-09-01T01:00:00Z"), "出港しました"));

        List<ShipperNotice> unread = useCase.unread("shipper01");

        assertThat(unread).extracting(ShipperNotice::id).containsExactly(2L);
    }

    /**
     * <strong>他社の貨物の知らせは、番号を知っていても届かない。</strong>
     * 一覧（US33）と同じ判定を使う——ここで別の絞り込みを書くと、片方だけが正しくなる。
     */
    @Test
    @DisplayName("自社の貨物への知らせだけを返す")
    void othersCargoNeverReaches() {
        ShipperNoticeQueryUseCase useCase = useCaseWith(
                new ShipperNotice(1L, MINE, Instant.parse("2026-09-01T00:00:00Z"), "自社の知らせ"),
                new ShipperNotice(2L, OTHERS, Instant.parse("2026-09-01T01:00:00Z"), "他社の知らせ"));

        assertThat(useCase.unread("shipper01"))
                .extracting(ShipperNotice::message)
                .containsExactly("自社の知らせ");
    }

    @Test
    @DisplayName("荷主に紐付いていない利用者には、何も返さない")
    void unlinkedUserGetsNothing() {
        ShipperNoticeQueryUseCase useCase = useCaseWith(
                new ShipperNotice(1L, MINE, Instant.parse("2026-09-01T00:00:00Z"), "積み込みました"));

        assertThat(useCase.unread("unlinked01")).isEmpty();
    }

    @Test
    @DisplayName("読んだことにすると、次からは返らない")
    void acknowledgingHidesThem() {
        ShipperNoticeQueryUseCase useCase = useCaseWith(
                new ShipperNotice(1L, MINE, Instant.parse("2026-09-01T00:00:00Z"), "積み込みました"),
                new ShipperNotice(2L, MINE, Instant.parse("2026-09-01T01:00:00Z"), "出港しました"));

        useCase.acknowledge("shipper01", 2L);

        assertThat(useCase.unread("shipper01")).isEmpty();
    }

    /**
     * <strong>他人の既読を動かせない。</strong>読んだ位置は利用者ごとに持つ。
     */
    @Test
    @DisplayName("ある利用者が読んでも、別の利用者の未読は残る")
    void acknowledgementIsPerUser() {
        watermarks.put("shipper09", NoticeWatermark.unread());
        ShipperNoticeQueryUseCase useCase = useCaseWith(
                new ShipperNotice(1L, MINE, Instant.parse("2026-09-01T00:00:00Z"), "積み込みました"));

        useCase.acknowledge("shipper01", 1L);

        assertThat(useCase.unread("shipper01")).isEmpty();
        assertThat(watermarks.get("shipper09")).isEqualTo(NoticeWatermark.unread());
    }

    private ShipperNoticeQueryUseCase useCaseWith(ShipperNotice... notices) {
        return new ShipperNoticeQueryUseCase(new StubNotices(List.of(notices)),
                new StubWatermarks(watermarks), new StubLinks(), new StubSnapshots());
    }

    private record StubNotices(List<ShipperNotice> all) implements ShipperNoticeRepository {
        @Override
        public List<ShipperNotice> findNewerThan(List<TrackingNumber> trackingNumbers,
                long lastNoticeId, int limit) {
            return all.stream()
                    .filter(notice -> trackingNumbers.contains(notice.trackingNumber()))
                    .filter(notice -> notice.id() > lastNoticeId)
                    .limit(limit)
                    .toList();
        }
    }

    private record StubWatermarks(Map<String, NoticeWatermark> stored)
            implements NoticeWatermarkRepository {
        @Override
        public NoticeWatermark find(String username) {
            return stored.getOrDefault(username, NoticeWatermark.unread());
        }

        @Override
        public void save(String username, NoticeWatermark watermark) {
            stored.put(username, watermark);
        }
    }

    private static final class StubLinks implements UserShipperLinkFinder {
        @Override
        public Optional<Long> findLinkedShipperId(String username) {
            return "shipper01".equals(username) ? Optional.of(1L) : Optional.empty();
        }
    }

    private static final class StubSnapshots implements ShipperCargoSnapshotFinder {
        @Override
        public List<ShipperCargoSnapshot> findByShipperId(long shipperId) {
            return List.of(new ShipperCargoSnapshot("BKG-2026000001", MINE.value(), shipperId, false));
        }

        @Override
        public Optional<ShipperCargoSnapshot> findByTrackingNumber(TrackingNumber trackingNumber) {
            return Optional.empty();
        }

        @Override
        public java.util.Set<String> simulatedAmong(List<TrackingNumber> trackingNumbers) {
            return java.util.Set.of();
        }
    }
}
