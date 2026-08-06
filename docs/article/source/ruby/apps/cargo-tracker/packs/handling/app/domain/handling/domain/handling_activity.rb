# frozen_string_literal: true

module Handling
  module Domain
    # 荷役作業の集約ルート（US15/US16）。作業種別・場所・時刻・航海番号・荷受人確認を保持し、
    # 旅程との整合（route_check）を評価する。
    class HandlingActivity
      attr_reader :booking_id, :type, :location, :completion_time, :voyage_number,
                  :operator_name, :recipient_confirmation

      def self.register(booking_id:, type:, location:, completion_time:,
                        voyage_number: nil, operator_name: nil, recipient_confirmation: nil)
        raise ArgumentError, "予約番号は必須です" if booking_id.to_s.strip.empty?
        raise ArgumentError, "作業場所は必須です" if location.to_s.strip.empty?
        raise ArgumentError, "作業日時は必須です" if completion_time.nil?
        if type.requires_voyage_number? && voyage_number.to_s.strip.empty?
          raise ArgumentError, "#{type} には航海番号が必須です"
        end
        if type.claim? && !(recipient_confirmation&.valid?)
          raise ArgumentError, "引取（CLAIM）には荷受人確認が必須です"
        end

        new(booking_id: booking_id, type: type, location: location, completion_time: completion_time,
            voyage_number: voyage_number, operator_name: operator_name,
            recipient_confirmation: recipient_confirmation)
      end

      def self.reconstitute(**attrs) = new(**attrs)

      def initialize(booking_id:, type:, location:, completion_time:,
                     voyage_number: nil, operator_name: nil, recipient_confirmation: nil)
        @booking_id = booking_id
        @type = type
        @location = location
        @completion_time = completion_time
        @voyage_number = voyage_number
        @operator_name = operator_name
        @recipient_confirmation = recipient_confirmation
      end

      # 予定ルートとの整合を評価する（US15）。
      # LOAD/UNLOAD が旅程外 → :misrouted、RECEIVE/CLAIM が想定港以外 → :warning、一致 → :ok。
      # いずれも記録は阻止しない（判定結果に応じ呼び出し側が状態・警告を扱う）。
      def route_check(snapshot)
        if type.route_bound?
          snapshot.route_ports.include?(location) ? :ok : :misrouted
        else
          expected = type.receive? ? snapshot.origin : snapshot.destination
          location == expected ? :ok : :warning
        end
      end
    end
  end
end
