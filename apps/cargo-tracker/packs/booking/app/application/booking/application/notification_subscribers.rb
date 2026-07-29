# frozen_string_literal: true

module Booking
  module Application
    # ドメインイベント駆動の通知ハンドラ（ADR-0002）。
    # 集約が発行したイベントを購読し、Shared::Public::NotificationRecorder で送信記録を残す。
    # アプリケーションサービスからの NotificationPort 直接呼び出しは行わない（ADR-0002）。
    module NotificationSubscribers
      # 経路設計者（追跡番号発行依頼）の宛先。MVP は固定アドレス。
      ROUTE_PLANNER_ADDRESS = ENV.fetch("ROUTE_PLANNER_ADDRESS", "route-planner@cargo-tracker.example")
      # 営業担当者（条件協議依頼）の宛先。MVP は固定アドレス。
      SALES_ADDRESS = ENV.fetch("SALES_ADDRESS", "sales@cargo-tracker.example")
      # 管理職（重大例外エスカレーション）の宛先。MVP は固定アドレス。
      MANAGER_ADDRESS = ENV.fetch("MANAGER_ADDRESS", "manager@cargo-tracker.example")
      # 精算完了通知の宛先。MVP は固定アドレス（invoice_settled は shipper_id を持たないため）。
      SETTLEMENT_NOTICE_ADDRESS = ENV.fetch("SETTLEMENT_NOTICE_ADDRESS", "shipper@cargo-tracker.example")
      # 例外種別の表示ラベル（例外通知本文用）。
      EXCEPTION_LABELS = { "DELAY" => "遅延", "DAMAGE" => "破損", "LOST" => "紛失", "CUSTOMS_HOLD" => "税関保留" }.freeze
      # 荷役作業種別の表示ラベル（状態変更通知本文用）。
      HANDLING_LABELS = { "RECEIVE" => "受領", "LOAD" => "積込", "UNLOAD" => "荷降し", "CLAIM" => "引取" }.freeze

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

        # US10: 条件を満たす経路がない → 営業担当者へ荷主との条件協議依頼
        DomainEvents.subscribe("cargo_consultation_requested") do |payload|
          recorder.record(
            notifiable_type: "Cargo", notifiable_id: payload[:cargo_id],
            event_type: "CONSULTATION_REQUESTED", recipient_type: "SALES",
            recipient_address: SALES_ADDRESS,
            subject: "荷主との条件協議依頼",
            body: "予約 #{payload[:cargo_id]}（#{payload[:origin]}→#{payload[:destination]}）は条件を満たす経路がありません。荷主と着日・経由地の再協議を依頼します。"
          )
        end

        # US14: 追跡番号発行 → 荷主へ追跡番号と追跡方法を通知
        DomainEvents.subscribe("tracking_number_issued") do |payload|
          recorder.record(
            notifiable_type: "Cargo", notifiable_id: payload[:cargo_id],
            event_type: "TRACKING_ISSUED", recipient_type: "SHIPPER",
            recipient_address: shipper_email(shipper_directory, payload[:shipper_id]),
            subject: "追跡番号のご案内",
            body: "追跡番号 #{payload[:tracking_number]} を発行しました。追跡画面から輸送状況をご確認いただけます。"
          )
        end

        # US15/US16: 荷役記録 → 荷主へ状態変更通知
        DomainEvents.subscribe("handling_activity_registered") do |payload|
          label = HANDLING_LABELS.fetch(payload[:event_type], payload[:event_type])
          recorder.record(
            notifiable_type: "Cargo", notifiable_id: payload[:booking_id],
            event_type: "HANDLING_#{payload[:event_type]}", recipient_type: "SHIPPER",
            recipient_address: shipper_email(shipper_directory, payload[:shipper_id]),
            subject: "貨物状態の更新（#{label}）",
            body: "#{payload[:location]} にて#{label}作業が完了しました。"
          )
        end

        # US17: 貨物状態手動更新 → 荷主へ状態変更通知
        DomainEvents.subscribe("tracking_status_updated") do |payload|
          recorder.record(
            notifiable_type: "Cargo", notifiable_id: payload[:booking_id],
            event_type: "STATUS_UPDATED", recipient_type: "SHIPPER",
            recipient_address: shipper_email(shipper_directory, payload[:shipper_id]),
            subject: "貨物状態の更新",
            body: "貨物状態が #{payload[:transport_status]}#{payload[:location] ? "（#{payload[:location]}）" : ''} に更新されました。"
          )
        end

        # US19/US20: 例外検知 → 荷主へ例外通知。紛失（escalation_flag）は管理職へ緊急通知も送る。
        DomainEvents.subscribe("tracking_exception_detected") do |payload|
          label = EXCEPTION_LABELS.fetch(payload[:exception_type], payload[:exception_type])
          recorder.record(
            notifiable_type: "Cargo", notifiable_id: payload[:booking_id],
            event_type: "EXCEPTION_#{payload[:exception_type]}", recipient_type: "SHIPPER",
            recipient_address: shipper_email(shipper_directory, payload[:shipper_id]),
            subject: "貨物の例外発生（#{label}）",
            body: "貨物に#{label}が発生しました。#{payload[:description]}"
          )
          if payload[:escalation_flag]
            recorder.record(
              notifiable_type: "Cargo", notifiable_id: payload[:booking_id],
              event_type: "EXCEPTION_ESCALATION", recipient_type: "MANAGER",
              recipient_address: MANAGER_ADDRESS,
              subject: "【緊急】重大例外の発生（#{label}）",
              body: "貨物 #{payload[:booking_id]} に#{label}が発生しました。至急対応してください。"
            )
          end
        end

        # US19/US20: 例外解決（対応報告）→ 荷主へ対応報告通知
        DomainEvents.subscribe("tracking_exception_resolved") do |payload|
          label = EXCEPTION_LABELS.fetch(payload[:exception_type], payload[:exception_type])
          recorder.record(
            notifiable_type: "Cargo", notifiable_id: payload[:booking_id],
            event_type: "EXCEPTION_RESOLVED", recipient_type: "SHIPPER",
            recipient_address: shipper_email(shipper_directory, payload[:shipper_id]),
            subject: "貨物例外への対応報告（#{label}）",
            body: "#{label}への対応: #{payload[:resolution_notes]}"
          )
        end

        # US23: 請求書発行 → 荷主へ精算書発行通知
        DomainEvents.subscribe("invoice_created") do |payload|
          recorder.record(
            notifiable_type: "Cargo", notifiable_id: payload[:booking_id],
            event_type: "INVOICE_CREATED", recipient_type: "SHIPPER",
            recipient_address: shipper_email(shipper_directory, payload[:shipper_id]),
            subject: "請求書発行のご案内",
            body: "請求書 #{payload[:invoice_number]}（請求金額 #{payload[:total_amount]} 円・支払期限 #{payload[:due_date]}）を発行しました。"
          )
        end

        # US23: 精算完了 → 荷主へ精算完了通知（shipper_id から実宛先を解決・固定アドレスにフォールバック）
        DomainEvents.subscribe("invoice_settled") do |payload|
          address = shipper_email(shipper_directory, payload[:shipper_id]).presence || SETTLEMENT_NOTICE_ADDRESS
          recorder.record(
            notifiable_type: "Cargo", notifiable_id: payload[:booking_id],
            event_type: "INVOICE_SETTLED", recipient_type: "SHIPPER",
            recipient_address: address,
            subject: "精算完了のご案内", body: "請求書 #{payload[:invoice_number]} の入金を確認し精算が完了しました。"
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
