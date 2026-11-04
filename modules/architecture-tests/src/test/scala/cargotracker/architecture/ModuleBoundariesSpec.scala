package cargotracker.architecture

import com.tngtech.archunit.core.importer.{ClassFileImporter, ImportOption}
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.scalatest.funsuite.AnyFunSuite

/** 検査 A1：別の Unit の内側（domain・usecase・adapter）を参照しない。 参照してよいのは相手の contract だけ（ADR-001）。 あわせて、Unit
  * の内側の層の向き（domain ← usecase ← adapter、contract は葉）を検査する。
  *
  * 一覧に載っていないパッケージを「通す」検査にしない：Unit と層の一覧は下の 2 つだけで、 新しい Unit・層を足したらここに足す（足さなければ、そのパッケージは A1
  * の対象外になる点に注意）。
  */
class ModuleBoundariesSpec extends AnyFunSuite:

  private val units = List("booking", "routing", "tracking", "billing", "identity")
  private val internals = List("domain", "usecase", "commandadapter", "queryadapter")

  private val classes = new ClassFileImporter()
    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
    .importPackages("cargotracker")

  // 対象のクラスがまだ 1 つも無い Unit でも検査が通るように allowEmptyShould を使う。
  // クラスが現れた時点で、違反があれば赤になる。

  for
    owner <- units
    other <- units if owner != other
    layer <- internals
  do
    test(s"A1: $other は $owner の $layer を参照しない") {
      noClasses()
        .that()
        .resideInAPackage(s"cargotracker.$other..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(s"cargotracker.$owner.$layer..")
        .allowEmptyShould(true)
        .check(classes)
    }

  for unit <- units do
    test(s"層: $unit の domain は usecase・adapter・contract を参照しない") {
      noClasses()
        .that()
        .resideInAPackage(s"cargotracker.$unit.domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
          s"cargotracker.$unit.usecase..",
          s"cargotracker.$unit.commandadapter..",
          s"cargotracker.$unit.queryadapter..",
          s"cargotracker.$unit.contract.."
        )
        .allowEmptyShould(true)
        .check(classes)
    }

    test(s"層: $unit の usecase は adapter を参照しない") {
      noClasses()
        .that()
        .resideInAPackage(s"cargotracker.$unit.usecase..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
          s"cargotracker.$unit.commandadapter..",
          s"cargotracker.$unit.queryadapter.."
        )
        .allowEmptyShould(true)
        .check(classes)
    }

    test(s"層: $unit の contract は自 Unit の内側を参照しない（shared-kernel だけに依存する葉）") {
      noClasses()
        .that()
        .resideInAPackage(s"cargotracker.$unit.contract..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(internals.map(l => s"cargotracker.$unit.$l..")*)
        .allowEmptyShould(true)
        .check(classes)
    }

    test(s"層: $unit の commandadapter と queryadapter は互いを参照しない") {
      noClasses()
        .that()
        .resideInAPackage(s"cargotracker.$unit.commandadapter..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(s"cargotracker.$unit.queryadapter..")
        .allowEmptyShould(true)
        .check(classes)
      noClasses()
        .that()
        .resideInAPackage(s"cargotracker.$unit.queryadapter..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(s"cargotracker.$unit.commandadapter..")
        .allowEmptyShould(true)
        .check(classes)
    }
