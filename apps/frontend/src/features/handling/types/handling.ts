// 荷役作業の型定義（US15 / US16）

export type HandlingType = 'RECEIVE' | 'LOAD' | 'UNLOAD' | 'CLAIM' | 'CUSTOMS'

export interface ClaimVerificationInput {
  consigneeName: string
  signatureRef?: string
  confirmationCode?: string
}

export interface RegisterHandlingActivityRequest {
  trackingNumber: string
  handlingType: HandlingType
  unlocode: string
  occurredAt: string
  voyageNumber?: string
  operatorId: string
  claimVerification?: ClaimVerificationInput
}

export interface HandlingActivityResponse {
  activityId: string
  trackingNumber: string
  handlingType: string
  unlocode: string
  unexpected: boolean
}

export interface HandlingActivityRecord {
  activityId: string
  bookingId: string
  trackingNumber: string
  originUnlocode: string
  destinationUnlocode: string
  cargoType: string
  handlingType: string
  occurredAt: string
  recordedAt: string
  unlocode: string
  voyageNumber: string | null
  handlerId: string
  unexpected: boolean
}

export const HANDLING_TYPE_LABELS: Record<HandlingType, string> = {
  RECEIVE: '受領',
  LOAD: '積込',
  UNLOAD: '荷降し',
  CLAIM: '引取',
  CUSTOMS: '税関通過',
}
