import type { Shipper, ShipperType } from '../types'

/**
 * 荷主の入力値（登録・編集で共通）。
 *
 * コンポーネントと同じファイルに置くと Fast Refresh が効かなくなるため分けている。
 * 数値も文字列で持つ。入力途中の「0.」や空文字を数値に潰すと、未入力（未設定）と
 * 0% の区別が画面の側で失われる。
 */
export type ShipperFormValue = {
  type: ShipperType
  name: string
  email: string
  address: string
  phone: string
  contractNumber: string
  discountRatePercent: string
}

export const EMPTY_SHIPPER_FORM: ShipperFormValue = {
  type: 'INDIVIDUAL',
  name: '',
  email: '',
  address: '',
  phone: '',
  contractNumber: '',
  discountRatePercent: '',
}

/** 登録済みの荷主を入力値に戻す。編集画面の初期値。 */
export function shipperFormValueOf(shipper: Shipper): ShipperFormValue {
  return {
    type: shipper.type,
    name: shipper.name,
    email: shipper.email,
    address: shipper.address,
    phone: shipper.phone ?? '',
    contractNumber: shipper.contractNumber ?? '',
    discountRatePercent:
      shipper.discountRatePercent === null ? '' : String(shipper.discountRatePercent),
  }
}

/**
 * 送信前に、サーバが返すのと同じ文言で拒む。
 *
 * ブラウザ既定の検証（required / max）は吹き出しで知らせるだけで、画面には何も残らない。
 * 「押しても何も起きない」と受け取られ、営業担当者は原因を探せない。
 */
export function localInvalidMessage(value: ShipperFormValue): string | null {
  if (value.type !== 'CORPORATE') {
    return null
  }
  if (value.contractNumber.trim() === '') {
    return '法人荷主には契約番号が必要です'
  }
  if (value.discountRatePercent.trim() !== '') {
    const percent = Number(value.discountRatePercent)
    if (Number.isNaN(percent) || percent < 0 || percent > 30) {
      return `割引率は 0〜30% の範囲で指定してください: ${value.discountRatePercent}`
    }
  }
  return null
}

/** 入力値を送信用の形にする。契約情報は法人のときだけ送る。 */
export function shipperRequestOf(value: ShipperFormValue, registerAnyway: boolean) {
  const corporate = value.type === 'CORPORATE'
  return {
    type: value.type,
    name: value.name,
    email: value.email,
    address: value.address,
    phone: value.phone.trim() === '' ? null : value.phone,
    contractNumber: corporate && value.contractNumber.trim() !== '' ? value.contractNumber : null,
    discountRatePercent:
      corporate && value.discountRatePercent.trim() !== ''
        ? Number(value.discountRatePercent)
        : null,
    registerAnyway,
  }
}
