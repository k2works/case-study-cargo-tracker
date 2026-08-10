package com.example.cargotracker.handling.infrastructure.acl;

import com.example.cargotracker.booking.application.internal.outboundservices.acl
        .CargoCorrectionRequests;
import com.example.cargotracker.handling.domain.model.CorrectionRequest;
import com.example.cargotracker.handling.domain.repository.CorrectionRequestRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link CargoCorrectionRequests} の実装（ACL のアダプタ。C8）。
 *
 * <p><strong>返すのは表示のための素の値だけである。</strong> 申請の集約を返すと、
 * Booking が Handling のドメインを参照することになる（ArchUnit ルール 4）。
 *
 * <p><strong>Mapper を直接持たず、出力ポート（リポジトリ）を通す。</strong>
 * 既存の ACL アダプタはいずれもそうしている（{@code CargoExceptionsAdapter} /
 * {@code CargoSnapshotsAdapter} / {@code CustomsStatusesAdapter}）。
 * <strong>同じ問題に 2 つの答えを残さない</strong>（IT11 の {@code CustomsDeclaration} の轍）。
 *
 * <p><strong>形式の違う予約 ID を例外にしない。</strong> 例外にすると、
 * 予約詳細を開いただけで 500 になる。
 */
@Component
public class CargoCorrectionRequestsAdapter implements CargoCorrectionRequests {

    private final CorrectionRequestRepository repository;

    public CargoCorrectionRequestsAdapter(CorrectionRequestRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<CorrectionSummary> findByBookingId(String bookingId) {
        if (bookingId == null || bookingId.isBlank()) {
            return List.of();
        }
        UUID id;
        try {
            id = UUID.fromString(bookingId);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        return repository.findByBookingId(id).stream()
                .map(CargoCorrectionRequestsAdapter::toSummary)
                .toList();
    }

    private static CorrectionSummary toSummary(CorrectionRequest request) {
        return new CorrectionSummary(
                request.type().displayName(),
                request.reason(),
                request.requestedBy(),
                request.requestedAt(),
                request.status().displayName(),
                request.status().badgeClass(),
                // **画面の出し分けは集約の述語をそのまま呼ぶ。**
                // ここで `== PENDING` と書き直すと、状態を足したときに片方だけ古くなる
                request.status().isPending(),
                request.decision().reason());
    }
}
