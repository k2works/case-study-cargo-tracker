package com.example.cargotracker.support;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * テストの荷主・貨物を用意する<strong>唯一の場所</strong>（IT14 の C4 / IT15 の M6）。
 *
 * <p><strong>{@code cargo} に列を足すたび、直す場所が 1 か所であること</strong>が
 * 本クラスの存在理由である。IT16 で数え直したとき、{@code INSERT INTO cargo} は
 * <strong>39 クラス・41 か所</strong>にあり、列の組み合わせは 6 通りに分かれていた。
 *
 * <p><strong>返済を 2 度落としたあいだに、負債は記録より大きく育っていた。</strong>
 * IT14 の C4 は「5 クラス」、IT15 の M6 は「7 クラス」と数えていた。実際は
 * その 5 倍以上あった — <strong>数え方が違えば、落とす判断の前提も違う。</strong>
 *
 * <p><strong>既定値は「ごく普通の予約」である。</strong> 大阪発ロサンゼルス行きの
 * 一般貨物 1000kg、期限は 60 日後、仮受付・未割当。テストが関心を持つ属性だけを
 * 上書きする — <strong>関心の無い値が並ぶと、何がそのテストの条件なのか読めなくなる。</strong>
 *
 * <pre>{@code
 * var cargo = CargoFixture.on(jdbcTemplate)
 *         .status("IN_TRANSIT", "ROUTED")
 *         .trackingNumber("TRK-20260401-0001")
 *         .insert();
 * // cargo.bookingId() / cargo.cargoId() / cargo.shipperId()
 * }</pre>
 */
public final class CargoFixture {

    /** 用意した貨物の識別子。 */
    public record Inserted(UUID bookingId, long cargoId, UUID shipperId, String trackingNumber) {
    }

    private final JdbcTemplate jdbc;

    private UUID shipperId;
    private String shipperNamePrefix = "テスト商事";
    private String shipperEmail;
    private String shipperPhone = "06-1234-5678";
    private String shipperType = "INDIVIDUAL";
    private String contractNumber;
    private String discountRate = "0.0000";
    private String cargoType = "GENERAL";
    private int weight = 1000;
    private String origin = "JPOSA";
    private String destination = "USLAX";
    private int arrivalDeadlineDays = 60;
    private LocalDate arrivalDeadline;
    private String bookingStatus = "PRELIMINARY";
    private String routingStatus = "NOT_ROUTED";
    private String trackingNumber;
    private String consigneeName;
    private String claimCode;
    private String unNumber;
    private String hazardousClass;
    private String properShippingName;

    private CargoFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static CargoFixture on(JdbcTemplate jdbc) {
        return new CargoFixture(jdbc);
    }

    /** 既存の荷主に紐づける。<strong>指定しなければ荷主も新しく作る。</strong> */
    public CargoFixture shipper(UUID id) {
        this.shipperId = id;
        return this;
    }

    /** 新しく作る荷主の名前の接頭辞（画面の表示を確かめるテスト向け）。 */
    public CargoFixture shipperNamePrefix(String prefix) {
        this.shipperNamePrefix = prefix;
        return this;
    }

    /** 荷主の連絡先を指定する（督促の導線を確かめるテスト向け）。{@code null} は空のまま。 */
    public CargoFixture shipperContact(String email, String phone) {
        this.shipperEmail = email;
        this.shipperPhone = phone;
        return this;
    }

    /** 法人荷主にする（US03 / US22）。<strong>契約番号が無い法人は DB が拒む。</strong> */
    public CargoFixture corporate(String contract, String rate) {
        this.shipperType = "CORPORATE";
        this.contractNumber = contract;
        this.discountRate = rate;
        return this;
    }

    /**
     * 契約割引率を持つ法人荷主にする（US22）。契約番号は採番に任せる。
     *
     * <p><strong>個人と法人を引数で切り替える形を残さない。</strong> 呼び出し側が
     * {@code corporate ? ... : ...} を並べると、<strong>切り替えの分だけ
     * 準備の中身が読めなくなる</strong>。法人が要るテストだけがこれを呼ぶ。
     */
    public CargoFixture corporate(String rate) {
        this.shipperType = "CORPORATE";
        this.discountRate = rate;
        return this;
    }

    public CargoFixture cargoType(String type) {
        this.cargoType = type;
        return this;
    }

    public CargoFixture weight(int kilograms) {
        this.weight = kilograms;
        return this;
    }

    public CargoFixture route(String originUnlocode, String destinationUnlocode) {
        this.origin = originUnlocode;
        this.destination = destinationUnlocode;
        return this;
    }

    /** 到着期限を「今日から n 日後」で置く。<strong>固定日付を書かない</strong>（業務日付は動く）。 */
    public CargoFixture arrivalDeadlineInDays(int days) {
        this.arrivalDeadlineDays = days;
        return this;
    }

    /**
     * 到着期限を明示する。
     *
     * <p><strong>アプリの時計で決めた日付を渡すときに使う。</strong>
     * 既定は DB の {@code CURRENT_DATE} を基準にするが、業務のタイムゾーンで
     * 「今日」を決めるテストでは<strong>両者がずれる時間帯がある</strong>。
     */
    public CargoFixture arrivalDeadline(LocalDate date) {
        this.arrivalDeadline = date;
        return this;
    }

    public CargoFixture status(String booking, String routing) {
        this.bookingStatus = booking;
        this.routingStatus = routing;
        return this;
    }

    public CargoFixture trackingNumber(String number) {
        this.trackingNumber = number;
        return this;
    }

    public CargoFixture consignee(String name) {
        this.consigneeName = name;
        return this;
    }

    public CargoFixture claimCode(String code) {
        this.claimCode = code;
        return this;
    }

    /** 危険物の申告（US05）。<strong>3 つ揃って初めて申告になる。</strong> */
    public CargoFixture hazardous(String un, String hazardClass, String shippingName) {
        this.cargoType = "HAZARDOUS";
        this.unNumber = un;
        this.hazardousClass = hazardClass;
        this.properShippingName = shippingName;
        return this;
    }

    /** 用意する。荷主を指定していなければ荷主も作る。 */
    public Inserted insert() {
        UUID owner = shipperId != null ? shipperId : insertShipper();
        UUID bookingId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status,
                    tracking_number, consignee_name, claim_code,
                    un_number, hazardous_class, proper_shipping_name)
                VALUES (?, ?, ?, ?, ?, ?,
                        COALESCE(CAST(? AS DATE), CURRENT_DATE + CAST(? AS INTEGER)),
                        ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                bookingId, owner, cargoType, weight,
                origin, destination, arrivalDeadline, arrivalDeadlineDays,
                bookingStatus, routingStatus,
                trackingNumber, consigneeName, claimCode,
                unNumber, hazardousClass, properShippingName);

        Long cargoId = jdbc.queryForObject(
                "SELECT id FROM cargo WHERE booking_id = ?", Long.class, bookingId);
        if (cargoId == null) {
            throw new IllegalStateException("用意した貨物が読み戻せません: " + bookingId);
        }
        return new Inserted(bookingId, cargoId, owner, trackingNumber);
    }

    /**
     * 荷主を 1 件作る。
     *
     * <p><strong>採番は本番の経路（シーケンス）で行う。</strong> MAX+1 の自前採番は
     * シーケンスと衝突し、原因でない他のテストが UNIQUE 制約で落ちる。
     */
    private UUID insertShipper() {
        Long seq = jdbc.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID id = UUID.randomUUID();
        String email = shipperEmail != null
                ? shipperEmail : "fixture-%d@example.com".formatted(seq);
        // **契約番号が無い法人は DB が拒む**（chk_shipper_corporate_contract）
        String contract = contractNumber != null || !"CORPORATE".equals(shipperType)
                ? contractNumber : "CT-%06d".formatted(seq);
        jdbc.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street, contract_number, discount_rate)
                VALUES (?, ?, ?, ?, ?, ?,
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1',
                        ?, CAST(? AS NUMERIC))
                """,
                id, "SHP-%06d".formatted(seq), shipperType, shipperNamePrefix,
                email, shipperPhone, contract, discountRate);
        return id;
    }
}
