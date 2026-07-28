# frozen_string_literal: true

module Booking
  module Application
    # ドメインイベント駆動の通知ハンドラ（ADR-0002）。
    # 集約が発行したイベントを購読し、Shared::Public::NotificationRecorder で送信記録を残す。
    # アプリケーションサービスからの NotificationPort 直接呼び出しは行わない（ADR-0002）。
    module NotificationSubscribers
      # 経路設計者（追跡番号発行依頼）の宛先。MVP は固定アドレス。
      ROUTE_PLANNER_ADDRESS = ENV.fetch("ROUTE_PLANNER_ADDRESS", "route-planner@cargo-tracker.example")

      module_function

      def install!(recorder: Shared::Public::NotificationRecorder.new,
                   shipper_directory: Shipper::Public::ShipperDirectory.new)
        # US12: 経路紐付け → 荷主へ経路通知
        DomainEvents.subscribe("cargo_routed") do |payload|
          recorder.record(
            notifiable_type: "Cargo", notifiable_id: payload[:cargo_id],
            event_type: "ROUTE_NOTIFIED", recipient_type: "SHIPPER",
            recipient_address: shipper_email(shipper_directory, payload[:shipper_id]),
            subject: "経路のご案内",
            body: "#{payload[:origin]} → #{payload[:destination]}（到着予定 #{payload[:expected_arrival_time]}）"
          )
        end

        # US13: 予約確定 → 経路設計者へ追跡番号発行依頼
        DomainEvents.subscribe("cargo_confirmed") do |payload|
          recorder.record(
            notifiable_type: "Cargo", notifiable_id: payload[:cargo_id],
            event_type: "TRACKING_REQUESTED", recipient_type: "OPERATOR",
            recipient_address: ROUTE_PLANNER_ADDRESS,
            subject: "追跡番号発行依頼", body: "予約 #{payload[:cargo_id]} が確定しました。追跡番号を発行してください。"
          )
        end

        # US13: キャンセル → 荷主へキャンセル確認通知
        DomainEvents.subscribe("cargo_cancelled") do |payload|
          recorder.record(
            notifiable_type: "Cargo", notifiable_id: payload[:cargo_id],
            event_type: "BOOKING_CANCELLED", recipient_type: "SHIPPER",
            recipient_address: shipper_email(shipper_directory, payload[:shipper_id]),
            subject: "予約キャンセルのご確認", body: "予約 #{payload[:cargo_id]} をキャンセルしました。"
          )
        end
      end

      def shipper_email(shipper_directory, shipper_id)
        shipper_directory.find(shipper_id)&.email.to_s
      end
    end
  end
end
