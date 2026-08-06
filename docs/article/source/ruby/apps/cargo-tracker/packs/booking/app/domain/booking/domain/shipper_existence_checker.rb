# frozen_string_literal: true

module Booking
  module Domain
    # 荷主存在確認の ACL 出力ポート（Booking → Shipper の腐敗防止層）。
    # 具象アダプタは Infrastructure 層で Shipper 公開 API を呼び出す（ADR-0001/0003）。
    class ShipperExistenceChecker
      def exists?(_shipper_id)
        raise NotImplementedError, "#{self.class}#exists? is not implemented"
      end
    end
  end
end
