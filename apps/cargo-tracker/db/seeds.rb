# frozen_string_literal: true

# 開発・動作確認用の初期利用者。冪等（find_or_create_by）に投入する。
# ログイン画面のデフォルト入力（development）と一致させる。
DEFAULT_PASSWORD = "password123"

seed_users = [
  { username: "sales",   email: "sales@example.com",   role: "sales" },
  { username: "handler", email: "handler@example.com", role: "handler" },
  { username: "tracker", email: "tracker@example.com", role: "tracker" },
  { username: "billing", email: "billing@example.com", role: "billing" },
  { username: "admin",   email: "admin@example.com",   role: "admin" }
]

seed_users.each do |attrs|
  user = User.find_or_create_by!(username: attrs[:username]) do |u|
    u.email = attrs[:email]
    u.password = DEFAULT_PASSWORD
    u.enabled = true
  end
  user.user_roles.find_or_create_by!(role: attrs[:role])
  puts "seed user: #{user.username} / #{DEFAULT_PASSWORD}（#{attrs[:role]}）"
end

# ---------------------------------------------------------------------------
# 業務を実行できるサンプルデータ（場所・荷主・貨物予約）。
# 公開 API 経由でドメインルールを通して投入する（privacy を尊重）。
# 冪等: 荷主はメール重複で再登録されない。貨物予約は未投入時のみ作成する。
# 注: サンプルデータは development 限定。test では各 spec が factory/公開 API で
# 独自にデータを用意するため投入しない（CI の db:prepare 由来の重複を防ぐ）。
# ---------------------------------------------------------------------------
if Rails.env.development?

# --- 場所マスタ（Location 共有カーネル・US07/US08/US24 の港湾） ---
location_directory = Shared::Public::LocationDirectory.new
seed_locations = [
  { unlocode: "JPTYO", name: "Tokyo", country_code: "JP", time_zone: "Asia/Tokyo" },
  { unlocode: "JPOSA", name: "Osaka", country_code: "JP", time_zone: "Asia/Tokyo" },
  { unlocode: "USLAX", name: "Los Angeles", country_code: "US", time_zone: "America/Los_Angeles" },
  { unlocode: "USNYC", name: "New York", country_code: "US", time_zone: "America/New_York" },
  { unlocode: "NLRTM", name: "Rotterdam", country_code: "NL", time_zone: "Europe/Amsterdam" },
  { unlocode: "SGSIN", name: "Singapore", country_code: "SG", time_zone: "Asia/Singapore" },
  { unlocode: "CNSHA", name: "Shanghai", country_code: "CN", time_zone: "Asia/Shanghai" }
]
seed_locations.each do |attrs|
  location_directory.register(**attrs)
end
puts "seed locations: #{location_directory.all.size} 港"

# --- 荷主（US02/US03） ---
seed_shippers = [
  { shipper_type: "INDIVIDUAL", name: "山田太郎", email: "yamada@example.com",
    address: "東京都千代田区丸の内 1-1-1", phone: "03-1234-5678" },
  { shipper_type: "INDIVIDUAL", name: "佐藤花子", email: "sato@example.com",
    address: "大阪府大阪市北区梅田 2-2-2", phone: "06-2345-6789" },
  { shipper_type: "CORPORATE", name: "株式会社グローバル物流", email: "global@example.com",
    address: "神奈川県横浜市西区みなとみらい 3-3-3", phone: "045-3456-7890",
    contract_number: "C-1001", discount_rate: "0.15" },
  { shipper_type: "CORPORATE", name: "日本海運株式会社", email: "nihonkaiun@example.com",
    address: "兵庫県神戸市中央区港島 4-4-4", phone: "078-4567-8901",
    contract_number: "C-1002", discount_rate: "0.30" }
]

shipper_ids = {}
seed_shippers.each do |attrs|
  result = Shipper::Public::ShipperRegistration.new.call(**attrs)
  id = result.shipper_id || result.existing&.id
  shipper_ids[attrs[:email]] = id
  status = result.success? ? "作成" : "既存"
  puts "seed shipper: #{attrs[:name]}（id=#{id} / #{status}）"
end

# --- 貨物予約（US04/US05/US06） ---
booking_service = Booking::Public::CargoBookingService.new

if booking_service.all.empty?
  yamada = shipper_ids["yamada@example.com"]
  global = shipper_ids["global@example.com"]

  seed_bookings = [
    # 一般貨物（PRELIMINARY のまま）
    { shipper_id: yamada, cargo_type: "GENERAL", weight_kg: "1200.5",
      origin: "JPTYO", destination: "USLAX", arrival_deadline: "2026-12-01",
      description: "自動車部品", assign: false },
    # 危険物貨物（登録後に経路設計者へ引き渡し済み）
    { shipper_id: global, cargo_type: "HAZARDOUS", weight_kg: "800",
      origin: "JPOSA", destination: "NLRTM", arrival_deadline: "2026-12-15",
      description: "リチウム電池",
      hazardous: { hazardous_class: "9", un_number: "UN3480", proper_shipping_name: "LITHIUM ION BATTERIES" },
      assign: true },
    # 冷凍貨物（PRELIMINARY のまま）
    { shipper_id: global, cargo_type: "REFRIGERATED", weight_kg: "2500",
      origin: "JPTYO", destination: "SGSIN", arrival_deadline: "2026-11-20",
      description: "冷凍水産物",
      temperature: { min_temperature: -25, max_temperature: -18, unit: "CELSIUS" },
      assign: false }
  ]

  seed_bookings.each do |b|
    args = {
      shipper_id: b[:shipper_id], cargo_type: b[:cargo_type], weight_kg: b[:weight_kg],
      origin: b[:origin], destination: b[:destination], arrival_deadline: b[:arrival_deadline],
      description: b[:description]
    }
    if b[:hazardous]
      args[:hazardous_declaration] = Booking::Public::CargoBookingService.hazardous_declaration(**b[:hazardous])
    end
    if b[:temperature]
      args[:temperature_requirement] = Booking::Public::CargoBookingService.temperature_requirement(**b[:temperature])
    end

    result = booking_service.book(**args)
    if result.success?
      booking_service.assign_to_routing(result.booking_id) if b[:assign]
      state = b[:assign] ? "経路設計中" : "仮受付"
      puts "seed booking: #{result.booking_id}（#{b[:cargo_type]} / #{state}）"
    else
      puts "seed booking 失敗: #{result.error_message}"
    end
  end
else
    puts "seed booking: 既存の貨物予約があるためスキップ"
end

# --- 航海スケジュール（US24/US07・経路候補算出/見積の基盤） ---
voyage_directory = Routing::Public::VoyageDirectory.new
if voyage_directory.all.empty?
  seed_voyages = [
    { voyage_number: "V001", carrier_name: "Pacific Ocean Line",
      movements: [ { departure_unlocode: "JPTYO", arrival_unlocode: "USLAX",
                     departure_date: "2026-09-01T09:00", arrival_date: "2026-09-15T18:00", seq_number: 1 } ] },
    { voyage_number: "V002", carrier_name: "Euro Asia Express",
      movements: [ { departure_unlocode: "JPOSA", arrival_unlocode: "SGSIN",
                     departure_date: "2026-09-03T08:00", arrival_date: "2026-09-10T20:00", seq_number: 1 },
                   { departure_unlocode: "SGSIN", arrival_unlocode: "NLRTM",
                     departure_date: "2026-09-12T08:00", arrival_date: "2026-09-28T20:00", seq_number: 2 } ] },
    { voyage_number: "V003", carrier_name: "Trans Pacific Reefer",
      movements: [ { departure_unlocode: "JPTYO", arrival_unlocode: "SGSIN",
                     departure_date: "2026-09-05T07:00", arrival_date: "2026-09-14T19:00", seq_number: 1 } ] }
  ]
  seed_voyages.each do |v|
    voyage_directory.register(
      voyage_number: v[:voyage_number], carrier_name: v[:carrier_name],
      supported_cargo_types: %w[GENERAL HAZARDOUS REFRIGERATED], movements: v[:movements]
    )
    puts "seed voyage: #{v[:voyage_number]}（#{v[:carrier_name]}）"
  end
else
  puts "seed voyage: 既存の航海があるためスキップ"
end

# --- 輸送見積（US01・営業担当者の見積作成） ---
estimation_service = Estimation::Public::EstimationService.new
if estimation_service.all.empty?
  [
    { origin: "JPTYO", destination: "USLAX", arrival_deadline: "2026-10-15", cargo_type: "GENERAL", weight_kg: 1500 },
    { origin: "JPTYO", destination: "SGSIN", arrival_deadline: "2026-10-10", cargo_type: "REFRIGERATED", weight_kg: 2000 }
  ].each do |e|
    result = estimation_service.create_estimate(**e)
    puts "seed estimate: #{result.status}（#{e[:origin]}→#{e[:destination]}・#{result.estimate_id}）"
  end
else
  puts "seed estimate: 既存の見積があるためスキップ"
end

# --- フルライフサイクル（予約→経路→確定→追跡→荷役→引取→請求→精算・US04-US23 の一気通貫） ---
billing_service = Billing::Public::BillingService.new
tracking_service = Tracking::Public::TrackingService.new
handling_service = Handling::Public::HandlingService.new
if billing_service.invoices.empty?
  demo = booking_service.book(
    shipper_id: shipper_ids["global@example.com"], cargo_type: "GENERAL", weight_kg: "1000",
    origin: "JPTYO", destination: "USLAX", arrival_deadline: "2026-11-30", description: "デモ貨物（一気通貫）"
  )
  if demo.success?
    bid = demo.booking_id
    booking_service.assign_to_routing(bid)
    booking_service.assign_itinerary(bid, [
      { load_location: "JPTYO", unload_location: "USLAX", voyage_number: "V001",
        load_time: Time.utc(2026, 9, 1, 9), unload_time: Time.utc(2026, 9, 15, 18) }
    ])
    booking_service.confirm(bid)
    tn = tracking_service.issue_tracking_number(bid).tracking_number
    handling_service.register(tracking_number: tn, event_type: "RECEIVE", location: "JPTYO",
                              completion_time: Time.utc(2026, 9, 1, 8), operator_name: "荷役担当A")
    handling_service.register(tracking_number: tn, event_type: "LOAD", location: "JPTYO",
                              completion_time: Time.utc(2026, 9, 1, 10), voyage_number: "V001", operator_name: "荷役担当A")
    handling_service.register(tracking_number: tn, event_type: "CLAIM", location: "USLAX",
                              completion_time: Time.utc(2026, 9, 16, 10), operator_name: "荷役担当B",
                              recipient: { name: "受取太郎", confirmation_code: "OK-2026" })
    freight = billing_service.calculate_freight(bid)
    puts "seed lifecycle: #{bid} → 追跡 #{tn} → 荷役(受領/積込/引取) → 請求 #{freight.invoice_number}（法人割引適用）"

    # 遅延例外 + 対応報告（新到着予定日）の確認用（US19/T37）
    demo2 = booking_service.book(
      shipper_id: shipper_ids["global@example.com"], cargo_type: "GENERAL", weight_kg: "500",
      origin: "JPTYO", destination: "SGSIN", arrival_deadline: "2026-12-10", description: "デモ貨物（遅延例外）"
    )
    if demo2.success?
      booking_service.assign_to_routing(demo2.booking_id)
      booking_service.assign_itinerary(demo2.booking_id, [
        { load_location: "JPTYO", unload_location: "SGSIN", voyage_number: "V003",
          load_time: Time.utc(2026, 9, 5, 7), unload_time: Time.utc(2026, 9, 14, 19) }
      ])
      booking_service.confirm(demo2.booking_id)
      tn2 = tracking_service.issue_tracking_number(demo2.booking_id).tracking_number
      tracking_service.register_exception(tn2, exception_type: "DELAY", occurred_at: Time.utc(2026, 9, 10),
                                          description: "台風による遅延", location: "JPTYO")
      puts "seed lifecycle: #{demo2.booking_id} → 追跡 #{tn2} → 遅延例外登録（/public/tracking/#{tn2} で確認可）"
    end

    # 未払い請求（支払期限超過・US23-5 の billing:mark_overdue 実行で OVERDUE 化）
    demo3 = booking_service.book(
      shipper_id: shipper_ids["yamada@example.com"], cargo_type: "GENERAL", weight_kg: "300",
      origin: "JPTYO", destination: "USLAX", arrival_deadline: "2026-08-30", description: "デモ貨物（未払い）"
    )
    if demo3.success?
      booking_service.assign_to_routing(demo3.booking_id)
      booking_service.assign_itinerary(demo3.booking_id, [
        { load_location: "JPTYO", unload_location: "USLAX", voyage_number: "V001",
          load_time: Time.utc(2026, 7, 1, 9), unload_time: Time.utc(2026, 7, 15, 18) }
      ])
      booking_service.confirm(demo3.booking_id)
      tn3 = tracking_service.issue_tracking_number(demo3.booking_id).tracking_number
      handling_service.register(tracking_number: tn3, event_type: "LOAD", location: "JPTYO",
                                completion_time: Time.utc(2026, 7, 1, 10), voyage_number: "V001", operator_name: "荷役担当A")
      handling_service.register(tracking_number: tn3, event_type: "CLAIM", location: "USLAX",
                                completion_time: Time.utc(2026, 7, 16, 10), operator_name: "荷役担当B",
                                recipient: { name: "受取次郎", confirmation_code: "OK-OLD" })
      old = billing_service.calculate_freight(demo3.booking_id)
      # デモ用に支払期限を過去日へ補正（calculate_freight は現在時刻で発行するため）。
      # 開発シード限定。BC の privacy を尊重し、公開テーブルへ生 SQL で更新する。
      ActiveRecord::Base.connection.execute(
        "UPDATE invoices SET issued_at = '2026-06-01', due_date = '2026-07-01' " \
        "WHERE booking_id = #{ActiveRecord::Base.connection.quote(demo3.booking_id)}"
      )
      puts "seed lifecycle: #{demo3.booking_id} → 請求 #{old.invoice_number}（期限超過・`bin/rails billing:mark_overdue` で未払い通知）"
    end
  else
    puts "seed lifecycle 失敗: #{demo.error_message}"
  end
else
  puts "seed lifecycle: 既存の請求があるためスキップ"
end
end
