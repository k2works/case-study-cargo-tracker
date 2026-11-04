package cargotracker.sharedkernel

import java.time.Instant

/** 現在時刻の取得元（ADR-005）。ドメインとユースケースは `now()` を直接呼ばず、これを通す。 */
trait Clock:
  def now(): Instant

object Clock:
  val system: Clock = () => Instant.now()

  /** テスト用。いつ呼んでも同じ時刻を返す。「今日」を決めるのもこの Clock。 */
  def fixed(at: Instant): Clock = () => at
