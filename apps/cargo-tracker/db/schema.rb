# This file is auto-generated from the current state of the database. Instead
# of editing this file, please use the migrations feature of Active Record to
# incrementally modify your database, and then regenerate this schema definition.
#
# This file is the source Rails uses to define your schema when running `bin/rails
# db:schema:load`. When creating a new database, `bin/rails db:schema:load` tends to
# be faster and is potentially less error prone than running all of your
# migrations from scratch. Old migrations may fail to apply correctly if those
# migrations use external dependencies or application code.
#
# It's strongly recommended that you check this file into your version control system.

ActiveRecord::Schema[8.0].define(version: 2026_07_29_000012) do
  # These are extensions that must be enabled in order to support this database
  enable_extension "pg_catalog.plpgsql"

  create_table "cargos", force: :cascade do |t|
    t.string "booking_id", limit: 20, null: false
    t.bigint "shipper_id", null: false
    t.string "cargo_type", limit: 30, null: false
    t.decimal "weight_kg", precision: 10, scale: 3, null: false
    t.string "origin_unlocode", limit: 5, null: false
    t.string "destination_unlocode", limit: 5, null: false
    t.date "arrival_deadline", null: false
    t.string "booking_status", limit: 30, default: "preliminary", null: false
    t.decimal "dimension_length", precision: 10, scale: 3
    t.decimal "dimension_width", precision: 10, scale: 3
    t.decimal "dimension_height", precision: 10, scale: 3
    t.integer "quantity"
    t.string "description", limit: 500
    t.string "hazardous_class", limit: 10
    t.string "un_number", limit: 10
    t.string "proper_shipping_name", limit: 200
    t.decimal "min_temperature", precision: 10, scale: 3
    t.decimal "max_temperature", precision: 10, scale: 3
    t.string "temperature_unit", limit: 20
    t.integer "lock_version", default: 0, null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.string "consignee_name", limit: 200
    t.string "consignee_email", limit: 200
    t.string "routing_status", limit: 30, default: "NOT_ROUTED", null: false
    t.string "tracking_number", limit: 20
    t.string "last_handling_event_type", limit: 30
    t.string "last_handling_event_location", limit: 5
    t.string "last_handling_event_voyage", limit: 20
    t.index ["booking_id"], name: "index_cargos_on_booking_id", unique: true
    t.index ["shipper_id"], name: "index_cargos_on_shipper_id"
    t.index ["tracking_number"], name: "index_cargos_on_tracking_number", unique: true
  end

  create_table "carrier_movements", force: :cascade do |t|
    t.bigint "voyage_id", null: false
    t.string "departure_location_unlocode", limit: 5, null: false
    t.string "arrival_location_unlocode", limit: 5, null: false
    t.datetime "departure_date", null: false
    t.datetime "arrival_date", null: false
    t.integer "seq_number", null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["voyage_id", "seq_number"], name: "index_carrier_movements_on_voyage_id_and_seq_number", unique: true
    t.index ["voyage_id"], name: "index_carrier_movements_on_voyage_id"
  end

  create_table "estimates", force: :cascade do |t|
    t.string "estimate_uuid", null: false
    t.string "origin_unlocode", limit: 5, null: false
    t.string "destination_unlocode", limit: 5, null: false
    t.date "arrival_deadline", null: false
    t.string "cargo_type", limit: 30, null: false
    t.decimal "weight_kg", precision: 10, scale: 3, null: false
    t.string "status", limit: 20, default: "CREATED", null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["estimate_uuid"], name: "index_estimates_on_estimate_uuid", unique: true
  end

  create_table "handling_activities", force: :cascade do |t|
    t.string "booking_id", limit: 20, null: false
    t.string "event_type", limit: 30, null: false
    t.datetime "event_completion_time", null: false
    t.string "location_unlocode", limit: 5, null: false
    t.string "voyage_number", limit: 20
    t.string "operator_name", limit: 200
    t.string "recipient_name", limit: 200
    t.string "recipient_signature", limit: 200
    t.string "recipient_confirmation_code", limit: 50
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index "booking_id, event_type, event_completion_time, COALESCE(voyage_number, ''::character varying)", name: "idx_handling_activities_idempotency", unique: true
    t.index ["booking_id", "event_completion_time"], name: "idx_on_booking_id_event_completion_time_dbcd6fd0db"
    t.index ["booking_id"], name: "index_handling_activities_on_booking_id"
  end

  create_table "invoice_line_items", force: :cascade do |t|
    t.bigint "invoice_id", null: false
    t.string "description", limit: 200, null: false
    t.integer "amount_value", null: false
    t.string "amount_currency", limit: 3, null: false
    t.integer "seq_number", null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.string "adjustment_type", limit: 30
    t.index ["invoice_id", "seq_number"], name: "index_invoice_line_items_on_invoice_id_and_seq_number"
    t.index ["invoice_id"], name: "index_invoice_line_items_on_invoice_id"
  end

  create_table "invoices", force: :cascade do |t|
    t.string "invoice_number", limit: 30, null: false
    t.string "booking_id", limit: 20, null: false
    t.integer "total_amount_value", null: false
    t.string "total_amount_currency", limit: 3, null: false
    t.decimal "tax_rate", precision: 5, scale: 4, default: "0.1", null: false
    t.decimal "tax_amount", precision: 15, scale: 2, default: "0.0", null: false
    t.string "payment_status", limit: 30, null: false
    t.datetime "issued_at"
    t.date "due_date"
    t.integer "discount_amount_value"
    t.string "discount_amount_currency", limit: 3
    t.integer "lock_version", default: 0, null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.datetime "paid_at"
    t.integer "base_amount_value"
    t.bigint "shipper_id"
    t.integer "surcharge_amount_value", default: 0, null: false
    t.index ["booking_id"], name: "index_invoices_on_booking_id", unique: true
    t.index ["invoice_number"], name: "index_invoices_on_invoice_number", unique: true
  end

  create_table "legs", force: :cascade do |t|
    t.bigint "cargo_id", null: false
    t.string "voyage_number", limit: 30, null: false
    t.string "load_location_unlocode", limit: 5, null: false
    t.string "unload_location_unlocode", limit: 5, null: false
    t.datetime "load_time"
    t.datetime "unload_time"
    t.integer "seq_number", null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["cargo_id", "seq_number"], name: "index_legs_on_cargo_id_and_seq_number", unique: true
    t.index ["cargo_id"], name: "index_legs_on_cargo_id"
  end

  create_table "locations", force: :cascade do |t|
    t.string "unlocode", limit: 5, null: false
    t.string "name", limit: 100, null: false
    t.string "country_code", limit: 2
    t.string "time_zone", limit: 50
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["unlocode"], name: "index_locations_on_unlocode", unique: true
  end

  create_table "notifications", force: :cascade do |t|
    t.string "notifiable_type", limit: 100, null: false
    t.string "notifiable_id", limit: 50, null: false
    t.string "event_type", limit: 50, null: false
    t.string "recipient_type", limit: 30, null: false
    t.string "recipient_address", limit: 200, null: false
    t.string "subject", limit: 200
    t.text "body"
    t.string "status", limit: 20, default: "pending", null: false
    t.datetime "sent_at"
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["notifiable_type", "notifiable_id", "event_type"], name: "idx_on_notifiable_type_notifiable_id_event_type_7613f3bbd6"
  end

  create_table "payments", force: :cascade do |t|
    t.bigint "invoice_id", null: false
    t.integer "paid_amount_value", null: false
    t.string "paid_amount_currency", limit: 3, null: false
    t.datetime "paid_at", null: false
    t.string "payment_method", limit: 30, null: false
    t.string "transaction_reference", limit: 100
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["invoice_id"], name: "index_payments_on_invoice_id"
  end

  create_table "route_candidates", force: :cascade do |t|
    t.bigint "estimate_id", null: false
    t.string "voyage_number", limit: 20, null: false
    t.string "transit_port", limit: 5
    t.integer "transit_days", null: false
    t.decimal "estimated_cost", precision: 12, scale: 2, null: false
    t.integer "rank", default: 0, null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["estimate_id", "rank"], name: "index_route_candidates_on_estimate_id_and_rank"
    t.index ["estimate_id"], name: "index_route_candidates_on_estimate_id"
  end

  create_table "shippers", force: :cascade do |t|
    t.string "shipper_code", limit: 20, null: false
    t.string "shipper_type", limit: 20, null: false
    t.string "name", limit: 200, null: false
    t.string "address", limit: 500
    t.string "email", limit: 200, null: false
    t.string "phone", limit: 50
    t.string "contract_number", limit: 50
    t.decimal "discount_rate", precision: 5, scale: 4, default: "0.0"
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["email"], name: "index_shippers_on_email", unique: true
    t.index ["shipper_code"], name: "index_shippers_on_shipper_code", unique: true
  end

  create_table "tracking_activities", force: :cascade do |t|
    t.string "tracking_number", limit: 20, null: false
    t.string "booking_id", limit: 20, null: false
    t.string "transport_status", limit: 30, default: "NOT_RECEIVED", null: false
    t.integer "lock_version", default: 0, null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.string "status_before_exception", limit: 30
    t.index ["booking_id"], name: "index_tracking_activities_on_booking_id", unique: true
    t.index ["tracking_number"], name: "index_tracking_activities_on_tracking_number", unique: true
  end

  create_table "tracking_exception_events", force: :cascade do |t|
    t.bigint "tracking_activity_id", null: false
    t.string "exception_type", limit: 50, null: false
    t.datetime "occurred_at", null: false
    t.boolean "escalation_flag", default: false, null: false
    t.string "description", limit: 500
    t.string "location_unlocode", limit: 5
    t.datetime "resolved_at"
    t.text "resolution_notes"
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["tracking_activity_id", "occurred_at"], name: "idx_on_tracking_activity_id_occurred_at_6464dd9c87"
    t.index ["tracking_activity_id"], name: "index_tracking_exception_events_on_tracking_activity_id"
  end

  create_table "tracking_handling_events", force: :cascade do |t|
    t.bigint "tracking_activity_id", null: false
    t.string "event_type", limit: 30, null: false
    t.datetime "event_time", null: false
    t.string "location_unlocode", limit: 5
    t.string "voyage_number", limit: 20
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["tracking_activity_id", "event_time"], name: "idx_on_tracking_activity_id_event_time_3a50768455"
    t.index ["tracking_activity_id"], name: "index_tracking_handling_events_on_tracking_activity_id"
  end

  create_table "user_roles", force: :cascade do |t|
    t.bigint "user_id", null: false
    t.string "role", limit: 50, null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["user_id", "role"], name: "index_user_roles_on_user_id_and_role", unique: true
    t.index ["user_id"], name: "index_user_roles_on_user_id"
  end

  create_table "users", force: :cascade do |t|
    t.string "username", limit: 50, null: false
    t.string "email", limit: 200, null: false
    t.string "password_digest", limit: 255, null: false
    t.boolean "enabled", default: true, null: false
    t.integer "failed_attempts", default: 0, null: false
    t.datetime "locked_at"
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["email"], name: "index_users_on_email", unique: true
    t.index ["username"], name: "index_users_on_username", unique: true
  end

  create_table "voyages", force: :cascade do |t|
    t.string "voyage_number", limit: 20, null: false
    t.string "carrier_name", limit: 100, null: false
    t.string "ship_name", limit: 100
    t.string "supported_cargo_types", limit: 100, default: "GENERAL", null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.integer "lock_version", default: 0, null: false
    t.index ["voyage_number"], name: "index_voyages_on_voyage_number", unique: true
  end

  add_foreign_key "cargos", "shippers"
  add_foreign_key "carrier_movements", "voyages"
  add_foreign_key "invoice_line_items", "invoices"
  add_foreign_key "legs", "cargos"
  add_foreign_key "payments", "invoices"
  add_foreign_key "route_candidates", "estimates", on_delete: :cascade
  add_foreign_key "tracking_exception_events", "tracking_activities"
  add_foreign_key "tracking_handling_events", "tracking_activities"
  add_foreign_key "user_roles", "users"
end
