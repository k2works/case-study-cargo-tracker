package com.example.cargotracker.booking.infrastructure.acl;

import com.example.cargotracker.billing.application.internal.outboundservices.acl
        .BillableCargoPort;
import com.example.cargotracker.booking.application.internal.outboundservices.acl
        .CargoCorrectionRequests;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.CargoExceptions;
import com.example.cargotracker.booking.infrastructure.repositories.BillableCargoRow;
import com.example.cargotracker.booking.infrastructure.repositories.BookingQueryMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link BillableCargoPort} の実装（ACL のアダプタ。US21）。
 *
 * <p><strong>返すのは素の値だけである</strong>（ADR-005）。{@code Cargo} を返すと
 * Billing が Booking のドメインを参照することになる（ArchUnit ルール 4）。
 *
 * <p><strong>SQL では他 BC のテーブルを触らない</strong>（ADR-015）。
 * 訂正の申請中は {@code CargoCorrectionRequests}（→ Handling）、
 * 例外の有無は {@code CargoExceptions}（→ Tracking）で受け取る。
 * <strong>ここで JOIN すると、越境の許容リストがまた伸びる</strong>
 * （IT13 で 2 行返したばかりである）。
 *
 * <p><strong>距離係数は区間数である。</strong> 実距離を持っていないため、経由の多さを
 * 距離の代わりに使う。ADR-008 の概算式とは別物である
 * （あちらは経路候補の並べ替え用であり、請求には使わない）。
 */
@Component
public class BillableCargoAdapter implements BillableCargoPort {

    private final BookingQueryMapper mapper;
    private final CargoCorrectionRequests correctionRequests;
    private final CargoExceptions cargoExceptions;

    public BillableCargoAdapter(
            BookingQueryMapper mapper,
            CargoCorrectionRequests correctionRequests,
            CargoExceptions cargoExceptions) {
        this.mapper = mapper;
        this.correctionRequests = correctionRequests;
        this.cargoExceptions = cargoExceptions;
    }

    @Override
    public List<BillableCargoSummary> findPending() {
        List<BillableCargoRow> rows = mapper.findBillable();
        if (rows.isEmpty()) {
            return List.of();
        }

        // **まとめて 1 回ずつ聞く**（IT13 レビュー C4）。1 行ごとに問い合わせると、
        // 一覧を描くコストが件数に比例して増える
        Set<String> bookingIds = rows.stream()
                .map(row -> row.getBookingId().toString())
                .collect(java.util.stream.Collectors.toSet());
        Set<String> trackingNumbers = rows.stream()
                .map(BillableCargoRow::getTrackingNumber)
                .filter(n -> n != null && !n.isBlank())
                .collect(java.util.stream.Collectors.toSet());

        Set<String> withCorrection =
                correctionRequests.findBookingIdsWithPendingCorrection(bookingIds);
        Set<String> withException =
                cargoExceptions.findTrackingNumbersWithException(trackingNumbers);

        return rows.stream()
                .map(row -> toSummary(row, withCorrection, withException))
                // **訂正・取り消しの申請中は請求対象に出さない**（IT12 持ち越し C8）。
                // 取り消されるかもしれない引取をもとに請求書を出すと、
                // 出した後で引取が無かったことになる
                .filter(cargo -> !cargo.correctionRequested())
                .toList();
    }

    @Override
    public Optional<BillableCargoSummary> findByBookingId(String bookingId) {
        if (bookingId == null || bookingId.isBlank()) {
            return Optional.empty();
        }
        UUID id;
        try {
            id = UUID.fromString(bookingId.strip());
        } catch (IllegalArgumentException e) {
            // **形式の違う ID を例外にしない。** 画面が 500 になる
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.findBillableByBookingId(id))
                .map(row -> toSummary(
                        row,
                        // 1 件なので、まとめる相手がいない。同じ経路を通す
                        correctionRequests.findBookingIdsWithPendingCorrection(
                                Set.of(row.getBookingId().toString())),
                        row.getTrackingNumber() == null || row.getTrackingNumber().isBlank()
                                ? Set.of()
                                : cargoExceptions.findTrackingNumbersWithException(
                                        Set.of(row.getTrackingNumber()))));
    }

    /**
     * 引取が済んでいるか（C14）。
     *
     * <p><strong>正典は {@code BookingStatus} である。</strong> 状態名を文字列で
     * 比べ直すと、状態を足したときに片方だけ古くなる。
     *
     * <p><strong>読めない状態を引取済みにしない。</strong> 未知の値は
     * 「まだ引取が済んでいない」として扱う。請求は後からでもできるが、
     * 誤って出した請求書は取り消す業務になる。
     */
    private static boolean claimed(BillableCargoRow row) {
        if (row.getBookingStatus() == null) {
            return false;
        }
        try {
            return com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus
                    .valueOf(row.getBookingStatus()).isDeliveredOrLater();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 1 行を表示用に変換する。
     *
     * <p><strong>訂正の有無と例外の有無は、まとめて引いた集合から読む</strong>（C4）。
     * ここで 1 件ずつ問い合わせると、一覧のコストが件数に比例する。
     */
    private static BillableCargoSummary toSummary(
            BillableCargoRow row, Set<String> withCorrection, Set<String> withException) {
        String bookingId = row.getBookingId().toString();
        return new BillableCargoSummary(
                bookingId,
                row.getTrackingNumber() == null ? "" : row.getTrackingNumber(),
                new BillableCargoSummary.Shipper(
                        row.getShipperId().toString(),
                        row.getShipperName(),
                        "CORPORATE".equals(row.getShipperType())),
                new BillableCargoSummary.Route(row.getOrigin(), row.getDestination()),
                new BillableCargoSummary.Cargo(
                        row.getCargoType(),
                        row.getWeight(),
                        // 区間数をそのまま運ぶ。**0 本のときに拒むのは BillableCargo の仕事である**
                        // （ここで隠すと「なぜ請求できないか」が業務の言葉にならない）
                        BigDecimal.valueOf(Math.max(row.getLegCount(), 0))),
                new BillableCargoSummary.State(
                        // **引取が済んだかは予約状態が決める**（IT13 レビュー C14）。
                        // 「一覧のクエリが返した行だから引取済み」と読み替えると、
                        // 抽出条件を変えた瞬間に判定だけが古くなる
                        claimed(row),
                        // **いつ引取が済んだか**（C1）。経理の月次はこの日付で締める
                        row.getClaimedAt(),
                        withCorrection.contains(bookingId),
                        row.getTrackingNumber() != null
                                && withException.contains(row.getTrackingNumber())));
    }
}
