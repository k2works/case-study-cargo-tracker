package com.example.cargotracker.billing.application.internal.commandservices;

import com.example.cargotracker.billing.domain.model.valueobjects.InvoiceId;
import com.example.cargotracker.billing.domain.model.aggregates.Reminder;
import com.example.cargotracker.billing.domain.repository.ReminderRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 督促したことを記録する（IT14 レビュー C3）。
 *
 * <p><strong>催促そのものは人が行う</strong>（ADR-006 により外部へは送らない）。
 * ここに残すのは<strong>いつ・誰が・何を伝えたか</strong>であり、
 * 二重の催促と、誰も連絡しないまま月をまたぐことの両方を防ぐ。
 */
@Service
public class RemindInvoiceCommandService {

    /** 督促の結果。 */
    public enum Outcome {
        /** 記録した。 */
        RECORDED,
        /** 請求書が見つからない。 */
        NOT_FOUND,
        /** 入力が業務の条件を満たさない。 */
        REJECTED
    }

    /**
     * 督促の結果。
     *
     * @param reason 拒んだ理由（<strong>記録できたときは {@code null}</strong>）
     */
    public record Result(Outcome outcome, String reason) {
    }

    private final ReminderRepository repository;

    /** 業務のタイムゾーンの時計。<strong>督促した日時をここで決める。</strong> */
    private final Clock clock;

    public RemindInvoiceCommandService(ReminderRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * 督促したことを記録する。
     *
     * <p><strong>長すぎる入力で 500 を出さない。</strong> 業務の言葉で拒む。
     */
    @Transactional
    public Result recordReminder(String invoiceNumber, String note, String actor) {
        Reminder reminder;
        try {
            reminder = new Reminder(clock.instant(), actor, note);
        } catch (IllegalArgumentException e) {
            return new Result(Outcome.REJECTED, e.getMessage());
        }
        if (!repository.save(InvoiceId.of(invoiceNumber), reminder)) {
            return new Result(Outcome.NOT_FOUND, null);
        }
        return new Result(Outcome.RECORDED, null);
    }
}
