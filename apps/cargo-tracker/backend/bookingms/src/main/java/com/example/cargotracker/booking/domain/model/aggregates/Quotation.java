package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.booking.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.booking.domain.model.commands.CreateQuotationCommand;
import com.example.cargotracker.booking.domain.model.events.QuotationCreatedEvent;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.EstimatedAmount;
import com.example.cargotracker.booking.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.booking.domain.model.valueobjects.QuotationTerms;
import com.example.cargotracker.booking.domain.model.valueobjects.QuotedRoute;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;

/**
 * 輸送見積（UC01 / US01）。
 *
 * <p><b>{@code Cargo} には混ぜない。</b> 見積は予約の前段にあり、<b>予約に至らない
 * 見積が存在する</b>。予約の集約に見積の状態を持たせると、「予約されていない予約」
 * という読みにくい状態ができる。</p>
 *
 * <p><b>候補は外で数えてから渡す</b>（{@code CreateQuotationCommand}）。集約は
 * 経路探索を知らない——ACL 越しの問い合わせを集約に持ち込むと、相手が落ちている
 * あいだ集約そのものが使えなくなる。</p>
 *
 * <p>不変条件は {@code domain-model.md} が正典。1（5 項目・出発地 ≠ 目的地）は
 * {@code RouteSpecification} とここが、2（請求と同じ料率）は
 * {@code QuotationEstimator} と契約テストが、3（予約との食い違いは断らず項目名で
 * 知らせる）は {@link #diffAgainst} が守る。</p>
 */
@EventSourced(idType = String.class, tagKey = "quotationId")
public class Quotation {

    /** 見積の有効期間。<b>業務が決める数字</b>だが、この版では固定でよい。 */
    private static final int VALID_DAYS = 30;

    private String quotationId;
    private String originUnLocode;
    private String destinationUnLocode;
    private LocalDate arrivalDeadline;
    private String cargoType;
    private BigDecimal weightKg;

    /**
     * 候補の数。
     *
     * <p><b>概算額そのものは持たない。</b> 読む側が集約の中に居ない——金額は
     * 投影が持ち、画面はそちらを読む。<b>読む側の無い状態を先に持たない</b>
     * （IT12 の「常に 0 の列」と同じ形。SpotBugs が実際に指摘した）。</p>
     *
     * <p>候補の数だけは残す。<b>0 件かどうか</b>の判断に要る（画面が数え直さない）。</p>
     */
    private int candidateCount;

    @EntityCreator
    public Quotation() {
        // Axon がイベント再生で呼ぶ。
    }

    /**
     * 見積を作る（US01 §受入基準 1・4）。
     *
     * <p><b>static ではなくインスタンスのハンドラにする。</b> 両方置くと、集約が
     * 既に存在しても static のほうが呼ばれ、同じ見積番号で 2 度作れる
     * （IT2 で実測）。</p>
     *
     * <p><b>候補 0 件でも作る</b>（正典の不変条件）。断ると、営業担当者は
     * 「間に合う経路がありません」という答えを荷主に返せない。</p>
     */
    @CommandHandler
    public String create(CreateQuotationCommand command, EventAppender appender, Clock clock) {
        if (quotationId != null) {
            throw new IllegalTransition("見積 " + quotationId + " はすでに作られています");
        }
        QuotationId id = new QuotationId(command.quotationId());
        if (command.routeSpecification() == null) {
            throw new BusinessRuleViolation("出発地・目的地・希望期限は必須です");
        }
        if (command.cargoType() == null) {
            throw new BusinessRuleViolation("貨物種別は必須です");
        }
        if (command.weight() == null) {
            throw new BusinessRuleViolation("重量は必須です");
        }
        if (command.cargoType() == CargoType.HAZARDOUS
                && command.hazardousDeclaration() == null) {
            // 危険物は申告が要る（{@code CargoSpecification} と同じ規則）。見積の
            // 時点で聞いておかないと、予約で初めて断られて出し直しになる。
            throw new BusinessRuleViolation("危険物には危険物申告が必要です");
        }

        List<QuotedRoute> candidates = command.candidates() == null
                ? List.of() : command.candidates();
        LocalDate today = LocalDate.ofInstant(clock.instant(),
                com.example.cargotracker.shared.infrastructure.time
                        .BusinessClockConfiguration.BUSINESS_ZONE);

        List<QuotationCreatedEvent.Candidate> rows = new ArrayList<>();
        int seq = 1;
        for (QuotedRoute route : candidates) {
            rows.add(new QuotationCreatedEvent.Candidate(seq++, route.voyageNumbers(),
                    String.join(" > ", route.ports()),
                    route.transitDays(), route.estimatedCharge().roundToUnit().amount(),
                    route.estimatedCharge().currency(), route.overdueDays()));
        }

        EstimatedAmount cheapest = cheapestOf(candidates);
        appender.append(new QuotationCreatedEvent(id.value(),
                command.routeSpecification().origin().unLocode().value(),
                command.routeSpecification().destination().unLocode().value(),
                command.routeSpecification().arrivalDeadline(),
                command.cargoType().name(),
                command.weight().kilograms(),
                command.hazardousDeclaration() == null
                        ? null : command.hazardousDeclaration().imoClass(),
                command.hazardousDeclaration() == null
                        ? null : command.hazardousDeclaration().unNumber(),
                cheapest.roundToUnit().amount(), cheapest.currency(),
                today.plusDays(VALID_DAYS), rows,
                command.createdBy(), clock.instant()));
        return id.value();
    }

    /**
     * 予約との食い違いを項目名で返す（不変条件 3）。
     *
     * <p><b>断らない。</b> 荷主の事情は見積のあとで変わる——重量が増えることも、
     * 期限が延びることもある。断ると業務が止まるので、<b>何がどう違うかを
     * 知らせる</b>にとどめる。</p>
     *
     * <p><b>「何から何へ」まで返す。</b> 項目名だけでは、営業担当者は見積を
     * 開き直して見比べることになる。</p>
     *
     * @return 違いの説明。<b>空なら見積どおり</b>
     */
    public List<String> diffAgainst(BookCargoCommand command) {
        if (quotationId == null) {
            throw new IllegalTransition("見積がありません");
        }
        // **比較は 1 か所**（QuotationTerms）。予約の受付側（QuotationDiff）と
        // 別々に書くと、片方だけが正しくてもう片方が違いを見落とす。
        return terms().differencesAgainst(QuotationTerms.of(command));
    }

    /** この見積が示した 5 項目。 */
    private QuotationTerms terms() {
        return new QuotationTerms(originUnLocode, destinationUnLocode, arrivalDeadline,
                cargoType, weightKg);
    }

    /** 期限に間に合う候補があるか。<b>集約が答える</b>（画面に数え直させない）。 */
    public boolean hasCandidate() {
        return candidateCount > 0;
    }

    @EventSourcingHandler
    void on(QuotationCreatedEvent event) {
        this.quotationId = event.quotationId();
        this.originUnLocode = event.originUnLocode();
        this.destinationUnLocode = event.destinationUnLocode();
        this.arrivalDeadline = event.arrivalDeadline();
        this.cargoType = event.cargoType();
        this.weightKg = event.weightKg();
        this.candidateCount = event.candidates().size();
    }

    /**
     * いちばん安い候補の概算。
     *
     * <p><b>期限に間に合う候補を優先する。</b> 間に合わない候補のほうが安いのは
     * よくあることで、それを見積の額として示すと「その額では間に合わない」と
     * 伝えそこねる。間に合う候補が無ければ、間に合わない候補の中から選ぶ。</p>
     */
    private static EstimatedAmount cheapestOf(List<QuotedRoute> candidates) {
        return candidates.stream()
                .filter(QuotedRoute::meetsDeadline)
                .map(QuotedRoute::estimatedCharge)
                .min(java.util.Comparator.comparing(EstimatedAmount::amount))
                .or(() -> candidates.stream()
                        .map(QuotedRoute::estimatedCharge)
                        .min(java.util.Comparator.comparing(EstimatedAmount::amount)))
                .orElse(EstimatedAmount.zero());
    }

}
