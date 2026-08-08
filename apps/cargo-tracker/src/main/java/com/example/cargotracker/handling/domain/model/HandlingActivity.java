package com.example.cargotracker.handling.domain.model;

import com.example.cargotracker.shared.domain.model.Location;
import java.time.Instant;

/**
 * 荷役作業。Handling モジュールの集約ルート（US15 / US16）。
 *
 * <p><strong>予定と違う作業も記録する。</strong> 判定するのは「予定どおりか」で
 * あって「登録してよいか」ではない。拒否すると、実際に起きた作業がどこにも
 * 残らず、追跡は現実と食い違ったまま進む。
 */
public class HandlingActivity {

    private final HandledCargo cargo;
    private final HandlingDetails details;
    private final Instant completionTime;
    private final Location location;
    private final String note;
    private final String operatorName;
    private final long version;

    private HandlingActivity(
            HandledCargo cargo,
            HandlingDetails details,
            Instant completionTime,
            Location location,
            String note,
            String operatorName,
            long version) {
        this.cargo = cargo;
        this.details = details;
        this.completionTime = completionTime;
        this.location = location;
        this.note = note;
        this.operatorName = operatorName;
        this.version = version;
    }

    /**
     * 荷役作業を登録する。
     *
     * <p>積込・荷降しは航海番号が必須である（デシジョンテーブル）。
     * <strong>どの便に対する作業か分からない記録は、誤配の判定にも追跡の表示にも
     * 使えない。</strong>
     */
    public static HandlingActivity register(RegisterHandlingCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("荷役登録コマンドは必須です");
        }
        if (command.cargo() == null) {
            throw new IllegalArgumentException("作業の対象となった貨物は必須です");
        }
        if (command.details() == null) {
            throw new IllegalArgumentException("荷役種別は必須です");
        }
        if (command.completionTime() == null) {
            throw new IllegalArgumentException("作業日時は必須です");
        }
        if (command.location() == null) {
            throw new IllegalArgumentException("作業場所は必須です");
        }
        // 種別ごとの要否は HandlingDetails が守る（組み立てた時点で成立している）
        return new HandlingActivity(
                command.cargo(), command.details(), command.completionTime(),
                command.location(), command.note(), command.operatorName(), 0L);
    }

    /** 永続化された状態から復元する。 */
    public static HandlingActivity reconstruct(
            HandledCargo cargo,
            HandlingDetails details,
            Instant completionTime,
            Location location,
            String note,
            String operatorName,
            long version) {
        return new HandlingActivity(cargo, details, completionTime, location,
                note, operatorName, version);
    }

    /**
     * 予約の予定ルートと突き合わせる（{@code domain-model.md} のデシジョンテーブル）。
     *
     * <p>照合先は種別で変わる。
     *
     * <ul>
     *   <li>受領 — 予約の出発地。違えば<strong>警告</strong></li>
     *   <li>積込 — 旅程に「この便でこの港から積む」区間があるか。無ければ<strong>誤配</strong></li>
     *   <li>荷降し — 旅程に「この便でこの港で降ろす」区間があるか。無ければ<strong>誤配</strong></li>
     *   <li>引取 — 予約の目的地。違えば<strong>警告</strong></li>
     *   <li>通関 — 場所を照合しない</li>
     * </ul>
     *
     * <p><strong>経路が割り当てられていない貨物の積込・荷降しは誤配にしない。</strong>
     * 比べる予定そのものが無いためである。予定が無いことを「予定と違う」と呼ぶと、
     * 誤配の件数が意味を失う。
     */
    public HandlingValidation isValidFor(CargoSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("予約の写しは必須です");
        }
        return switch (type()) {
            case CUSTOMS -> HandlingValidation.asPlanned();
            case RECEIVE -> matchesEndpoint(snapshot.origin(), "出発地");
            case CLAIM -> matchesClaim(snapshot);
            case LOAD, UNLOAD -> matchesItinerary(snapshot);
        };
    }

    private HandlingValidation matchesEndpoint(String expected, String label) {
        if (location.unlocode().equals(expected)) {
            return HandlingValidation.asPlanned();
        }
        return HandlingValidation.warning(
                "予約の%s（%s）と違う場所での%sです: %s"
                        .formatted(label, expected, type().displayName(),
                                location.unlocode()));
    }

    /**
     * 引取を照合する（US16）。<strong>場所と荷受人の両方を見る。</strong>
     *
     * <p><strong>どちらか一方しか出さないと、作業員はもう片方に気づかない。</strong>
     * IT7 の最初の実装は場所しか見ておらず、画面のコメントが
     * 「予約の荷受人と違えば警告を出す」と宣言していたのに実装が無かった
     * （レビュー H4。{@code ClaimConfirmation.matchesConsignee} がテストからしか
     * 呼ばれていない状態＝配線漏れのサインだった）。
     *
     * <p><strong>いずれも拒否しない。</strong> 場所の違いは港の中の別ゲート、
     * 氏名の違いは代理受領であり、どちらも業務上あり得る。
     */
    private HandlingValidation matchesClaim(CargoSnapshot snapshot) {
        HandlingValidation place = matchesEndpoint(snapshot.destination(), "目的地");
        if (claimConfirmation() == null
                || claimConfirmation().matchesConsignee(snapshot.consigneeName())) {
            return place;
        }
        String consigneeWarning = "予約の荷受人（%s）と違う方が受け取っています: %s。担当者メモに理由を記入してください"
                .formatted(snapshot.consigneeName(), claimConfirmation().consigneeName());
        // 場所も違う場合は両方伝える
        return HandlingValidation.warning(place.hasMessage()
                ? place.message() + " / " + consigneeWarning
                : consigneeWarning);
    }

    private HandlingValidation matchesItinerary(CargoSnapshot snapshot) {
        if (!snapshot.isRouted()) {
            // 予定が無いので比べようがない。**「予定と違う」とは呼ばない**
            return HandlingValidation.warning(
                    "経路が割り当てられていないため、予定ルートと照合できません");
        }
        boolean onPlan = snapshot.itineraryLegs().stream().anyMatch(leg ->
                leg.voyageNumber().equals(voyageNumber().value())
                        && location.unlocode().equals(
                                type() == HandlingType.LOAD
                                        ? leg.loadLocation() : leg.unloadLocation()));
        if (onPlan) {
            return HandlingValidation.asPlanned();
        }
        // **次にすることを書く。** 誤配は追跡担当者が組み直す必要があり、
        // 作業員に見えるのはこの 1 回きりである（追跡側に一覧も印もまだ無い。
        // IT6 レビュー H13。一覧は US28 / IT11）。
        // 「予定と違います」だけでは、作業員は何をすればよいか分からない
        return HandlingValidation.misrouted(
                "予定ルートに無い%sです: %s 便 / %s。追跡担当者に連絡してください"
                        .formatted(type().displayName(), voyageNumber().value(),
                                location.unlocode()));
    }

    /** 作業の対象となった貨物（読み取った番号と引き当てた予約のひと組）。 */
    public HandledCargo cargo() {
        return cargo;
    }

    public CargoBookingId cargoBookingId() {
        return cargo.bookingId();
    }

    /** 荷役種別と、その種別に応じて要る詳細のひと組。 */
    public HandlingDetails details() {
        return details;
    }

    public HandlingType type() {
        return details.type();
    }

    public Instant completionTime() {
        return completionTime;
    }

    public Location location() {
        return location;
    }

    /** 航海番号。積込・荷降し以外では {@code null}。 */
    public HandlingVoyageNumber voyageNumber() {
        return details.voyageNumber();
    }

    /**
     * 読み取った追跡番号。
     *
     * <p><strong>予約 ID から逆算しない。</strong> 誤読した場合に、その痕跡が消える。
     */
    public ScannedTrackingNumber scannedTrackingNumber() {
        return cargo.scannedTrackingNumber();
    }

    /** 荷受人確認。引取以外では {@code null}（US16）。 */
    public ClaimConfirmation claimConfirmation() {
        return details.claimConfirmation();
    }

    /**
     * 担当者メモ。任意。
     *
     * <p>代理受領の理由など、<strong>「なぜそうしたか」</strong>を残す。
     * 警告を出しておきながら理由を書く場所が無いと、警告は読み飛ばされる。
     */
    public String note() {
        return note;
    }

    /** 作業員名。任意。 */
    public String operatorName() {
        return operatorName;
    }

    public long version() {
        return version;
    }
}
