# frozen_string_literal: true

module Tracking
  module Domain
    # 追跡活動リポジトリの抽象ポート（出力ポート・ADR-0001）。
    class TrackingRepository
      def save(_activity) = raise NotImplementedError
      def find_by_booking_id(_booking_id) = raise NotImplementedError
      def find_by_tracking_number(_tracking_number) = raise NotImplementedError
      def exists_for_booking?(_booking_id) = raise NotImplementedError
    end
  end
end
