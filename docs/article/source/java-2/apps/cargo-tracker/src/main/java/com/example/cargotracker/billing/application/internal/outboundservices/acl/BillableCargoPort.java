package com.example.cargotracker.billing.application.internal.outboundservices.acl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 請求の対象となる貨物を読む出力ポート（Billing → Booking の ACL。US21）。
 *
 * <p><strong>料金の算出に要るのは輸送実績である</strong>（受入基準 2）。
 * 経路・重量・貨物種別を Booking から受け取り、Billing の中で料金に換える。
 *
 * <p>運ぶのは<strong>素の値だけ</strong>である（ADR-005）。{@code Cargo} を渡すと
 * Billing が Booking のドメインを参照することになる（ArchUnit ルール 4）。
 */
public interface BillableCargoPort {

    /**
     * 請求書がまだ無い引取済みの貨物（請求対象一覧）。
     *
     * <p><strong>訂正・取り消しの申請中は含めない</strong>（IT12 持ち越し C8）。
     * 取り消されるかもしれない引取をもとに請求書を出すと、
     * 出した後で引取が無かったことになる。
     */
    List<BillableCargoSummary> findPending();

    /** 1 件（料金算出の画面で読む）。 */
    Optional<BillableCargoSummary> findByBookingId(String bookingId);

    /**
     * 請求に要る輸送実績（US21 の受入基準 2）。
     *
     * <p><strong>意味のまとまりごとに入れ子へ分けている</strong>（IT17 の R6）。
     * 以前は 14 個の要素が一列に並び、{@code claimed} / {@code correctionRequested} /
     * {@code hasException} という<strong>3 つの {@code boolean} が隣り合って</strong>いた
     * — 取り違えても何も起きず、請求してよい貨物の判定だけが静かに変わる。
     *
     * <p>ポートが呼ぶ名前は委譲するアクセサで残している。
     *
     * @param bookingId      予約 ID
     * @param trackingNumber 追跡番号。<strong>経理担当者が貨物を指す値である</strong>
     * @param shipper        荷主（<strong>誰への請求か</strong>と割引の可否）
     * @param route          出発地と目的地
     * @param cargo          貨物の仕様
     * @param state          請求してよいかを決める状態
     */
    record BillableCargoSummary(
            String bookingId,
            String trackingNumber,
            Shipper shipper,
            Route route,
            Cargo cargo,
            State state) {

        /**
         * 荷主。
         *
         * @param id        荷主 ID
         * @param name      荷主名。<strong>誰への請求かが読めないと確認できない</strong>
         * @param corporate 法人荷主か。<strong>割引の可否を決める</strong>
         */
        public record Shipper(String id, String name, boolean corporate) { }

        /**
         * 経路。
         *
         * @param origin      出発地
         * @param destination 目的地
         */
        public record Route(String origin, String destination) { }

        /**
         * 貨物の仕様。
         *
         * @param type           貨物種別（{@code GENERAL} / {@code HAZARDOUS} /
         *                       {@code REFRIGERATED}）。<strong>表示名への変換は
         *                       Billing 側で行う</strong>（{@code CargoTypeFactor} は
         *                       Billing のドメインであり、他 BC から参照させない）
         * @param weightKg       重量（kg）
         * @param distanceFactor 距離係数（区間数から求める）
         */
        public record Cargo(String type, BigDecimal weightKg, BigDecimal distanceFactor) { }

        /**
         * 請求してよいかを決める状態。
         *
         * @param claimed             引取が完了しているか
         * @param claimedAt           引取が済んだ日時（IT13 レビュー C1）。
         *                            <strong>経理の月次はこの日付で締める。</strong>
         *                            列が無かったころの引取は {@code null}
         * @param correctionRequested 訂正・取り消しが申請中か
         * @param hasException        例外（遅延・破損等）が起きているか。
         *                            <strong>料金調整の対象があることを示す</strong>
         */
        public record State(
                boolean claimed,
                java.time.Instant claimedAt,
                boolean correctionRequested,
                boolean hasException) { }

        // --- 呼び出し側が使う名前（委譲するアクセサ）---

        /** @return 荷主 ID */
        public String shipperId() {
            return shipper.id();
        }

        /** @return 荷主名 */
        public String shipperName() {
            return shipper.name();
        }

        /** @return 法人荷主か */
        public boolean corporate() {
            return shipper.corporate();
        }

        /** @return 出発地 */
        public String origin() {
            return route.origin();
        }

        /** @return 目的地 */
        public String destination() {
            return route.destination();
        }

        /** @return 貨物種別 */
        public String cargoType() {
            return cargo.type();
        }

        /** @return 重量（kg） */
        public BigDecimal weightKg() {
            return cargo.weightKg();
        }

        /** @return 距離係数 */
        public BigDecimal distanceFactor() {
            return cargo.distanceFactor();
        }

        /** @return 引取が完了しているか */
        public boolean claimed() {
            return state.claimed();
        }

        /** @return 引取が済んだ日時 */
        public java.time.Instant claimedAt() {
            return state.claimedAt();
        }

        /** @return 訂正・取り消しが申請中か */
        public boolean correctionRequested() {
            return state.correctionRequested();
        }

        /** @return 例外が起きているか */
        public boolean hasException() {
            return state.hasException();
        }
    }
}
