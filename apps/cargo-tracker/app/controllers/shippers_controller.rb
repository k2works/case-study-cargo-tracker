# frozen_string_literal: true

# 荷主登録（US02/US03 / Shipper Context）。営業担当者ロールのみ。
# Shipper Context へは公開 API（Shipper::Public::*）経由でのみアクセスする（ADR-0003・privacy）。
class ShippersController < ApplicationController
  before_action -> { require_role(:sales) }

  def index
    @shippers = directory.all
  end

  def new
    @form = default_form
  end

  def create
    @form = form_params
    result = Shipper::Public::ShipperRegistration.new.call(**use_case_args(@form))

    if result.success?
      redirect_to shippers_path, notice: "荷主を登録しました（荷主コード: #{result.shipper_code}）"
    elsif result.duplicate?
      @existing = result.existing
      flash.now[:alert] = "このメールアドレスは既に登録されています。既存荷主をご確認ください。"
      render :new, status: :unprocessable_entity
    else
      flash.now[:alert] = result.error_message
      render :new, status: :unprocessable_entity
    end
  end

  private

  def directory
    @directory ||= Shipper::Public::ShipperDirectory.new
  end

  def default_form
    { shipper_type: "INDIVIDUAL", name: "", email: "", phone: "", address: "",
      contract_number: "", discount_rate: "" }
  end

  def form_params
    params.permit(:shipper_type, :name, :email, :phone, :address, :contract_number, :discount_rate)
          .to_h.symbolize_keys.reverse_merge(default_form)
  end

  # フォームの割引率（%）をドメインの割引率（0〜0.3）へ変換して渡す。
  def use_case_args(form)
    args = form.slice(:shipper_type, :name, :email, :phone, :address, :contract_number)
    if form[:shipper_type] == "CORPORATE" && form[:discount_rate].present?
      args[:discount_rate] = (BigDecimal(form[:discount_rate].to_s) / 100).to_s
    end
    args
  end
end
