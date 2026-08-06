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
    private final ShipperContact contact;
    private final long version;

    public Shipper(
            ShipperId id,
            ShipperCode shipperCode,
            ShipperType shipperType,
            ShipperName name,
            Email email,
            Phone phone,
            Address address) {
        this(id, shipperCode, shipperType, name, new ShipperContact(email, phone, address), 0L);
    }

    public Shipper(
            ShipperId id,
            ShipperCode shipperCode,
            ShipperType shipperType,
            ShipperName name,
            ShipperContact contact,
            long version) {
        if (id == null || shipperCode == null || shipperType == null
                || name == null || contact == null) {
            throw new IllegalArgumentException("荷主の必須項目が欠けています");
        }
        this.id = id;
        this.shipperCode = shipperCode;
        this.shipperType = shipperType;
        this.name = name;
        this.contact = contact;
        this.version = version;
    }

    /**
     * 荷主名を訂正する（US32）。
     *
     * <p><strong>Setter を生やさない。</strong> 「名前を書き換える」ではなく
     * 「荷主名を訂正する」という業務のことばで名づけることで、
     * どの操作が業務操作ログに残るべきかがコードから読める。
     */
    public Shipper rename(ShipperName newName) {
        return new Shipper(id, shipperCode, shipperType, newName, contact, version);
    }

    /** 連絡先（メールアドレス・電話番号）を訂正する（US32）。 */
    public Shipper changeContact(Email newEmail, Phone newPhone) {
        return new Shipper(id, shipperCode, shipperType, name,
                new ShipperContact(newEmail, newPhone, contact.address()), version);
    }

    /** 住所を訂正する（US32）。 */
    public Shipper relocate(Address newAddress) {
        return new Shipper(id, shipperCode, shipperType, name,
                new ShipperContact(contact.email(), contact.phone(), newAddress), version);
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

    public ShipperContact contact() {
        return contact;
    }

    public Email email() {
        return contact.email();
    }

    public Phone phone() {
        return contact.phone();
    }

    public Address address() {
        return contact.address();
    }

    /** 楽観的ロック用のバージョン。読み取り時の値であり、更新が成功すると DB 側で 1 増える。 */
    public long version() {
        return version;
    }
}
