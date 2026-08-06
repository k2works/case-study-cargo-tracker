# frozen_string_literal: true

module Booking
  module Domain
    # 経路設計者への通知の出力ポート（US04 予約登録通知・US06 経路設計依頼通知）。
    # 本格的なドメインイベント駆動・通知記録の永続化は ADR-0002 に基づき後続 IT で実装する。
    class RoutingNotifier
      def notify_booking_registered(_booking_id)
        raise NotImplementedError
      end

      def notify_routing_requested(_booking_id)
        raise NotImplementedError
      end
    end
  end
end
