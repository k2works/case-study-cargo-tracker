package com.example.cargotracker;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;

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
     * <p>handling は tracking のサブパッケージである（ADR-002）。独立した BC ではない。
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
                            "com.example.cargotracker.billing..",
                            "com.example.cargotracker.estimation..",
                            "com.example.cargotracker.shared..",
                            // 認証・認可の支援サブドメイン。共有カーネルではない（ADR-005）
                            "com.example.cargotracker.security..",
                            // テストの共通基盤。BC ではないため個別に許可する。
                            "com.example.cargotracker.support..")
                    .because("トップレベルパッケージは Bounded Context と 1 対 1 である（ADR-002）");

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
                    // テストの共通基盤（統合テストの基底クラス）。BC ではない
                    .ignoreDependency(alwaysTrue(), resideInAPackage("..support.."))
                    .because("BC 間の通信はドメインイベントまたは ACL 経由でなければならない");
}
