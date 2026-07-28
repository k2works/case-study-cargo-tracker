# frozen_string_literal: true

require "capybara/rspec"

RSpec.configure do |config|
  # JS を要しないシナリオは rack_test で高速に実行する。
  config.before(:each, type: :system) do
    driven_by :rack_test
  end
end
