# frozen_string_literal: true

# 貨物予約（Booking Context / US04・US05・US06）。営業担当者ロールのみ。
# Booking Context へは公開 API（Booking::Public::*）経由でのみアクセスする（privacy）。
class BookingsController < ApplicationController
  before_action -> { require_role(:sales) }

  def index
    @bookings = service.all
  end

  def new
    @form = default_form
    @shippers = shipper_directory.all
  end

  def create
    @form = form_params
    @shippers = shipper_directory.all
    result = service.book(**use_case_args(@form))

    if result.success?
      redirect_to booking_path(result.booking_id), notice: "予約を登録しました（予約番号: #{result.booking_id}）"
    else
      flash.now[:alert] = result.error_message
      render :new, status: :unprocessable_entity
    end
  end

  def show
    @booking = service.find(params[:id])
    return redirect_to bookings_path, alert: "予約が見つかりません" if @booking.nil?

    @notifications = service.notifications(params[:id])
  end

  # 経路設計者への引き渡し（US06）。
  def assign_routing
    case service.assign_to_routing(params[:id])
    when :ok
      redirect_to booking_path(params[:id]), notice: "経路設計を依頼しました"
    when :invalid
      redirect_to booking_path(params[:id]), alert: "この予約は引き渡しできない状態です"
    else
      redirect_to bookings_path, alert: "予約が見つかりません"
    end
  end

  # 予約確定（US13・→CONFIRMED）。
  def confirm
    transition_and_redirect(service.confirm(params[:id]),
                            ok: "予約を確定しました", invalid: "この予約は確定できない状態です")
  end

  # 予約キャンセル（US13・→CANCELLED）。
  def cancel
    transition_and_redirect(service.cancel(params[:id]),
                            ok: "予約をキャンセルしました", invalid: "この予約はキャンセルできない状態です")
  end

  # ルート変更差戻し（US13・ROUTE_PROPOSED→ROUTE_REQUESTED）。
  def reroute
    transition_and_redirect(service.request_rerouting(params[:id]),
                            ok: "経路設計中へ差し戻しました", invalid: "この予約は差戻しできない状態です")
  end

  private

  def transition_and_redirect(status, ok:, invalid:)
    case status
    when :ok
      redirect_to booking_path(params[:id]), notice: ok
    when :invalid
      redirect_to booking_path(params[:id]), alert: invalid
    else
      redirect_to bookings_path, alert: "予約が見つかりません"
    end
  end

  def service
    @service ||= Booking::Public::CargoBookingService.new
  end

  def shipper_directory
    @shipper_directory ||= Shipper::Public::ShipperDirectory.new
  end

  def default_form
    { cargo_type: "GENERAL", shipper_id: "", weight_kg: "", origin: "", destination: "",
      arrival_deadline: "", description: "", hazardous_class: "", un_number: "",
      proper_shipping_name: "", min_temperature: "", max_temperature: "", temperature_unit: "CELSIUS" }
  end

  def form_params
    params.permit(:cargo_type, :shipper_id, :weight_kg, :origin, :destination, :arrival_deadline,
                  :description, :hazardous_class, :un_number, :proper_shipping_name,
                  :min_temperature, :max_temperature, :temperature_unit)
          .to_h.symbolize_keys.reverse_merge(default_form)
  end

  # フォーム値をユースケース引数（値オブジェクト）へ変換する。
  def use_case_args(form)
    args = {
      shipper_id: form[:shipper_id].to_i,
      cargo_type: form[:cargo_type],
      weight_kg: form[:weight_kg],
      origin: form[:origin],
      destination: form[:destination],
      arrival_deadline: form[:arrival_deadline],
      description: form[:description].presence
    }
    args[:hazardous_declaration] = hazardous_declaration(form) if form[:cargo_type] == "HAZARDOUS"
    args[:temperature_requirement] = temperature_requirement(form) if form[:cargo_type] == "REFRIGERATED"
    args
  end

  def hazardous_declaration(form)
    return nil if form[:hazardous_class].blank?

    Booking::Public::CargoBookingService.hazardous_declaration(
      hazardous_class: form[:hazardous_class], un_number: form[:un_number],
      proper_shipping_name: form[:proper_shipping_name]
    )
  end

  def temperature_requirement(form)
    return nil if form[:temperature_unit].blank? || form[:min_temperature].blank?

    Booking::Public::CargoBookingService.temperature_requirement(
      min_temperature: form[:min_temperature].to_d, max_temperature: form[:max_temperature].to_d,
      unit: form[:temperature_unit]
    )
  end
end
