package views.helpers

import cargotracker.shared.domain.{Location, Money}

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, ZoneId, ZonedDateTime}

/** 画面表示用フォーマッタ。Twirl テンプレートから呼び出して `Money` / `Instant` / `Location` 等の値オブジェクトを統一フォーマットで描画する。
  *
  * IT3 マルチパースペクティブレビュー高優先度 #1 への対応として導入（IT4 タスク 0.1）。
  */
object DisplayFormatters:

  private val JstZone: ZoneId = ZoneId.of("Asia/Tokyo")

  private val DateTimeJst: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'JST'")

  private val DateOnly: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

  /** Money を「1,000 JPY」形式に整形する。amount は最小通貨単位そのまま桁区切り表示。 */
  def formatMoney(money: Money): String =
    f"${money.amount}%,d ${money.currency}"

  /** Option[Money] を整形する。None は "-"。 */
  def formatMoneyOpt(money: Option[Money]): String =
    money.map(formatMoney).getOrElse("-")

  /** Instant を JST 表示（"2099-07-01 09:00 JST"）に整形する。 */
  def formatInstant(instant: Instant): String =
    ZonedDateTime.ofInstant(instant, JstZone).format(DateTimeJst)

  /** LocalDate を ISO 形式（"2099-07-01"）で表示する。 */
  def formatDate(date: LocalDate): String = date.format(DateOnly)

  /** Location を「JPTYO（東京）」形式に整形する。name 未設定時は UnLocode のみ。 */
  def formatLocation(location: Location): String =
    if location.name.nonEmpty then s"${location.unLocode}（${location.name}）"
    else location.unLocode
