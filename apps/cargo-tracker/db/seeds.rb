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
# 業務を実行できるサンプルデータ（荷主・貨物予約）。
# 公開 API 経由でドメインルールを通して投入する（privacy を尊重）。
# 冪等: 荷主はメール重複で再登録されない。貨物予約は未投入時のみ作成する。
# ---------------------------------------------------------------------------

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
