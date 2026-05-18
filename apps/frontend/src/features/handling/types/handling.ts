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

// US17: 貨物状態手動更新
export type CargoStatus = 'IN_TRANSIT' | 'DELIVERED' | 'EXCEPTION'

export const CARGO_STATUS_LABELS: Record<CargoStatus, string> = {
  IN_TRANSIT: '輸送中',
  DELIVERED: '引取済',
  EXCEPTION: '例外',
}

export interface UpdateCargoStatusRequest {
  newStatus: CargoStatus
  unlocode: string
  updatedAt: string
  operatorId: string
}

export interface CargoStatusUpdateResponse {
  historyId: string
  trackingNumber: string
  newStatus: string
  unlocode: string
}

export interface CargoSnapshotResponse {
  bookingId: string
  trackingNumber: string
  originUnlocode: string
  destinationUnlocode: string
  cargoType: string
}

export interface CargoStatusHistoryRecord {
  historyId: string
  trackingNumber: string
  newStatus: string
  unlocode: string
  updatedAt: string
  operatorId: string
  recordedAt: string
}
