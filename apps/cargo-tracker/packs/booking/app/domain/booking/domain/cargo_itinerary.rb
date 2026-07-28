# frozen_string_literal: true

module Booking
  module Domain
    # 旅程（CargoItinerary）。1 区間以上の Leg 列からなり、隣接脚は連結（Leg[n].unload == Leg[n+1].load）する。
    # 出発地・目的地・到着予定時刻を導出する不変オブジェクト。
    CargoItinerary = Data.define(:legs) do
      def initialize(legs:)
        raise ArgumentError, "旅程には 1 区間以上の脚が必要です" if legs.nil? || legs.empty?

        legs.each_cons(2) do |current, following|
          if current.unload_location != following.load_location
            raise ArgumentError,
                  "脚が連結しません: #{current.unload_location} → #{following.load_location}"
          end
        end

        # 到着期限判定（satisfied_by?）に用いるため最終脚の荷揚時刻は必須とする。
        raise ArgumentError, "最終脚の荷揚時刻は必須です" if legs.last.unload_time.nil?

        super
      end

      def origin = legs.first.load_location
      def destination = legs.last.unload_location
      def expected_arrival_time = legs.last.unload_time
    end
  end
end
