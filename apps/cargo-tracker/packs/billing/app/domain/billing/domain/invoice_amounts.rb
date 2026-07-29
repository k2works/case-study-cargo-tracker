# frozen_string_literal: true

module Billing
  module Domain
    # 請求書の金額内訳（基本・割引率・割増・税・合計）を束ねる値オブジェクト。
    # Invoice 集約の金額引数を集約し、一貫した受け渡しを担う。
    InvoiceAmounts = Data.define(:base, :discount_rate, :surcharge, :tax, :total)
  end
end
