# frozen_string_literal: true

module Routing
  module Domain
    # 航海リポジトリの出力ポート（ヘキサゴナルの抽象契約）。
    class VoyageRepository
      def save(_voyage)
        raise NotImplementedError, "#{self.class}#save is not implemented"
      end

      def find_by_number(_voyage_number)
        raise NotImplementedError, "#{self.class}#find_by_number is not implemented"
      end

      def exists_by_number?(_number)
        raise NotImplementedError, "#{self.class}#exists_by_number? is not implemented"
      end

      def all(*)
        raise NotImplementedError, "#{self.class}#all is not implemented"
      end
    end
  end
end
