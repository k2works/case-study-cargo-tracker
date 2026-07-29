# frozen_string_literal: true

# 見積（Estimation Context・US01）。営業担当者ロール。
# Estimation Context へは公開 API 経由でのみアクセスする。
class EstimatesController < ApplicationController
  before_action -> { require_role(:sales) }

  # 見積で選択できる貨物種別（US01）。
  CARGO_TYPES = %w[GENERAL HAZARDOUS REFRIGERATED].freeze

  def index
    @estimates = estimation_service.all
  end

  def new; end

  def show
    @estimate = estimation_service.find(params[:id])
    redirect_to estimates_path, alert: "見積が見つかりません" if @estimate.nil?
  end

  # 輸送見積を作成する（US01）。
  def create
    result = estimation_service.create_estimate(
      origin: params[:origin], destination: params[:destination],
      arrival_deadline: params[:arrival_deadline], cargo_type: params[:cargo_type],
      weight_kg: params[:weight_kg]
    )
    case result.status
    when :ok
      redirect_to estimate_path(result.estimate_id), notice: "見積を作成しました"
    when :no_route
      redirect_to new_estimate_path, alert: "希望期限に間に合うルートが見つかりませんでした"
    else
      redirect_to new_estimate_path, alert: "入力内容が不正です: #{result.error_message}"
    end
  end

  private

  def estimation_service
    @estimation_service ||= Estimation::Public::EstimationService.new
  end
end
