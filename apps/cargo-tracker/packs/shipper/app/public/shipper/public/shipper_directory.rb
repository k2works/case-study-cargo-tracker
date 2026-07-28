# frozen_string_literal: true

module Shipper
  module Public
    # Shipper Context の公開参照 API（他コンテキスト・アプリ層向け）。
    # 越境識別子は shippers.id（サロゲート）を用いる（ADR-0003）。
    class ShipperDirectory
      # 公開ビュー（内部集約を晒さない読み取り専用の投影）。
      View = Data.define(:id, :code, :name, :email, :corporate, :discount_percentage) do
        def corporate? = corporate
      end

      # 荷主 ID（shippers.id）の存在確認。Booking の ACL が利用する。
      def exists?(shipper_id)
        Infrastructure::ShipperRecord.exists?(id: shipper_id)
      end

      # 荷主 ID から公開ビューを取得する。未登録なら nil。
      def find(shipper_id)
        record = Infrastructure::ShipperRecord.find_by(id: shipper_id)
        record && to_view(record)
      end

      # 全荷主の公開ビュー一覧。
      def all
        Infrastructure::ShipperRecord.order(:created_at).map { |r| to_view(r) }
      end

      private

      def to_view(record)
        corporate = record.shipper_type == "CORPORATE"
        View.new(id: record.id, code: record.shipper_code, name: record.name,
                 email: record.email, corporate: corporate,
                 discount_percentage: (corporate ? (record.discount_rate * 100).to_i : nil))
      end
    end
  end
end
