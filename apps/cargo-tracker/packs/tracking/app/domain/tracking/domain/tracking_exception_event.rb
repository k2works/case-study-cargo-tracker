# frozen_string_literal: true

module Tracking
  module Domain
    # 追跡例外イベント（TrackingExceptionEvent）。TrackingActivity 集約内のエンティティ。
    # 例外の発生（種別・日時・場所・説明）と解決（解決時刻・対応メモ）を管理する（US19/US20）。
    # LOST 例外は生成時にエスカレーションフラグを立てる。
    class TrackingExceptionEvent
      attr_reader :exception_type, :occurred_at, :description, :location_unlocode,
                  :escalation_flag, :resolved_at, :resolution_notes

      def initialize(exception_type:, occurred_at:, description: nil, location_unlocode: nil,
                     escalation_flag: nil, resolved_at: nil, resolution_notes: nil)
        raise ArgumentError, "例外種別は必須です" unless exception_type.is_a?(ExceptionType)
        raise ArgumentError, "発生日時は必須です" if occurred_at.nil?

        @exception_type = exception_type
        @occurred_at = occurred_at
        @description = description
        @location_unlocode = location_unlocode
        # 明示指定がなければ種別から導出（LOST=true）。
        @escalation_flag = escalation_flag.nil? ? exception_type.escalation_required? : escalation_flag
        @resolved_at = resolved_at
        @resolution_notes = resolution_notes
      end

      # 対応内容を記録して例外を解決する（US19/US20 対応報告）。
      def resolve(resolved_at:, resolution_notes: nil)
        @resolved_at = resolved_at
        @resolution_notes = resolution_notes
      end

      # 解決済みか（resolved_at が設定されていれば解決済み）。
      def resolved? = !resolved_at.nil?
    end
  end
end
