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
      # exceptions・status_before_exception は例外処理（US19/US20）の復元時に渡す。
      def self.reconstitute(tracking_number:, booking_id:, transport_status:,
                            exceptions: [], status_before_exception: nil)
        new(tracking_number: tracking_number, booking_id: booking_id, transport_status: transport_status,
            exceptions: exceptions, status_before_exception: status_before_exception)
      end

      def initialize(tracking_number:, booking_id:, transport_status:,
                     exceptions: [], status_before_exception: nil)
        @tracking_number = tracking_number
        @booking_id = booking_id
        @transport_status = transport_status
        @exceptions = exceptions
        @status_before_exception = status_before_exception
      end

      attr_reader :exceptions, :status_before_exception

      # 荷役作業種別に応じて輸送状態を進める（US15）。対応状態がなければ変更しない。
      def apply_handling(handling_type)
        next_status = TrackingStatus.for_handling(handling_type)
        @transport_status = next_status if next_status
      end

      # 状態・位置を手動で更新する（US17）。指定の輸送状態へ差し替える。
      def update_status(transport_status)
        @transport_status = transport_status
      end

      # 例外を登録し、輸送状態を EXCEPTION に遷移させる（US19/US20）。
      # 発生前の状態を保持し、解決時に復帰できるようにする（precondition・T30）。
      def register_exception(exception_type:, occurred_at:, description: nil, location_unlocode: nil)
        event = TrackingExceptionEvent.new(
          exception_type: exception_type, occurred_at: occurred_at,
          description: description, location_unlocode: location_unlocode
        )
        @status_before_exception ||= @transport_status
        @exceptions << event
        @transport_status = TrackingStatus.exception
        event
      end

      # 未解決の例外を保持しているか。
      def active_exception? = @exceptions.any? { |e| !e.resolved? }

      # 未解決の例外（最初の 1 件）。なければ nil。
      def active_exception = @exceptions.find { |e| !e.resolved? }

      # 未解決かつエスカレーション対象の例外を保持しているか（US20 紛失）。
      def escalated? = @exceptions.any? { |e| !e.resolved? && e.escalation_flag }

      # 例外を解決し、発生前の輸送状態へ復帰する（precondition・T30）。
      # 集約が保持していない例外は解決できない。
      def resolve_exception(event, resolved_at:, resolution_notes: nil)
        raise ArgumentError, "解決対象の例外が集約に存在しません" unless @exceptions.include?(event)

        event.resolve(resolved_at: resolved_at, resolution_notes: resolution_notes)
        return if active_exception?

        @transport_status = @status_before_exception if @status_before_exception
        @status_before_exception = nil
      end
    end
  end
end
