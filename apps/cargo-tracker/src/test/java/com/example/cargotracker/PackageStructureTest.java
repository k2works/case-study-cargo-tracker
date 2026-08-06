package com.example.cargotracker;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

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
                            "com.example.cargotracker.shared..")
                    .because("トップレベルパッケージは Bounded Context と 1 対 1 である（ADR-002）");
}
