# frozen_string_literal: true

module Tracking
  module Domain
    # 貨物追跡の集約ルート（US14）。追跡番号・対象予約・現在の輸送状態を管理する。
    # booking_id は Booking Context の自然キー（越境識別子・ADR-0003）を論理参照する。
    class TrackingActivity
      attr_reader :tracking_number, :booking_id, :transport_status

      # 追跡番号を発行して追跡活動を開始する（US14）。NOT_RECEIVED（受領待ち）で生成。
      def self.issue(booking_id:, tracking_number: nil)
        raise ArgumentError, "予約番号は必須です" if booking_id.nil? || booking_id.to_s.strip.empty?

        new(
          tracking_number: tracking_number || TrackingNumber.generate,
          booking_id: booking_id, transport_status: TrackingStatus.initial
        )
      end

      # 永続化からの復元専用（生成時バリデーションを再評価しない）。
      def self.reconstitute(tracking_number:, booking_id:, transport_status:)
        new(tracking_number: tracking_number, booking_id: booking_id, transport_status: transport_status)
      end

      def initialize(tracking_number:, booking_id:, transport_status:)
        @tracking_number = tracking_number
        @booking_id = booking_id
        @transport_status = transport_status
      end

      # 荷役作業種別に応じて輸送状態を進める（US15）。対応状態がなければ変更しない。
      def apply_handling(handling_type)
        next_status = TrackingStatus.for_handling(handling_type)
        @transport_status = next_status if next_status
      end

      # 状態・位置を手動で更新する（US17）。指定の輸送状態へ差し替える。
      def update_status(transport_status)
        @transport_status = transport_status
      end
    end
  end
end
