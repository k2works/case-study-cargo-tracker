package cargotracker.arch

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.{ClassFileImporter, ImportOption}
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.scalatest.funsuite.AnyFunSuite

/** ヘキサゴナルアーキテクチャの境界を ArchUnit で強制する。 architecture_backend.md のレイヤー責務とパッケージ構成例に準拠する。
  */
class HexagonalArchitectureSpec extends AnyFunSuite:

  // 本番コードのコンパイル出力のみを対象（テストクラス・Twirl 生成物は除外）
  private val classes =
    new ClassFileImporter()
      .withImportOption(_.contains("/scala-3.3.6/classes/"))
      .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
      .importPackages("cargotracker..")

  test("ルール 1: domain 層は application / infrastructure / interfaces / Play / ScalikeJDBC に依存してはならない"):
    val rule = noClasses()
      .that()
      .resideInAPackage("..cargotracker..domain..")
      .should()
      .dependOnClassesThat()
      .resideInAnyPackage(
        "..cargotracker..application..",
        "..cargotracker..infrastructure..",
        "..cargotracker..interfaces..",
        "play..",
        "scalikejdbc..",
        "controllers..",
        "views.."
      )
      .because("ドメイン層はフレームワーク・永続化・UI から独立した純粋な Scala で記述する")
    rule.check(classes)

  test("ルール 2: application 層は infrastructure / interfaces に依存してはならない"):
    val rule = noClasses()
      .that()
      .resideInAPackage("..cargotracker..application..")
      .should()
      .dependOnClassesThat()
      .resideInAnyPackage(
        "..cargotracker..infrastructure..",
        "..cargotracker..interfaces.."
      )
      .because("アプリケーション層は出力ポート（trait）に依存し、具体実装はインフラ層に置く")
    rule.check(classes)

  test("ルール 3: コンテキストの domain / application は他コンテキストの内部に直接依存してはならない（infrastructure は ACL アダプター用途で許容）"):
    val contexts = Seq("auth", "billing", "booking", "estimation", "handling", "routing", "shipper", "tracking")
    contexts.foreach { ctx =>
      val others = contexts.filter(_ != ctx).flatMap { other =>
        Seq(
          s"..cargotracker.$other.domain..",
          s"..cargotracker.$other.application..",
          s"..cargotracker.$other.infrastructure.."
        )
      }
      val rule = noClasses()
        .that()
        .resideInAnyPackage(
          s"..cargotracker.$ctx.domain..",
          s"..cargotracker.$ctx.application.."
        )
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(others*)
        .because(
          s"$ctx の domain / application は他コンテキストの実装に依存しない。infrastructure 配下の ACL アダプターのみが他コンテキストの port を呼ぶことができる"
        )
      rule.check(classes)
    }

  test("ルール 4: application.commandservices / queryservices 配下のクラスは命名規約に従う"):
    // Scala のコンパニオンオブジェクト・enum case などはクラス名が `$` で終わる合成クラスとなるため除外する
    val notScalaSynthetic: DescribedPredicate[JavaClass] =
      new DescribedPredicate[JavaClass]("not a Scala synthetic class (binary name not ending with '$')"):
        override def test(c: JavaClass): Boolean = !c.getName.endsWith("$")

    val commandRule = ArchRuleDefinition
      .classes()
      .that()
      .resideInAPackage("..cargotracker..application.commandservices..")
      .and()
      .areTopLevelClasses()
      .and(notScalaSynthetic)
      .should()
      .haveSimpleNameEndingWith("CommandService")
      .orShould()
      .haveSimpleNameEndingWith("Command")
      .because(
        "commandservices パッケージのトップレベルクラスはユースケース実行（*CommandService）か入力 DTO（*Command）のいずれかに統一する"
      )
    commandRule.check(classes)

    val queryRule = ArchRuleDefinition
      .classes()
      .that()
      .resideInAPackage("..cargotracker..application.queryservices..")
      .and()
      .areTopLevelClasses()
      .and(notScalaSynthetic)
      .should()
      .haveSimpleNameEndingWith("QueryService")
      .orShould()
      .haveSimpleNameEndingWith("Query")
      .orShould()
      .haveSimpleNameEndingWith("Command")
      .orShould()
      .haveSimpleNameEndingWith("Result")
      .orShould()
      .haveSimpleNameEndingWith("Candidate")
      .because(
        "queryservices パッケージのトップレベルクラスは CQRS Query 側の実装（*QueryService）か入出力 DTO（*Query / *Command / *Result / *Candidate）に統一する（ADR 0008）"
      )
    queryRule.check(classes)

  test("ルール 5: infrastructure のリポジトリ実装は domain の repositories trait に依存している"):
    val rule = ArchRuleDefinition
      .classes()
      .that()
      .resideInAPackage("..cargotracker..infrastructure.repositories..")
      .and()
      .haveSimpleNameStartingWith("ScalikeJdbc")
      .should()
      .dependOnClassesThat()
      .resideInAnyPackage("..cargotracker..domain.model.repositories..")
      .because("インフラ層のリポジトリ実装は domain 層の出力ポート（trait）を実装するアダプターである")
    rule.check(classes)
