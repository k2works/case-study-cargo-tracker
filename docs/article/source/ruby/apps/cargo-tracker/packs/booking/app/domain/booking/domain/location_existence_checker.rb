# frozen_string_literal: true

module Booking
  module Domain
    # 地点存在確認の ACL 出力ポート（Booking → Shared/Location の腐敗防止層）。
    # 具象アダプタは Infrastructure 層で Shared の公開 API を呼び出す（ADR-0001/0003）。
    class LocationExistenceChecker
      def exists?(_unlocode)
        raise NotImplementedError, "#{self.class}#exists? is not implemented"
      end
    end
  end
end
