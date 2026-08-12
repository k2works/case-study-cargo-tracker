package com.example.cargotracker.shipper.domain.model.aggregates;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperCode;
import com.example.cargotracker.shipper.domain.model.valueobjects.CorporateContract;
import com.example.cargotracker.shipper.domain.model.valueobjects.Address;
import com.example.cargotracker.shipper.domain.model.valueobjects.Email;
import com.example.cargotracker.shipper.domain.model.valueobjects.Phone;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperContact;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperName;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperType;

import com.example.cargotracker.shared.domain.model.valueobjects.ShipperId;
import javax.annotation.concurrent.Immutable;

/**
 * 荷主。Shipper Context の集約ルート。
 *
 * <p>他の境界付けられたコンテキストのクラスを直接参照しない。Booking からの参照は
 * {@code ShipperExistenceChecker} ACL ポートを経由する（{@code domain-model.md}）。
 *
 * <p><strong>法人荷主をサブタイプにしない</strong>（US03 / IT7 設計反映 #12）。
 * {@code domain-model.md} は {@code CorporateShipper extends Shipper} と定義していたが、
 * 本クラスは {@code final} かつ不変であり、継承すると
 * <strong>「法人なのに契約が無い」「個人なのに契約がある」組み合わせを型で防げない</strong>。
 * 契約は {@link CorporateContract} としてひと組で持つ。
 */
@Immutable
public final class Shipper {

    private final ShipperId id;
    private final ShipperCode shipperCode;
    private final ShipperType shipperType;
    private final ShipperName name;
    private final ShipperContact contact;

    /**
     * 法人契約（US03）。<strong>個人荷主では {@code null} である。</strong>
     *
     * <p>種別と契約は常にひと組であり、{@link #requireConsistent} が両者の整合を守る。
     */
    private final CorporateContract contract;
    private final long version;

    public Shipper(
            ShipperId id,
            ShipperCode shipperCode,
            ShipperType shipperType,
            ShipperName name,
            Email email,
            Phone phone,
            Address address) {
        this(id, shipperCode, shipperType, name,
                new ShipperContact(email, phone, address), null, 0L);
    }

    public Shipper(
            ShipperId id,
            ShipperCode shipperCode,
            ShipperType shipperType,
            ShipperName name,
            ShipperContact contact,
            CorporateContract contract,
            long version) {
        if (id == null || shipperCode == null || shipperType == null
                || name == null || contact == null) {
            throw new IllegalArgumentException("荷主の必須項目が欠けています");
        }
        requireConsistent(shipperType, contract);
        this.id = id;
        this.shipperCode = shipperCode;
        this.shipperType = shipperType;
        this.name = name;
        this.contact = contact;
        this.contract = contract;
        this.version = version;
    }

    /**
     * 種別と契約の整合を守る。
     *
     * <p><strong>「法人なのに契約が無い」と「個人なのに契約がある」の両方を弾く。</strong>
     * 前者は割引の根拠を請求書に書けず、後者は個人に存在しないはずの契約が付く。
     * DB の {@code chk_shipper_corporate_contract} と同じ不変条件である。
     */
    private static void requireConsistent(ShipperType type, CorporateContract contract) {
        if (type == ShipperType.CORPORATE && contract == null) {
            throw new IllegalArgumentException("法人荷主には契約番号と契約割引率が必要です");
        }
        if (type == ShipperType.INDIVIDUAL && contract != null) {
            throw new IllegalArgumentException("個人荷主に法人契約は指定できません");
        }
    }

    /**
     * 荷主名を訂正する（US32）。
     *
     * <p><strong>Setter を生やさない。</strong> 「名前を書き換える」ではなく
     * 「荷主名を訂正する」という業務のことばで名づけることで、
     * どの操作が業務操作ログに残るべきかがコードから読める。
     */
    public Shipper rename(ShipperName newName) {
        return new Shipper(id, shipperCode, shipperType, newName, contact, contract, version);
    }

    /** 連絡先（メールアドレス・電話番号）を訂正する（US32）。 */
    public Shipper changeContact(Email newEmail, Phone newPhone) {
        return new Shipper(id, shipperCode, shipperType, name,
                new ShipperContact(newEmail, newPhone, contact.address()), contract, version);
    }

    /** 住所を訂正する（US32）。 */
    public Shipper relocate(Address newAddress) {
        return new Shipper(id, shipperCode, shipperType, name,
                new ShipperContact(contact.email(), contact.phone(), newAddress),
                contract, version);
    }

    /** 個人荷主を新規登録する。 */
    public static Shipper registerIndividual(
            ShipperId id, ShipperCode code, ShipperName name,
            Email email, Phone phone, Address address) {
        return new Shipper(id, code, ShipperType.INDIVIDUAL, name, email, phone, address);
    }

    /**
     * 法人荷主を新規登録する（US03）。
     *
     * <p>契約番号と契約割引率は必須である。<strong>精算のときに
     * どの契約に基づく割引かを説明できる必要がある</strong>（US22）。
     */
    public static Shipper registerCorporate(
            ShipperId id, ShipperCode code, ShipperName name,
            Email email, Phone phone, Address address, CorporateContract contract) {
        return new Shipper(id, code, ShipperType.CORPORATE, name,
                new ShipperContact(email, phone, address), contract, 0L);
    }

    /**
     * 契約条件を訂正する（US03）。
     *
     * <p><strong>Setter を生やさない。</strong> 「契約を書き換える」ではなく
     * 「契約条件を訂正する」という業務のことばで名づけることで、
     * どの操作が業務操作ログに残るべきかがコードから読める
     * （契約割引率の変更は監査ログに記録する。{@code non_functional.md} §4.4）。
     *
     * @throws IllegalStateException 個人荷主のとき
     */
    public Shipper changeContract(CorporateContract newContract) {
        if (!isCorporate()) {
            throw new IllegalStateException("個人荷主に法人契約は指定できません");
        }
        return new Shipper(id, shipperCode, shipperType, name, contact, newContract, version);
    }

    /** 法人契約。個人荷主では {@code null}。 */
    public CorporateContract contract() {
        return contract;
    }

    /** 契約を持つか（個人荷主は常に {@code false}）。 */
    public boolean hasContract() {
        return contract != null;
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
