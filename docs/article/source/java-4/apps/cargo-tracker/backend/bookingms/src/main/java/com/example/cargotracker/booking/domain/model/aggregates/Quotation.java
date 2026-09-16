package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.booking.domain.model.commands.CreateQuotationCommand;
import com.example.cargotracker.booking.domain.model.events.QuotationCreatedEvent;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.EstimatedAmount;
import com.example.cargotracker.booking.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.booking.domain.model.valueobjects.QuotedRoute;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
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
 * 知らせる）は {@code application.QuotationDiff} が守る。</p>
 *
 * <p><b>食い違いを集約が数えない理由。</b> 差分は予約を受け付けた<b>あと</b>に
 * 出す表示で、業務の判断（受け付けるかどうか）には使わない——集約を復元するのは
 * 「判断に使う状態」が要るときだけである。比較そのものは
 * {@code QuotationTerms} の 1 か所にあり、集約が持っても同じものを呼ぶだけに
 * なるので、<b>本番から呼ばれないメソッドを置かない</b>（定義済み未使用は
 * 配線漏れのサインで、読む人が「ここが守っている」と誤読する）。</p>
 */
@EventSourced(idType = String.class, tagKey = "quotationId")
public class Quotation {

    /** 見積の有効期間。<b>業務が決める数字</b>だが、この版では固定でよい。 */
    private static final int VALID_DAYS = 30;

    /**
     * 見積番号。**復元されたかどうかを見る唯一の状態**である。
     *
     * <p><b>5 項目を持たない。</b> 持つと「集約が見積の中身を守っている」ように
     * 読めるが、実際に守るのは作るときの検査（{@code RouteSpecification} と
     * ここ）と、比較の 1 か所（{@code QuotationTerms}）である。<b>読まない状態を
     * 復元すると、次の書き手が「ここから読める」と思って使う</b>——読み取りは
     * 投影の仕事で、集約を復元するのは判断に使う状態が要るときだけである。</p>
     */
    private String quotationId;

    @EntityCreator
    public Quotation() {
        // Axon がイベント再生で呼ぶ。
    }

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

    @EventSourcingHandler
    void on(QuotationCreatedEvent event) {
        // **同じ見積番号で二度作らせないための復元**である。中身は読まない
        // （読まない状態を復元すると、次の書き手が使ってしまう）。
        this.quotationId = event.quotationId();
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
