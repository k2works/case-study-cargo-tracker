package cargotracker.sharedkernel

/** 業務エラー。例外ではなく値で返す（ドメイン → ユースケース → GraphQL のエラーコードへ写す。BE 9.3）。 */
trait DomainError:
  def message: String

/** 値オブジェクトの検証に失敗した。`field` は利用者向けのエラー表示で項目を指すための名前。 */
final case class InvalidValue(field: String, reason: String) extends DomainError:
  def message: String = s"$field: $reason"
