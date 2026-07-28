# frozen_string_literal: true

module Booking
  module Infrastructure
    # 荷主存在確認 ACL のインプロセス・アダプタ（ADR-0003）。
    # Shipper Context の公開 API（Shipper::Public::ShipperDirectory）を呼び出す。
    # 将来コンテキストを分離する場合は Faraday/HTTP 実装へ差し替える（ポートは不変）。
    class ShipperDirectoryExistenceChecker < Domain::ShipperExistenceChecker
      def initialize(directory: Shipper::Public::ShipperDirectory.new)
        @directory = directory
      end

      def exists?(shipper_id)
        @directory.exists?(shipper_id)
      end
    end
  end
end
