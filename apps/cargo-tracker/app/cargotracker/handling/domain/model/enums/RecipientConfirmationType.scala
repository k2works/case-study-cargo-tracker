package cargotracker.handling.domain.model.enums

/** 荷受人確認の種別（IT7 0.12 / M6）。Claim 種別の荷役登録時に必須。
  *
  *   - `Signature`: 紙伝票への署名
  *   - `Stamp`: 受領印
  *   - `IdCard`: 身分証提示
  *   - `Code`: 引取コード入力
  */
enum RecipientConfirmationType:
  case Signature
  case Stamp
  case IdCard
  case Code

object RecipientConfirmationType:
  def fromName(name: String): Option[RecipientConfirmationType] =
    values.find(_.toString == name)
