# frozen_string_literal: true

require "rails_helper"

RSpec.describe Shared::Domain::Location do
  describe "生成" do
    it "UN/LOCODE と名称を保持する" do
      loc = described_class.new(unlocode: "JPTYO", name: "Tokyo")
      expect(loc.unlocode).to eq("JPTYO")
      expect(loc.name).to eq("Tokyo")
    end

    it "UN/LOCODE は 5 文字の英大文字/数字" do
      expect { described_class.new(unlocode: "JPT", name: "X") }.to raise_error(ArgumentError)
      expect { described_class.new(unlocode: "jptyo", name: "X") }.to raise_error(ArgumentError)
    end

    it "名称は必須" do
      expect { described_class.new(unlocode: "JPTYO", name: "") }.to raise_error(ArgumentError)
    end
  end

  describe "#same_as?" do
    it "同一 UN/LOCODE なら真" do
      a = described_class.new(unlocode: "JPTYO", name: "Tokyo")
      b = described_class.new(unlocode: "JPTYO", name: "東京")
      expect(a.same_as?(b)).to be true
    end

    it "異なる UN/LOCODE なら偽" do
      a = described_class.new(unlocode: "JPTYO", name: "Tokyo")
      b = described_class.new(unlocode: "USLAX", name: "LA")
      expect(a.same_as?(b)).to be false
    end
  end

  describe "値等価" do
    it "同一 UN/LOCODE・名称なら等価" do
      expect(described_class.new(unlocode: "JPTYO", name: "Tokyo"))
        .to eq(described_class.new(unlocode: "JPTYO", name: "Tokyo"))
    end
  end
end
