# frozen_string_literal: true

module Handling
  module Domain
    # 荷役作業履歴（Read Model・CQRS Query 側）。時系列の荷役イベントを保持し、
    # 最新完了イベントを問い合わせる。集約（HandlingActivity）とは切り離す。
    class HandlingActivityHistory
      Entry = Data.define(:event_type, :location, :voyage_number, :completion_time,
                          :operator_name, :recipient_name)

      def initialize(entries)
        @entries = entries.sort_by(&:completion_time)
      end

      attr_reader :entries

      def most_recently_completed_event = @entries.last
      def empty? = @entries.empty?
    end
  end
end
