# frozen_string_literal: true

module Booking
  module Domain
    # 貨物予約リポジトリの出力ポート（ヘキサゴナルの抽象契約）。
    class CargoRepository
      def save(_cargo)
        raise NotImplementedError, "#{self.class}#save is not implemented"
      end

      def find_by_booking_id(_booking_id)
        raise NotImplementedError, "#{self.class}#find_by_booking_id is not implemented"
      end

      def find_by_tracking_number(_tracking_number)
        raise NotImplementedError, "#{self.class}#find_by_tracking_number is not implemented"
      end

      def all(*)
        raise NotImplementedError, "#{self.class}#all is not implemented"
      end
    end
  end
end
