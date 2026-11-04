package cargotracker.fixture.domain

/** A3 のメタ検査用のフィクスチャ。テストのクラスパスにだけ存在する（本番の検査は DO_NOT_INCLUDE_TESTS で除外）。 */
object ImpureDomain:
  def suite: org.scalatest.funsuite.AnyFunSuite = null
