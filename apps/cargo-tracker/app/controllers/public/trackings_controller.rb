# frozen_string_literal: true

module Public
  # 公開貨物追跡（認証不要 / 未認証ユーザーの入口）。プレースホルダ。
  class TrackingsController < ApplicationController
    include Placeholder

    skip_before_action :require_login

    def new = placeholder("公開貨物追跡入力")
    def show = placeholder("公開貨物追跡")
  end
end
