module ApplicationHelper
  # 輸送状態（TrackingStatus）の日本語表示ラベル（US15/US17・表示側の可読性）。
  TRACKING_STATUS_LABELS = {
    "NOT_RECEIVED" => "受領待ち", "RECEIVED" => "受領済", "LOADED" => "積込済",
    "ONBOARD_CARRIER" => "輸送中", "UNLOADED" => "荷降し済",
    "AWAITING_CLAIM" => "引取待ち", "CLAIMED" => "引取済", "EXCEPTION" => "例外発生"
  }.freeze

  # 例外種別（ExceptionType）の日本語表示ラベル（US19/US20）。
  EXCEPTION_TYPE_LABELS = {
    "DELAY" => "遅延", "DAMAGE" => "破損", "LOST" => "紛失", "CUSTOMS_HOLD" => "税関保留"
  }.freeze

  # 荷役イベント種別の日本語表示ラベル（US15/US16）。
  HANDLING_EVENT_LABELS = {
    "RECEIVE" => "受領", "LOAD" => "積込", "UNLOAD" => "荷降し", "CLAIM" => "引取",
    "MANUAL_UPDATE" => "手動更新"
  }.freeze

  def tracking_status_label(value)
    TRACKING_STATUS_LABELS.fetch(value.to_s, value)
  end

  def handling_event_label(value)
    HANDLING_EVENT_LABELS.fetch(value.to_s, value)
  end

  def exception_type_label(value)
    EXCEPTION_TYPE_LABELS.fetch(value.to_s, value)
  end

  # 貨物種別（CargoType）の日本語表示ラベル（US01/US04）。
  CARGO_TYPE_LABELS = { "GENERAL" => "一般", "HAZARDOUS" => "危険物", "REFRIGERATED" => "冷凍・冷蔵" }.freeze

  def cargo_type_label(value)
    CARGO_TYPE_LABELS.fetch(value.to_s, value)
  end

  # 支払状態（PaymentStatus）の日本語表示ラベル（US21/US23）。
  PAYMENT_STATUS_LABELS = {
    "PENDING" => "支払待ち", "CONFIRMED" => "精算済", "OVERDUE" => "支払期限超過", "REFUNDED" => "返金済"
  }.freeze

  def payment_status_label(value)
    PAYMENT_STATUS_LABELS.fetch(value.to_s, value)
  end
end
