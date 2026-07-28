# frozen_string_literal: true

module Handling
  module Infrastructure
    # 荷役作業リポジトリの Active Record 実装（出力アダプタ）。
    class ActiveRecordHandlingRepository < Domain::HandlingRepository
      def save(activity)
        HandlingActivityRecord.create!(
          booking_id: activity.booking_id,
          event_type: activity.type.value,
          event_completion_time: activity.completion_time,
          location_unlocode: activity.location,
          voyage_number: activity.voyage_number,
          operator_name: activity.operator_name,
          recipient_name: activity.recipient_confirmation&.recipient_name,
          recipient_signature: activity.recipient_confirmation&.signature,
          recipient_confirmation_code: activity.recipient_confirmation&.confirmation_code
        )
        activity
      end

      # クエリ専用の荷役履歴（Read Model・CQRS Query 側）。
      def history_for(booking_id)
        HandlingActivityRecord.where(booking_id: booking_id).order(:event_completion_time).map do |r|
          Domain::HandlingActivityHistory::Entry.new(
            event_type: r.event_type, location: r.location_unlocode,
            voyage_number: r.voyage_number, completion_time: r.event_completion_time,
            operator_name: r.operator_name, recipient_name: r.recipient_name
          )
        end
      end
    end
  end
end
