package com.example.bookingms.domain.model;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 荷主。貨物を発送する主体で、個人・法人の 2 種別を持つ。
 *
 * <p>荷主コードは永続化の経路で採番する。集約側で MAX+1 のように自前で採番すると
 * シーケンスと衝突し、原因でない他の処理が UNIQUE 制約で落ちる。
 */
public final class Shipper {

    /** 重複判定と連絡に使えることだけを確かめる緩い検査。厳密な妥当性は送信時にしか分からない。 */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final Long id;
    private final String shipperCode;
    private final ShipperType type;
    private final String name;
    private final String email;
    private final String address;
    private final String phone;
    private final CorporateContract contract;

    private Shipper(Long id, String shipperCode, ShipperType type, String name, String email,
            String address, String phone, CorporateContract contract) {
        this.id = id;
        this.shipperCode = shipperCode;
        this.type = type;
        this.name = name;
        this.email = email;
        this.address = address;
        this.phone = phone;
        this.contract = contract;
    }

    /** 契約情報を伴わない新規登録（個人、または契約情報を後で入れる場合の入口）。 */
    public static Shipper register(ShipperType type, String name, String email, String address,
            String phone) {
        return register(type, name, email, address, phone, null);
    }

    /**
     * 新規に受け入れる。ここでだけ入力を検査する。
     *
     * <p>契約情報は法人のときだけ持てる。割引率は未設定を許す（交渉が終わっていないことがある）が、
     * 契約番号は必須とする。契約番号の無い法人を許すと、US22（法人割引）で全件の追加入力が発生する。
     */
    public static Shipper register(ShipperType type, String name, String email, String address,
            String phone, CorporateContract contract) {
        if (type == null) {
            throw new IllegalArgumentException("荷主種別は必須です");
        }
        if (isBlank(name)) {
            throw new IllegalArgumentException("氏名/社名は必須です");
        }
        if (isBlank(address)) {
            throw new IllegalArgumentException("住所は必須です");
        }
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("メールアドレスの形式が不正です: " + email);
        }
        if (type == ShipperType.CORPORATE) {
            if (contract == null) {
                throw new IllegalArgumentException("法人荷主には契約番号が必要です");
            }
        } else if (contract != null) {
            throw new IllegalArgumentException("契約番号と割引率は法人荷主にだけ設定できます");
        }
        return new Shipper(null, null, type, name, email, address, phone, contract);
    }

    /**
     * 永続化された行から復元する。
     *
     * <p>ここでは検査しない。検査を後から足すと、その規則が無かったころの行が読めなくなる。
     */
    public static Shipper restore(Long id, String shipperCode, ShipperType type, String name,
            String email, String address, String phone) {
        return restore(id, shipperCode, type, name, email, address, phone, null);
    }

    /** 契約情報を含めて復元する。ここでは検査しない。 */
    public static Shipper restore(Long id, String shipperCode, ShipperType type, String name,
            String email, String address, String phone, CorporateContract contract) {
        return new Shipper(id, shipperCode, type, name, email, address, phone, contract);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public Long id() {
        return id;
    }

    public String shipperCode() {
        return shipperCode;
    }

    public ShipperType type() {
        return type;
    }

    public String name() {
        return name;
    }

    public String email() {
        return email;
    }

    public String address() {
        return address;
    }

    public String phone() {
        return phone;
    }

    /** 法人か。種別の比較を呼び出し側に散らかさない。 */
    public boolean isCorporate() {
        return type == ShipperType.CORPORATE;
    }

    /** 法人契約の条件。個人は空を返す。 */
    public Optional<CorporateContract> contract() {
        return Optional.ofNullable(contract);
    }

    public Optional<ContractNumber> contractNumber() {
        return contract().map(CorporateContract::number);
    }

    /** 割引率。未設定は空を返す。0% と「未設定」は違う。 */
    public Optional<DiscountRate> discountRate() {
        return contract().flatMap(CorporateContract::rate);
    }
}
