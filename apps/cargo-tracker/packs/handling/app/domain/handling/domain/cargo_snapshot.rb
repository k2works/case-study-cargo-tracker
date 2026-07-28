# frozen_string_literal: true

module Handling
  module Domain
    # 貨物スナップショット（ACL・US15）。Booking Context の公開ビューから射影した読み取り専用の値。
    # 荷役妥当性（route_check）の照合に用いる。Handling は Booking 集約に直接依存しない（ADR-0001/0003）。
    CargoSnapshot = Data.define(:booking_id, :origin, :destination, :leg_locations) do
      def initialize(booking_id:, origin:, destination:, leg_locations: [])
        super(booking_id: booking_id, origin: origin, destination: destination,
              leg_locations: Array(leg_locations))
      end

      # 旅程が経由する港（積地・揚地）の集合。旅程未紐付けなら出発地・目的地のみ。
      def route_ports
        (leg_locations + [ origin, destination ]).compact.uniq
      end
    end
  end
end
