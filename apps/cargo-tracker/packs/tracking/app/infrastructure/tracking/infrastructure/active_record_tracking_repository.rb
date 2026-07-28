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

      private

      def to_domain(record)
        Domain::TrackingActivity.reconstitute(
          tracking_number: Domain::TrackingNumber.new(value: record.tracking_number),
          booking_id: record.booking_id,
          transport_status: Domain::TrackingStatus.new(value: record.transport_status)
        )
      end
    end
  end
end
