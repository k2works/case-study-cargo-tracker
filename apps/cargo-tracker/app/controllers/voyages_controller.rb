# frozen_string_literal: true

# 航路（Routing Context / US24・US25・US07）。MVP では営業担当者が経路設計者を代替。
# Routing Context へは公開 API（Routing::Public::VoyageDirectory）経由でのみアクセスする。
class VoyagesController < ApplicationController
  before_action -> { require_role(:sales) }
  before_action :set_voyage, only: %i[show edit]

  def index
    @search = search_params
    @voyages = if @search.values.any?(&:present?)
                 directory.search(**search_args(@search))
    else
                 directory.all
    end
  end

  def show
    # @voyage は set_voyage（before_action）で取得済み。ビューが表示する。
  end

  def new
    @form = default_form
  end

  def create
    @form = voyage_params
    result = directory.register(**register_args(@form))
    if result.success?
      redirect_to voyage_path(result.voyage_number), notice: "航海スケジュールを登録しました（航海番号: #{result.voyage_number}）"
    else
      flash.now[:alert] = result.error_message
      render :new, status: :unprocessable_entity
    end
  end

  def edit
    # @voyage は set_voyage（before_action）で取得済み。編集フォームを表示する。
  end

  def update
    @form = voyage_params.merge(voyage_number: params[:id])
    result = directory.update(**update_args(@form))
    if result.success?
      redirect_to voyage_path(params[:id]), notice: "航海スケジュールを更新しました"
    else
      @voyage = directory.find(params[:id])
      flash.now[:alert] = result.error_message
      render :edit, status: :unprocessable_entity
    end
  end

  private

  def set_voyage
    @voyage = directory.find(params[:id])
    redirect_to voyages_path, alert: "航海が見つかりません" unless @voyage
  end

  def directory
    @directory ||= Routing::Public::VoyageDirectory.new
  end

  def default_form
    { voyage_number: "", carrier_name: "", ship_name: "", origin: "", destination: "",
      departure_date: "", arrival_date: "", supported_cargo_types: %w[GENERAL] }
  end

  def voyage_params
    params.permit(:voyage_number, :carrier_name, :ship_name, :origin, :destination,
                  :departure_date, :arrival_date, supported_cargo_types: [])
          .to_h.symbolize_keys.reverse_merge(default_form)
  end

  def search_params
    { origin: params[:origin], destination: params[:destination],
      departure_from: params[:departure_from], departure_to: params[:departure_to],
      cargo_type: params[:cargo_type] }
  end

  def search_args(search)
    search.compact_blank
  end

  # フォームの単一区間を movements 配列に変換する（IT3 は出発港→到着港の 1 区間）。
  def register_args(form)
    {
      voyage_number: form[:voyage_number], carrier_name: form[:carrier_name], ship_name: form[:ship_name],
      supported_cargo_types: Array(form[:supported_cargo_types]).presence || %w[GENERAL],
      movements: [ { departure_unlocode: form[:origin], arrival_unlocode: form[:destination],
                     departure_date: form[:departure_date], arrival_date: form[:arrival_date], seq_number: 1 } ]
    }
  end

  def update_args(form)
    {
      voyage_number: form[:voyage_number], carrier_name: form[:carrier_name].presence,
      ship_name: form[:ship_name].presence,
      supported_cargo_types: Array(form[:supported_cargo_types]).presence,
      movements: [ { departure_unlocode: form[:origin], arrival_unlocode: form[:destination],
                     departure_date: form[:departure_date], arrival_date: form[:arrival_date], seq_number: 1 } ]
    }
  end
end
