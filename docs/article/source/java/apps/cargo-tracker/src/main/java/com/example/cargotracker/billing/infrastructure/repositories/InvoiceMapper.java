package com.example.cargotracker.billing.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface InvoiceMapper {

    void insert(InvoiceRecord record);

    void update(InvoiceRecord record);

    InvoiceRecord findByInvoiceNumber(String invoiceNumber);

    InvoiceRecord findByBookingId(String bookingId);

    List<InvoiceRecord> findAll();
}
