package cargotracker.sharedkernel

import java.util.random.RandomGenerator

/** 集約 ID（ULID。ADR-003）。26 文字の Crockford Base32。先頭 10 文字は時刻（ミリ秒、48 ビット）、 続く 16 文字は乱数（80
  * ビット）で、文字列として時系列に並ぶ。 予約番号などの「人が読む番号」とは別物。
  */
final case class Ulid private (value: String):
  override def toString: String = value

object Ulid:
  private val Alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
  private val TimeChars = 10
  private val RandChars = 16
  private val Length = TimeChars + RandChars
  private val MaxTimeMs = (1L << 48) - 1

  /** 時刻は Clock から、乱数は引数から取る（テストで再現できるように）。 */
  def generate(clock: Clock, random: RandomGenerator): Ulid =
    val ms = clock.now().toEpochMilli
    require(ms >= 0 && ms <= MaxTimeMs, s"時刻が ULID の範囲外: $ms")
    val time = (TimeChars - 1 to 0 by -1).map(i => Alphabet(((ms >> (5 * i)) & 31).toInt))
    val rand = Seq.fill(RandChars)(Alphabet(random.nextInt(32)))
    Ulid((time ++ rand).mkString)

  def generate(clock: Clock): Ulid = generate(clock, new java.security.SecureRandom())

  /** `field` は、エラーに載せる項目名（呼び出し側が決める）。 */
  def parse(raw: String, field: String = "ulid"): Either[InvalidValue, Ulid] =
    val v = raw.trim.toUpperCase
    val ok = v.length == Length && v.forall(Alphabet.contains) && v.head <= '7'
    Either.cond(ok, Ulid(v), InvalidValue(field, "26 文字の Crockford Base32 ではない"))
