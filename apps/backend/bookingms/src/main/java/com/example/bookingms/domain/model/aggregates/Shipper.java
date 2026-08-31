package com.example.bookingms.domain.model.aggregates;

import java.util.Optional;
import com.example.bookingms.domain.model.valueobjects.ContractNumber;
import com.example.bookingms.domain.model.valueobjects.CorporateContract;
import com.example.bookingms.domain.model.valueobjects.DiscountRate;
import com.example.bookingms.domain.model.valueobjects.EmailAddress;
import com.example.bookingms.domain.model.valueobjects.ShipperProfile;
import com.example.bookingms.domain.model.valueobjects.ShipperType;

/**
 * 荷主。貨物を発送する主体で、個人・法人の 2 種別を持つ。
 *
 * <p>荷主コードは永続化の経路で採番する。集約側で MAX+1 のように自前で採番すると
 * シーケンスと衝突し、原因でない他の処理が UNIQUE 制約で落ちる。
 */
public final class Shipper {

    /**
     * シミュレーション由来の荷主コードの帯（[ADR-030] 決定 3）。
     *
     * <p><strong>識別の根拠はこの帯 1 本にする。</strong>別に列を持つと、帯と列が
     * 食い違う行が生まれ、どちらが正しいかを決める規則がまた要る。貨物・請求書・追跡は
     * すべて荷主から辿れるため、帯だけで判断できる。
     */
    public static final String SIMULATED_CODE_PREFIX = "SIM-";

    private final Long id;
    private final String shipperCode;
    private final ShipperType type;
    private final ShipperProfile profile;
    private final CorporateContract contract;

    /**
     * シミュレーション由来か。
     *
     * <p>採番済みなら<strong>荷主コードの帯から読む</strong>。採番前（登録の途中）だけ、
     * 登録時の指示を持つ——どちらの帯で採番するかを永続化の経路に伝えるためである。
     */
    private final boolean simulatedRegistration;

    private Shipper(Long id, String shipperCode, ShipperType type, ShipperProfile profile,
            CorporateContract contract) {
        this(id, shipperCode, type, profile, contract, false);
    }

    private Shipper(Long id, String shipperCode, ShipperType type, ShipperProfile profile,
            CorporateContract contract, boolean simulatedRegistration) {
        this.id = id;
        this.shipperCode = shipperCode;
        this.type = type;
        this.profile = profile;
        this.contract = contract;
        this.simulatedRegistration = simulatedRegistration;
    }

    /** シミュレーション由来か（[ADR-030] 決定 3）。 */
    public boolean simulated() {
        return shipperCode == null ? simulatedRegistration : isSimulatedCode(shipperCode);
    }

    /**
     * その荷主コードがシミュレーションの帯か。
     *
     * <p>荷主の集約を組み立てずに判定したい場所（一覧の絞り込み）から使う。
     * <strong>判定はここ 1 つに置く</strong>——同じ判定を呼ぶ側に書き直すと、
     * 帯を変えたときに書き直した側だけが古いまま残る。
     */
    public static boolean isSimulatedCode(String shipperCode) {
        return shipperCode != null && shipperCode.startsWith(SIMULATED_CODE_PREFIX);
    }

    /**
     * シミュレーションが作る荷主（[ADR-030] 決定 3）。
     *
     * <p><strong>入口を分ける。</strong>真偽値の引数で分けると、呼び出し側を読んだだけでは
     * 何が true なのか分からない。分けておけば、実業務の登録経路からは呼べない。
     */
    public static Shipper registerSimulated(ShipperType type, String name, String email,
            String address, String phone) {
        Shipper shipper = register(type, name, email, address, phone);
        return new Shipper(shipper.id, shipper.shipperCode, shipper.type, shipper.profile,
                shipper.contract, true);
    }

    /** 契約情報を伴わない新規登録（個人、または契約情報を後で入れる場合の入口）。 */
    public static Shipper register(ShipperType type, String name, String email, String address,
            String phone) {
        return register(type, ShipperProfile.of(name, email, address, phone), null);
    }

    /**
     * 新規に受け入れる。ここでだけ入力を検査する。
     *
     * <p>契約情報は法人のときだけ持てる。割引率は未設定を許す（交渉が終わっていないことがある）が、
     * 契約番号は必須とする。契約番号の無い法人を許すと、US22（法人割引）で全件の追加入力が発生する。
     */
    public static Shipper register(ShipperType type, String name, String email, String address,
            String phone, CorporateContract contract) {
        return register(type, ShipperProfile.of(name, email, address, phone), contract);
    }

    /** 新規に受け入れる。ここでだけ入力を検査する。 */
    public static Shipper register(
            ShipperType type, ShipperProfile profile, CorporateContract contract) {
        requireValid(type, profile, contract);
        return new Shipper(null, null, type, profile, contract);
    }

    /**
     * 登録済みの荷主の内容を直す（US02 / #550）。
     *
     * <p><strong>荷主コードと id は変わらない。</strong>コードが変わると、予約から見た荷主が
     * 別人になる。採番し直すのは新規登録だけである。
     *
     * <p><strong>検査は新規登録と同じものを通す。</strong>登録のときだけ検査すると、編集という
     * 緩いほうの入口から壊れた値が入る。
     *
     * <p><strong>種別は変えられない。</strong>個人と法人ではその後に成り立つ規則（契約情報を
     * 持てるか・割引の対象か）が違う。種別を変える必要が出たら、それは別の荷主である。
     */
    public Shipper edit(ShipperProfile newProfile, CorporateContract newContract) {
        requireValid(type, newProfile, newContract);
        return new Shipper(id, shipperCode, type, newProfile, newContract);
    }

    /** 新規登録と編集で同じ検査を通す。入口ごとに違う検査を書くと、緩いほうから壊れる。 */
    private static void requireValid(
            ShipperType type, ShipperProfile profile, CorporateContract contract) {
        if (type == null) {
            throw new IllegalArgumentException("荷主種別は必須です");
        }
        if (profile == null || isBlank(profile.name())) {
            throw new IllegalArgumentException("氏名/社名は必須です");
        }
        if (isBlank(profile.address())) {
            throw new IllegalArgumentException("住所は必須です");
        }
        if (profile.email() == null) {
            throw new IllegalArgumentException("メールアドレスの形式が不正です: null");
        }
        if (type == ShipperType.CORPORATE) {
            if (contract == null) {
                throw new IllegalArgumentException("法人荷主には契約番号が必要です");
            }
        } else if (contract != null) {
            throw new IllegalArgumentException("契約番号と割引率は法人荷主にだけ設定できます");
        }
    }

    /**
     * 永続化された行から復元する。
     *
     * <p>ここでは検査しない。検査を後から足すと、その規則が無かったころの行が読めなくなる。
     */
    public static Shipper restore(Long id, String shipperCode, ShipperType type, String name,
            String email, String address, String phone) {
        return restore(
                id, shipperCode, type, ShipperProfile.restore(name, email, address, phone), null);
    }

    /** 連絡先をまとめて渡して復元する。ここでは検査しない。 */
    public static Shipper restore(Long id, String shipperCode, ShipperType type,
            ShipperProfile profile, CorporateContract contract) {
        return new Shipper(id, shipperCode, type, profile, contract);
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

    /** 連絡先。氏名/社名・メールアドレス・住所・電話番号で 1 組。 */
    public ShipperProfile profile() {
        return profile;
    }

    public String name() {
        return profile.name();
    }

    public EmailAddress email() {
        return profile.email();
    }

    public String address() {
        return profile.address();
    }

    public String phone() {
        return profile.phone();
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
