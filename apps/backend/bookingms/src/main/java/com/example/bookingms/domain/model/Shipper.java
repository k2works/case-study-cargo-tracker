package com.example.bookingms.domain.model;

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

    private Shipper(Long id, String shipperCode, ShipperType type, String name, String email,
            String address, String phone) {
        this.id = id;
        this.shipperCode = shipperCode;
        this.type = type;
        this.name = name;
        this.email = email;
        this.address = address;
        this.phone = phone;
    }

    /** 新規に受け入れる。ここでだけ入力を検査する。 */
    public static Shipper register(ShipperType type, String name, String email, String address,
            String phone) {
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
        return new Shipper(null, null, type, name, email, address, phone);
    }

    /**
     * 永続化された行から復元する。
     *
     * <p>ここでは検査しない。検査を後から足すと、その規則が無かったころの行が読めなくなる。
     */
    public static Shipper restore(Long id, String shipperCode, ShipperType type, String name,
            String email, String address, String phone) {
        return new Shipper(id, shipperCode, type, name, email, address, phone);
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
}
