# frozen_string_literal: true

require "rails_helper"

# Booking → Shipper の ACL アダプタ（腐敗防止層）。Shipper 公開 API への委譲を固定する。
RSpec.describe Booking::Infrastructure::ShipperDirectoryExistenceChecker do
  subject(:checker) { described_class.new }

  let(:shipper_id) do
    Shipper::Public::ShipperRegistration.new.call(
      shipper_type: "INDIVIDUAL", name: "荷主太郎", email: "n@example.com", address: "東京"
    ).shipper_id
  end

  it "実在する荷主 ID なら true" do
    expect(checker.exists?(shipper_id)).to be true
  end

  it "存在しない荷主 ID なら false" do
    expect(checker.exists?(-1)).to be false
  end
end
