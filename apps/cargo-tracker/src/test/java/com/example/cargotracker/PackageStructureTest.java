package com.example.cargotracker;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * パッケージ構成が設計の正典と一致していることを検証する。
 *
 * <p>ここで検証するのは「構成が崩れていないこと」であり、実装の有無ではない。
 * BC を追加・改名したときに本テストが落ちることで、ArchUnit の slices ルール
 * （docs/design/test_strategy.md §3.3 ルール 4）が前提としている
 * 「トップレベルパッケージ = BC 境界」が守られていることを担保する。
 *
 * <p><strong>test_strategy.md §3.3 が定める 6 ルールのうち、本クラスはルール 5 のみを実装している。</strong>
 * 依存方向のルール（1・3）、Spring アノテーション禁止（2）、BC 間参照禁止（4）、
 * 共有カーネルの範囲（6）は、対象となるクラスが 1 つも存在しない現時点では
 * ArchUnit の {@code failOnEmptyShould} により失敗する。
 *
 * <p>これを {@code allowEmptyShould(true)} で通すことはしない。**何も検査していないルールを
 * 緑にすると、実装が入った後も検査されていないことに気づけなくなる。** 各ルールは
 * 対応する層の実装を追加するイテレーションで、実際に違反を検出できることを確認したうえで有効化する。
 */
@AnalyzeClasses(packages = "com.example.cargotracker")
class PackageStructureTest {

    /**
     * すべてのクラスが期待する境界付けられたコンテキストのいずれかに属すること（ルール 5）。
     *
     * <p><strong>handling は独立した BC である</strong>（ADR-010。ADR-002 を置き換えた）。
     * shared は共有カーネルであり、格納してよいのは Location と ShipperId のみ（ADR-005）。
     */
    @ArchTest
    static final ArchRule すべてのクラスはBC集合のいずれかに属する =
            classes()
                    .should().resideInAnyPackage(
                            "com.example.cargotracker",
                            "com.example.cargotracker.booking..",
                            "com.example.cargotracker.shipper..",
                            "com.example.cargotracker.routing..",
                            "com.example.cargotracker.tracking..",
                            // 荷役。**独立した BC である**（ADR-010）
                            "com.example.cargotracker.handling..",
                            "com.example.cargotracker.billing..",
                            "com.example.cargotracker.estimation..",
                            "com.example.cargotracker.shared..",
                            // 認証・認可の支援サブドメイン。共有カーネルではない（ADR-005）
                            "com.example.cargotracker.security..",
                            // テストの共通基盤。BC ではないため個別に許可する。
                            "com.example.cargotracker.support..",
                            // 受け入れシナリオのテスト。BC をまたぐ業務の流れを確かめる
                            // ためのものであり、**本番コードは置かない**
                            "com.example.cargotracker.scenario..")
                    .because("トップレベルパッケージは Bounded Context と 1 対 1 である（ADR-010）");

    /**
     * ルール 1: ドメイン層がインフラ層に依存しない。
     *
     * <p>依存方向は infrastructure から domain への一方向でなければならない。
     */
    @ArchTest
    static final ArchRule ドメイン層はインフラ層に依存しない =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .because("ドメイン層はインフラ層を直接参照してはならない");

    /**
     * ルール 2: ドメイン層で Spring のアノテーションを使わない。
     *
     * <p>ドメインオブジェクトは POJO でなければならない。フレームワークに縛られた
     * ドメインは、フレームワークの都合で設計が歪む。
     */
    @ArchTest
    static final ArchRule ドメイン層はSpringに依存しない =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                    .because("ドメイン層は Spring フレームワークに依存してはならない");

    /**
     * ADR-004: ドメイン層が MyBatis の型に依存しない。
     *
     * <p><strong>「ドメイン層はインフラ層に依存しない」だけでは足りない。</strong>
     * {@code org.apache.ibatis} は {@code ..infrastructure..} に含まれないため、
     * ドメインの集約に {@code @Results} や {@code @Param} を直接付けても、
     * 依存方向のルールは緑のまま通る。ADR-004 は「ドメインモデルの
     * {@code @Entity} は不要になる」ことを利点として挙げているが、
     * それを強制する仕組みが無かった（IT2 タスク 0-1 の棚卸しで発覚）。
     */
    @ArchTest
    static final ArchRule ドメイン層はMyBatisに依存しない =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("org.apache.ibatis..")
                    .because("永続化技術はドメインモデルに現れてはならない（ADR-004）");

    /**
     * ルール 3: アプリケーション層がインフラ層を直接参照しない。
     *
     * <p>参照はドメイン層で定義した出力ポート経由に限る（DIP）。
     */
    @ArchTest
    static final ArchRule アプリケーション層はインフラ層に依存しない =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .because("アプリケーション層はポート経由でのみインフラ層と通信する");

    /**
     * ルール 6: 共有カーネルに置いてよいのは {@code Location} と {@code ShipperId} のみ（ADR-005）。
     *
     * <p>検査対象は共有カーネルそのもの、すなわち {@code shared.domain.model} である。
     * {@code shared.infrastructure} 配下は横断的な技術基盤（TypeHandler・共通画面）であり
     * 共有カーネルではないため対象外とする。
     *
     * <p><strong>共有カーネルは放置すると必ず肥大化する。</strong> 「ここに置けば全 BC から使える」は
     * 常に正しく聞こえるが、1 クラス増えるたびに全 BC の再ビルドとレビューを強制する。
     * 人間のレビューではなくテストで固定する。認証の {@code UserAccount} / {@code Role} は
     * この規律に従い {@code security} サブドメインへ分離した。
     */
    @ArchTest
    static final ArchRule 共有カーネルはLocationとShipperIdのみ =
            classes()
                    .that().resideInAPackage("com.example.cargotracker.shared.domain.model")
                    .should().haveSimpleNameStartingWith("Location")
                    .orShould().haveSimpleNameStartingWith("ShipperId")
                    .because("共有カーネルの構成要素は Location と ShipperId のみである（ADR-005）");

    /**
     * CQRS: {@code interfaces} 層がリポジトリを直接参照しない（IT1 ふりかえり Try T6）。
     *
     * <p>画面が必要とするのは「表示したい形のデータ」であり、集約ではない。
     * Controller がリポジトリを直接呼ぶと、集約を 1 件ずつ読んで画面で組み立てる
     * コードが自然に生まれ、**一覧を開くたびに N+1 のクエリが飛ぶ**。
     * 読み取りはクエリサービス（{@code application..queryservices}）に集約し、
     * 表示に最適化した SQL で 1 回で取る。
     */
    @ArchTest
    static final ArchRule 画面層はリポジトリを直接参照しない =
            noClasses()
                    .that().resideInAPackage("..interfaces..")
                    .should().dependOnClassesThat().resideInAPackage("..domain.repository..")
                    .because("読み取りはクエリサービスを経由する（CQRS のクエリ側）");

    /**
     * ルール 4: 異なる Bounded Context 間でクラスを直接参照しない。
     *
     * <p>通信はドメインイベントまたは ACL ポート経由でなければならない。
     *
     * <p><strong>IT1 で有効化した。</strong> 保留の理由は「対象クラスが 0 件だと
     * failOnEmptyShould で落ちる」ことだったが、shipper と security に実クラスが
     * 存在する時点でその理由は消えている。次のイテレーションで最初の BC 間依存
     * （booking → shipper）が生まれる瞬間に守るルールが無いと、ACL を挟まない直接参照が
     * 入ったことに気づけず、後から剥がすコストが跳ね上がる。
     *
     * <p>共有カーネル（{@code shared}）と認証・認可（{@code security}）への参照は除外する。
     * 前者は共有が前提であり、後者は全 BC の入口に横断的に効くため、
     * いずれも BC 間の結合ではない（ADR-005 / ADR-007）。
     */
    @ArchTest
    static final ArchRule コンテキスト間でクラスを直接参照しない =
            SlicesRuleDefinition.slices()
                    .matching("com.example.cargotracker.(*)..")
                    .should().notDependOnEachOther()
                    // ignoreDependency の引数は (依存元, 依存先)。**向きを逆にすると
                    // 「shared から他 BC への依存」を無視することになり、狙いと反対に働く**
                    .ignoreDependency(alwaysTrue(), resideInAPackage("..shared.."))
                    .ignoreDependency(alwaysTrue(), resideInAPackage("..security.."))
                    // ACL ポートは BC 間の**唯一の許可された越境点**である（ADR-005 / ADR-007）。
                    // ポートを定義するのは利用側の BC、実装するのは提供側の BC であり、
                    // 実装クラスからポートへの参照は必然的に BC をまたぐ。
                    //
                    // **除外するのはポートのパッケージだけである。** 集約や値オブジェクトへの
                    // 直接参照（booking → shipper.domain.model.Shipper 等）は引き続き落ちる。
                    // ここを "..shipper.." のように BC 単位で緩めると、ACL を通す動機が消える。
                    .ignoreDependency(alwaysTrue(), resideInAPackage("..outboundservices.acl.."))
                    // テストの共通基盤（統合テストの基底クラス）。BC ではない
                    .ignoreDependency(alwaysTrue(), resideInAPackage("..support.."))
                    // **support から各 BC への参照も除外する。** 方言スモークテストは
                    // すべてのクエリサービスを呼ぶ必要があり、BC をまたぐのが仕事である。
                    // テスト専用の基盤であり本番コードではない。
                    // 逆方向（本番コードの BC 間参照）は引き続き落ちる
                    .ignoreDependency(resideInAPackage("..support.."), alwaysTrue())
                    // **受け入れシナリオのテストも除外する。** BC をまたぐ業務の流れ
                    // （経路を確定すると貨物の経路状態が変わる）は、どちらかの BC の
                    // 内側に置くと必ず相手の型を参照する。**またぐのが仕事**である。
                    // 本番コードは scenario に置かない（ルール 5 が縛る）
                    .ignoreDependency(resideInAPackage("..scenario.."), alwaysTrue())
                    .because("BC 間の通信はドメインイベントまたは ACL 経由でなければならない");

    /**
     * <strong>ドメイン層とアプリケーション層に BC 間の参照を作らない</strong>（ADR-012）。
     *
     * <p>JIG のパッケージ図では Booking ⇄ Routing と Booking ⇄ Tracking が循環している。
     * 上のルール 4 が緑なのは、<strong>ACL ポートのパッケージを依存先から除外している</strong>
     * ためであり、「循環していない」ことを主張してはいない。<strong>JIG のほうが正直である。</strong>
     *
     * <p>循環の一部は業務上の必要から残す（ADR-012）。追跡番号の発行も経路の割り当ても
     * 可否をその場で画面に返す必要があり、イベントにすると<strong>拒否の理由を返せなくなる</strong>。
     *
     * <p><strong>残すからこそ、閉じ込める場所を検査で固定する。</strong> BC 間の参照が
     * {@code infrastructure/acl} のアダプタに閉じている限り、次の性質が保たれる。
     *
     * <ul>
     *   <li>ドメインとアプリケーションは<strong>自分の BC のポート interface しか知らない</strong>
     *       （依存性逆転が成立している）</li>
     *   <li>循環はコンパイル単位の都合であって、<strong>業務ロジックの相互依存ではない</strong></li>
     *   <li>BC を別モジュールへ切り出すとき、<strong>動かすのはアダプタだけで済む</strong></li>
     * </ul>
     *
     * <p><strong>いま 0 件であることと、明日も 0 件であることは別である。</strong>
     * ADR-012 を書いた時点の実測が 0 件であり、それを本ルールで固定する。
     */
    @ArchTest
    static final ArchRule ドメイン層とアプリケーション層はBCをまたがない =
            SlicesRuleDefinition.slices()
                    .matching("com.example.cargotracker.(*)..")
                    .should().notDependOnEachOther()
                    .ignoreDependency(alwaysTrue(), resideInAPackage("..shared.."))
                    .ignoreDependency(alwaysTrue(), resideInAPackage("..security.."))
                    // **ここでは ACL ポートを除外しない。** 除外すると、この検査が
                    // 守ろうとしている「アプリケーション層が他 BC のポートを直接持たない」
                    // ことを検査できなくなる
                    //
                    // 依存元をドメイン層・アプリケーション層に絞る。
                    // infrastructure/acl のアダプタは対象外である（そこが唯一の越境点）
                    .ignoreDependency(
                            resideOutsideOfPackages("..domain..", "..application.."),
                            alwaysTrue())
                    .because("ADR-012: BC 間の参照は infrastructure/acl のアダプタに閉じ込める。"
                            + "ドメイン層とアプリケーション層は自分の BC のポートしか知らない");
}
