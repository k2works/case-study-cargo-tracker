package com.example.cargotracker.billing.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InvoiceMapper {

    void insert(@Param("row") InvoiceRecord row);

    void update(@Param("row") InvoiceRecord row);

    InvoiceRecord findById(@Param("id") String id);

    InvoiceRecord findByFreightChargeId(@Param("freightChargeId") String freightChargeId);

    List<InvoiceRecord> findAll();
}
