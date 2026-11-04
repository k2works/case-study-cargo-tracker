package cargotracker.sharedkernel

import java.time.Instant
import org.scalatest.funsuite.AnyFunSuite

class ClockSpec extends AnyFunSuite:

  test("固定した Clock は、いつ呼んでも同じ時刻を返す") {
    val at = Instant.parse("2026-11-04T01:02:03Z")
    val clock = Clock.fixed(at)
    assert(clock.now() == at)
    assert(clock.now() == at)
  }

  test("システムの Clock は、呼び出しの前後の時刻に収まる") {
    val before = Instant.now()
    val now = Clock.system.now()
    val after = Instant.now()
    assert(!now.isBefore(before) && !now.isAfter(after))
  }
