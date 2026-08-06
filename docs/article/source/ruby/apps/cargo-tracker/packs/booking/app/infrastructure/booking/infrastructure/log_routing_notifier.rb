# frozen_string_literal: true

module Booking
  module Infrastructure
    # 経路設計者通知のログ実装（暫定）。ADR-0002 のイベント駆動・永続化は後続 IT で置換する。
    class LogRoutingNotifier < Domain::RoutingNotifier
      def initialize(logger: Rails.logger)
        @logger = logger
      end

      def notify_booking_registered(booking_id)
        @logger.info("[通知] 経路設計者へ: 予約登録 #{booking_id}")
      end

      def notify_routing_requested(booking_id)
        @logger.info("[通知] 経路設計者へ: 経路設計依頼 #{booking_id}")
      end
    end
  end
end
