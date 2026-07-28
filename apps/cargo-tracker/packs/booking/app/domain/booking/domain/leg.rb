# frozen_string_literal: true

module Booking
  module Domain
    # 旅程の 1 区間（脚）。積地・揚地（UN/LOCODE）・航海番号・積揚時刻を持つ不変オブジェクト。
    Leg = Data.define(:load_location, :unload_location, :voyage_number, :load_time, :unload_time) do
      LEG_UNLOCODE_FORMAT = /\A[A-Z0-9]{5}\z/

      def initialize(load_location:, unload_location:, voyage_number:, load_time: nil, unload_time: nil)
        raise ArgumentError, "積地は UN/LOCODE 形式（5 文字）です: #{load_location}" unless LEG_UNLOCODE_FORMAT.match?(load_location.to_s)
        raise ArgumentError, "揚地は UN/LOCODE 形式（5 文字）です: #{unload_location}" unless LEG_UNLOCODE_FORMAT.match?(unload_location.to_s)
        raise ArgumentError, "積地と揚地は異なる必要があります" if load_location == unload_location
        raise ArgumentError, "航海番号は必須です" if voyage_number.nil? || voyage_number.to_s.strip.empty?

        super
      end
    end
  end
end
