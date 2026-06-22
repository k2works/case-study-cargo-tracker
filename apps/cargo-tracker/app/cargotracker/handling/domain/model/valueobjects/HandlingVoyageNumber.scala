package cargotracker.handling.domain.model.valueobjects

/** Handling Context 固有の航海番号型（コンテキスト分離 / domain-model.md L874）。 */
opaque type HandlingVoyageNumber = String

object HandlingVoyageNumber:
  sealed trait Error
  case object Empty extends Error

  def apply(raw: String): Either[Error, HandlingVoyageNumber] =
    if raw.nonEmpty then Right(raw) else Left(Empty)

  def unsafeFrom(raw: String): HandlingVoyageNumber =
    apply(raw).fold(_ => throw IllegalArgumentException(s"Invalid HandlingVoyageNumber: $raw"), identity)

  extension (vn: HandlingVoyageNumber) def value: String = vn
