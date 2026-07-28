module ApplicationHelper
  # 輸送状態（TrackingStatus）の日本語表示ラベル（US15/US17・表示側の可読性）。
  TRACKING_STATUS_LABELS = {
    "NOT_RECEIVED" => "受領待ち", "RECEIVED" => "受領済", "LOADED" => "積込済",
    "ONBOARD_CARRIER" => "輸送中", "UNLOADED" => "荷降し済",
    "AWAITING_CLAIM" => "引取待ち", "CLAIMED" => "引取済"
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
end
