/** 荷主宛のお知らせ 1 件（US39）。 */
export type ShipperNotification = {
  id: number
  trackingNumber: string
  noticedAt: string
  message: string
}

export type ShipperNotifications = {
  notifications: ShipperNotification[]
}
