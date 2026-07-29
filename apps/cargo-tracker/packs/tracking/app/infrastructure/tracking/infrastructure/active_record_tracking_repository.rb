# frozen_string_literal: true

module Tracking
  module Infrastructure
    # 追跡活動リポジトリの Active Record 実装（出力アダプタ）。
    # TrackingActivity 集約（PORO）と TrackingActivityRecord（AR）の相互変換を担う。
    class ActiveRecordTrackingRepository < Domain::TrackingRepository
      def save(activity)
        record = TrackingActivityRecord.find_or_initialize_by(tracking_number: activity.tracking_number.value)
        record.assign_attributes(
          booking_id: activity.booking_id,
          transport_status: activity.transport_status.value
        )
        record.save!
        activity
      end

      def find_by_booking_id(booking_id)
        record = TrackingActivityRecord.find_by(booking_id: booking_id)
        record && to_domain(record)
      end

      def find_by_tracking_number(tracking_number)
        record = TrackingActivityRecord.find_by(tracking_number: tracking_number)
        record && to_domain(record)
      end

      def exists_for_booking?(booking_id)
        TrackingActivityRecord.exists?(booking_id: booking_id)
      end

      # 追跡活動の輸送状態を更新し、追跡イベント履歴を 1 件追加する（US15/US17）。
      def append_event(activity, event_type:, event_time:, location: nil, voyage_number: nil)
        TrackingActivityRecord.transaction do
          record = TrackingActivityRecord.lock.find_by!(tracking_number: activity.tracking_number.value)
          record.update!(transport_status: activity.transport_status.value)
          TrackingHandlingEventRecord.create!(
            tracking_activity_id: record.id, event_type: event_type, event_time: event_time,
            location_unlocode: location, voyage_number: voyage_number
          )
        end
        activity
      end

      def events_for(booking_id)
        activity = TrackingActivityRecord.find_by(booking_id: booking_id)
        return [] if activity.nil?

        TrackingHandlingEventRecord.where(tracking_activity_id: activity.id).order(:event_time).map do |e|
          { event_type: e.event_type, event_time: e.event_time,
            location: e.location_unlocode, voyage_number: e.voyage_number }
        end
      end

      # 例外イベント一覧（CQRS 読み取り・US19/US20 例外管理一覧）。発生日時の新しい順。
      def list_exceptions
        sql = <<~SQL.squish
          SELECT e.exception_type, e.occurred_at, e.escalation_flag, e.description,
                 e.location_unlocode, e.resolved_at, a.tracking_number, a.booking_id
          FROM tracking_exception_events e
          JOIN tracking_activities a ON a.id = e.tracking_activity_id
          ORDER BY e.occurred_at DESC
        SQL
        ApplicationRecord.connection.select_all(sql).map { |row| row.symbolize_keys }
      end

      # 予約に紐づく最新の新到着予定日（遅延例外の対応報告で設定・US18 推定到着日に優先・T37）。
      def revised_arrival_date_for(booking_id)
        sql = <<~SQL.squish
          SELECT e.revised_arrival_date
          FROM tracking_exception_events e
          JOIN tracking_activities a ON a.id = e.tracking_activity_id
          WHERE a.booking_id = #{ApplicationRecord.connection.quote(booking_id)}
            AND e.revised_arrival_date IS NOT NULL
          ORDER BY e.occurred_at DESC LIMIT 1
        SQL
        row = ApplicationRecord.connection.select_one(sql)
        row && row["revised_arrival_date"]
      end

      # 例外を登録し、輸送状態を EXCEPTION に更新する（US19/US20）。集約ルートで悲観ロック。
      # 発生前状態（status_before_exception）を集約状態として永続化し、解決時の復帰を正確にする（T30）。
      def save_exception(activity, event)
        TrackingActivityRecord.transaction do
          record = TrackingActivityRecord.lock.find_by!(tracking_number: activity.tracking_number.value)
          record.update!(transport_status: activity.transport_status.value,
                         status_before_exception: activity.status_before_exception&.value)
          TrackingExceptionEventRecord.create!(
            tracking_activity_id: record.id, exception_type: event.exception_type.value,
            occurred_at: event.occurred_at, escalation_flag: event.escalation_flag,
            description: event.description, location_unlocode: event.location_unlocode
          )
        end
        activity
      end

      # 例外を解決し、輸送状態を復帰させる（US19/US20 対応報告）。解決済み例外行へ対応内容を反映。
      # 復帰後は status_before_exception をクリアする。
      def resolve_exception(activity)
        TrackingActivityRecord.transaction do
          record = TrackingActivityRecord.lock.find_by!(tracking_number: activity.tracking_number.value)
          record.update!(transport_status: activity.transport_status.value,
                         status_before_exception: activity.status_before_exception&.value)
          activity.exceptions.select(&:resolved?).each do |event|
            scope = TrackingExceptionEventRecord.where(tracking_activity_id: record.id)
            row = event.id ? scope.find_by(id: event.id) : scope.where(resolved_at: nil).order(:occurred_at).first
            row&.update!(resolved_at: event.resolved_at, resolution_notes: event.resolution_notes,
                         revised_arrival_date: event.revised_arrival_date)
          end
        end
        activity
      end

      private

      def to_domain(record)
        Domain::TrackingActivity.reconstitute(
          tracking_number: Domain::TrackingNumber.new(value: record.tracking_number),
          booking_id: record.booking_id,
          transport_status: Domain::TrackingStatus.new(value: record.transport_status),
          exceptions: exceptions_of(record),
          status_before_exception: status_before_exception_of(record)
        )
      end

      # 永続化された発生前状態を復元する（履歴からの再導出はしない・US17 手動更新も正確に復帰）。
      def status_before_exception_of(record)
        value = record.status_before_exception
        value && Domain::TrackingStatus.new(value: value)
      end

      def exceptions_of(record)
        TrackingExceptionEventRecord.where(tracking_activity_id: record.id).order(:occurred_at).map do |e|
          Domain::TrackingExceptionEvent.new(
            id: e.id, exception_type: Domain::ExceptionType.new(value: e.exception_type),
            occurred_at: e.occurred_at, description: e.description, location_unlocode: e.location_unlocode,
            escalation_flag: e.escalation_flag, resolved_at: e.resolved_at, resolution_notes: e.resolution_notes,
            revised_arrival_date: e.revised_arrival_date
          )
        end
      end
    end
  end
end
