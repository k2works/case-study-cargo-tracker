# frozen_string_literal: true

# テストで使う主要港を Location マスタに登録するヘルパ（T17 で BookCargo が地点実在を検証するため）。
module LocationSeeder
  PORTS = {
    "JPTYO" => "Tokyo", "JPOSA" => "Osaka", "USLAX" => "Los Angeles",
    "USNYC" => "New York", "NLRTM" => "Rotterdam", "SGSIN" => "Singapore", "CNSHA" => "Shanghai"
  }.freeze

  def register_test_locations
    directory = Shared::Public::LocationDirectory.new
    PORTS.each { |unlocode, name| directory.register(unlocode: unlocode, name: name) }
  end
end

RSpec.configure do |config|
  config.include LocationSeeder
  # 予約・経路系の spec は港マスタを前提とするため、既定で登録しておく。
  config.before(:each, type: :system) { register_test_locations }
  config.before(:each, type: :request) { register_test_locations }
end
