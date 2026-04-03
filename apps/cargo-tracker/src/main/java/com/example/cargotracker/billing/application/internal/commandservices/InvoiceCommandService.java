package com.example.cargotracker.billing.application.internal.commandservices;

import com.example.cargotracker.billing.domain.model.aggregates.FreightId;
import com.example.cargotracker.billing.domain.model.aggregates.Invoice;
import com.example.cargotracker.billing.domain.model.aggregates.InvoiceId;
import com.example.cargotracker.billing.domain.model.commands.ConfirmPaymentCommand;
import com.example.cargotracker.billing.domain.model.commands.GenerateInvoiceCommand;
import com.example.cargotracker.billing.domain.model.repository.FreightChargeRepository;
import com.example.cargotracker.billing.domain.model.repository.InvoiceRepository;
import com.example.cargotracker.billing.domain.model.valueobjects.ChargeStatus;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Transactional
public class InvoiceCommandService {

    private final InvoiceRepository invoiceRepository;
    private final FreightChargeRepository freightChargeRepository;
    private final BookingRepository bookingRepository;

    public InvoiceCommandService(InvoiceRepository invoiceRepository,
                                 FreightChargeRepository freightChargeRepository,
                                 BookingRepository bookingRepository) {
        this.invoiceRepository = invoiceRepository;
        this.freightChargeRepository = freightChargeRepository;
        this.bookingRepository = bookingRepository;
    }

    public InvoiceId generateInvoice(GenerateInvoiceCommand command) {
        // H2: 重複チェック — 同一 freightChargeId の精算書が既に存在する場合はエラー
        invoiceRepository.findByFreightChargeId(command.freightChargeId())
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "この輸送料金の精算書はすでに存在します: " + command.freightChargeId());
                });

        var charge = freightChargeRepository.findById(FreightId.of(command.freightChargeId()))
                .orElseThrow(() -> new IllegalArgumentException("輸送料金が見つかりません: " + command.freightChargeId()));

        // H3: bookingId 整合性検証 — FreightCharge 取得後、重複チェックの後
        if (!charge.getBookingId().equals(command.bookingId())) {
            throw new IllegalArgumentException("輸送料金の予約 ID とリクエストの予約 ID が一致しません");
        }

        if (charge.getStatus() != ChargeStatus.CONFIRMED) {
            throw new IllegalStateException("輸送料金が確定されていません");
        }

        var invoice = Invoice.generate(
                InvoiceId.generate(),
                charge.getBookingId(),
                charge.getId().value().toString(),
                charge.getTotalAmount(),
                command.dueDate() != null ? command.dueDate() : LocalDate.now().plusDays(30)
        );

        invoiceRepository.save(invoice);
        return invoice.getId();
    }

    public void confirmPayment(ConfirmPaymentCommand command) {
        var invoice = invoiceRepository.findById(InvoiceId.of(command.invoiceId()))
                .orElseThrow(() -> new IllegalArgumentException("精算書が見つかりません: " + command.invoiceId()));
        var booking = bookingRepository.findById(BookingId.of(invoice.getBookingId()))
                .orElseThrow(() -> new IllegalArgumentException("予約が見つかりません: " + invoice.getBookingId()));

        invoice.confirmPayment();
        booking.settle();
        invoiceRepository.save(invoice);
        bookingRepository.save(booking);
    }
}
