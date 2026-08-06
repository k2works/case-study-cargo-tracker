package com.example.cargotracker.shipper.domain.model;

import com.example.cargotracker.shared.domain.model.ShipperId;
import javax.annotation.concurrent.Immutable;

/**
 * 荷主。Shipper Context の集約ルート。
 *
 * <p>他の境界付けられたコンテキストのクラスを直接参照しない。Booking からの参照は
 * {@code ShipperExistenceChecker} ACL ポートを経由する（{@code domain-model.md}）。
 *
 * <p>法人荷主（契約番号・契約割引率）は US03 で扱う。本クラスは種別を保持するのみとする。
 */
@Immutable
public final class Shipper {

    private final ShipperId id;
    private final ShipperCode shipperCode;
    private final ShipperType shipperType;
    private final ShipperName name;
    private final Email email;
    private final Phone phone;
    private final Address address;

    public Shipper(
            ShipperId id,
            ShipperCode shipperCode,
            ShipperType shipperType,
            ShipperName name,
            Email email,
            Phone phone,
            Address address) {
        if (id == null || shipperCode == null || shipperType == null
                || name == null || email == null || address == null) {
            throw new IllegalArgumentException("荷主の必須項目が欠けています");
        }
        this.id = id;
        this.shipperCode = shipperCode;
        this.shipperType = shipperType;
        this.name = name;
        this.email = email;
        this.phone = phone == null ? Phone.empty() : phone;
        this.address = address;
    }

    /** 個人荷主を新規登録する。 */
    public static Shipper registerIndividual(
            ShipperId id, ShipperCode code, ShipperName name,
            Email email, Phone phone, Address address) {
        return new Shipper(id, code, ShipperType.INDIVIDUAL, name, email, phone, address);
    }

    public boolean isCorporate() {
        return shipperType == ShipperType.CORPORATE;
    }

    public ShipperId id() {
        return id;
    }

    public ShipperCode shipperCode() {
        return shipperCode;
    }

    public ShipperType shipperType() {
        return shipperType;
    }

    public ShipperName name() {
        return name;
    }

    public Email email() {
        return email;
    }

    public Phone phone() {
        return phone;
    }

    public Address address() {
        return address;
    }
}
