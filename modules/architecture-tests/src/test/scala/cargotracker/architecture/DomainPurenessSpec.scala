package cargotracker.architecture

import com.tngtech.archunit.core.importer.{ClassFileImporter, ImportOption}
import org.scalatest.funsuite.AnyFunSuite

/** 検査 A3：domain が Pekko・Slick・GraphQL（Sangria）・ZIO を import しない（ADR-001・ADR-007）。
  *
  * 検査そのものが働くことも確かめる（メタ検査）：フィクスチャの「不純な domain」を渡すと赤になること。 最小の違反例だけでなく、実コードの形（domain
  * パッケージのクラスが禁止パッケージの型を持つ）で検査する。
  */
class DomainPurenessSpec extends AnyFunSuite:

  private val forbidden = List("org.apache.pekko..", "slick..", "sangria..", "zio..")

  private val production = new ClassFileImporter()
    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
    .importPackages("cargotracker")

  test("A3: 実コードの domain は Pekko・Slick・Sangria・ZIO に依存しない") {
    ArchRules.domainMustNotDependOn(forbidden*).check(production)
  }

  test("A3 のメタ検査: 禁止パッケージの型を持つ domain クラスを渡すと、検査は失敗する") {
    // フィクスチャ（テストのクラスパスにだけ存在）は org.scalatest の型を持つ。
    val fixture = new ClassFileImporter().importPackages("cargotracker.fixture")
    assertThrows[AssertionError] {
      ArchRules.domainMustNotDependOn("org.scalatest..").check(fixture)
    }
  }

  test("A3 のメタ検査: 禁止パッケージに触れない domain クラスなら、検査は通る") {
    val fixture = new ClassFileImporter().importPackages("cargotracker.fixture")
    ArchRules.domainMustNotDependOn("org.apache.pekko..").check(fixture)
  }
