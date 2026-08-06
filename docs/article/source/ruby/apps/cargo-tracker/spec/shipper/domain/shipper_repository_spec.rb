# frozen_string_literal: true

require "rails_helper"

# 出力ポート（リポジトリ契約）。ヘキサゴナルの依存方向を担保する抽象。
RSpec.describe Shipper::Domain::ShipperRepository do
  describe "契約（抽象メソッド）" do
    subject(:port) { described_class.new }

    %i[save find_by_code find_by_email exists_by_email? all].each do |method|
      it "##{method} は未実装なら NotImplementedError を送出する" do
        expect { port.public_send(method, nil) }.to raise_error(NotImplementedError)
      end
    end
  end

  describe "Active Record 実装の準拠" do
    it "ActiveRecordShipperRepository はポートを実装している" do
      expect(Shipper::Infrastructure::ActiveRecordShipperRepository.ancestors)
        .to include(described_class)
    end
  end
end
