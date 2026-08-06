# frozen_string_literal: true

module Shipper
  module Domain
    # 荷主リポジトリの出力ポート（ヘキサゴナルの抽象契約）。
    # アプリケーション層はこの抽象に依存し、具象アダプタは Infrastructure 層で実装する。
    # Ruby は duck typing だが、依存方向を明示・テスト可能にするため抽象基底として定義する。
    class ShipperRepository
      # 集約を永続化する。
      def save(_shipper)
        raise NotImplementedError, "#{self.class}#save is not implemented"
      end

      # 荷主コードで集約を復元する。
      def find_by_code(_code)
        raise NotImplementedError, "#{self.class}#find_by_code is not implemented"
      end

      # メールアドレスで集約を復元する。
      def find_by_email(_email)
        raise NotImplementedError, "#{self.class}#find_by_email is not implemented"
      end

      # メールアドレスの登録有無を返す。
      def exists_by_email?(_email)
        raise NotImplementedError, "#{self.class}#exists_by_email? is not implemented"
      end

      # 全集約を返す。
      def all(*)
        raise NotImplementedError, "#{self.class}#all is not implemented"
      end
    end
  end
end
