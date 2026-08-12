package com.example.cargotracker.billing.infrastructure.repositories;

import com.example.cargotracker.billing.domain.model.valueobjects.InvoiceId;
import com.example.cargotracker.billing.domain.model.aggregates.Reminder;
import com.example.cargotracker.billing.domain.repository.ReminderRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** {@link ReminderRepository} の MyBatis 実装（IT14 レビュー C3）。 */
@Repository
public class MyBatisReminderRepository implements ReminderRepository {

    private final ReminderMapper mapper;

    public MyBatisReminderRepository(ReminderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean save(InvoiceId invoiceId, Reminder reminder) {
        ReminderRecord row = new ReminderRecord();
        row.setInvoiceNumber(invoiceId.value());
        row.setRemindedAt(reminder.remindedAt());
        row.setRemindedBy(reminder.remindedBy());
        row.setNote(reminder.note());
        // **0 件は「請求書が無い」である。** 例外にせず、画面が業務の言葉で伝える
        return mapper.insert(row) == 1;
    }

    @Override
    public List<Reminder> findByInvoiceId(InvoiceId invoiceId) {
        return mapper.findByInvoiceNumber(invoiceId.value()).stream()
                .map(row -> new Reminder(
                        row.getRemindedAt(), row.getRemindedBy(), row.getNote()))
                .toList();
    }
}
