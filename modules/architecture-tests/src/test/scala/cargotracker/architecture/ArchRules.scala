package cargotracker.architecture

import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/** 適合性の検査で共有するルール。 */
object ArchRules:

  /** どの Unit の domain（`cargotracker.<unit>.domain..`）も、指定したパッケージに依存しない。 */
  def domainMustNotDependOn(packages: String*): ArchRule =
    noClasses()
      .that()
      .resideInAPackage("cargotracker.*.domain..")
      .should()
      .dependOnClassesThat()
      .resideInAnyPackage(packages*)
      .allowEmptyShould(true)
