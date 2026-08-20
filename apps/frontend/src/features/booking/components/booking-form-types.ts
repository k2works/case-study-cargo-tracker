import type { CargoType } from '../types'

/**
 * 貨物種別で出入りする追加項目の入力値（US05）。
 *
 * コンポーネントと同じファイルに置くと Fast Refresh が効かなくなるため分けている。
 */

export type HazardousInput = {
  hazardousClass: string
  unNumber: string
  properShippingName: string
}

export type TemperatureInput = {
  minCelsius: string
  maxCelsius: string
}

export const EMPTY_HAZARDOUS: HazardousInput = {
  hazardousClass: '',
  unNumber: '',
  properShippingName: '',
}

export const EMPTY_TEMPERATURE: TemperatureInput = { minCelsius: '', maxCelsius: '' }

/** 種別ごとに、どの追加項目を出すか。判断を 1 箇所にまとめる。 */
export function additionalFieldsOf(type: CargoType) {
  return {
    hazardous: type === 'HAZARDOUS',
    temperature: type === 'REFRIGERATED',
  }
}
