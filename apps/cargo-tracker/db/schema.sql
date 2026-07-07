\restrict dbmate

-- Dumped from database version 16.14
-- Dumped by pg_dump version 17.7

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: cargo_tracker_e2e; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA cargo_tracker_e2e;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: cargo; Type: TABLE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE TABLE cargo_tracker_e2e.cargo (
    id bigint NOT NULL,
    booking_id character varying(20) NOT NULL,
    shipper_id bigint NOT NULL,
    origin_unlocode character varying(5) NOT NULL,
    destination_unlocode character varying(5) NOT NULL,
    deadline timestamp with time zone NOT NULL,
    booking_status character varying(20) NOT NULL,
    version bigint DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    cargo_type character varying(20) DEFAULT 'GENERAL'::character varying NOT NULL,
    hazardous_class character varying(10),
    un_number character varying(10),
    proper_shipping_name text,
    min_temperature numeric,
    max_temperature numeric,
    temperature_unit character varying(1),
    itinerary_id uuid,
    cancellation_rate numeric(4,3),
    cancellation_tier character varying(10),
    cancellation_calculated_at timestamp with time zone,
    confirmed_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    CONSTRAINT cargo_booking_id_format CHECK (((booking_id)::text ~ '^BK-[A-Z0-9]{6}$'::text)),
    CONSTRAINT cargo_booking_status_check CHECK (((booking_status)::text = ANY ((ARRAY['Draft'::character varying, 'Submitted'::character varying, 'RouteProposed'::character varying, 'Confirmed'::character varying, 'Closed'::character varying])::text[]))),
    CONSTRAINT cargo_cancellation_rate_check CHECK (((cancellation_rate IS NULL) OR ((cancellation_rate >= 0.000) AND (cancellation_rate <= 1.000)))),
    CONSTRAINT cargo_cancellation_tier_check CHECK (((cancellation_tier IS NULL) OR ((cancellation_tier)::text = ANY ((ARRAY['FREE'::character varying, 'PARTIAL'::character varying, 'FULL'::character varying])::text[])))),
    CONSTRAINT cargo_hazardous_fields CHECK (((((cargo_type)::text = 'HAZARDOUS'::text) AND (hazardous_class IS NOT NULL) AND (un_number IS NOT NULL) AND (proper_shipping_name IS NOT NULL)) OR ((cargo_type)::text <> 'HAZARDOUS'::text))),
    CONSTRAINT cargo_refrigerated_fields CHECK (((((cargo_type)::text = 'REFRIGERATED'::text) AND (min_temperature IS NOT NULL) AND (max_temperature IS NOT NULL) AND (temperature_unit IS NOT NULL) AND ((temperature_unit)::text = ANY ((ARRAY['C'::character varying, 'F'::character varying])::text[]))) OR ((cargo_type)::text <> 'REFRIGERATED'::text))),
    CONSTRAINT cargo_type_check CHECK (((cargo_type)::text = ANY ((ARRAY['GENERAL'::character varying, 'HAZARDOUS'::character varying, 'REFRIGERATED'::character varying])::text[])))
);


--
-- Name: cargo_id_seq; Type: SEQUENCE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE SEQUENCE cargo_tracker_e2e.cargo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cargo_id_seq; Type: SEQUENCE OWNED BY; Schema: cargo_tracker_e2e; Owner: -
--

ALTER SEQUENCE cargo_tracker_e2e.cargo_id_seq OWNED BY cargo_tracker_e2e.cargo.id;


--
-- Name: carrier_movement; Type: TABLE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE TABLE cargo_tracker_e2e.carrier_movement (
    id bigint NOT NULL,
    voyage_id bigint NOT NULL,
    seq_number integer NOT NULL,
    departure_location_unlocode character varying(5) NOT NULL,
    arrival_location_unlocode character varying(5) NOT NULL,
    departure_time timestamp with time zone NOT NULL,
    arrival_time timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT carrier_movement_time_order CHECK ((departure_time < arrival_time))
);


--
-- Name: carrier_movement_id_seq; Type: SEQUENCE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE SEQUENCE cargo_tracker_e2e.carrier_movement_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: carrier_movement_id_seq; Type: SEQUENCE OWNED BY; Schema: cargo_tracker_e2e; Owner: -
--

ALTER SEQUENCE cargo_tracker_e2e.carrier_movement_id_seq OWNED BY cargo_tracker_e2e.carrier_movement.id;


--
-- Name: confirmation_code; Type: TABLE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE TABLE cargo_tracker_e2e.confirmation_code (
    id bigint NOT NULL,
    booking_id character varying(20) NOT NULL,
    issued_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    attempt_count integer DEFAULT 0 NOT NULL,
    version integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    code_hash character varying(72) NOT NULL,
    CONSTRAINT confirmation_code_attempt_count_check CHECK (((attempt_count >= 0) AND (attempt_count <= 5)))
);


--
-- Name: confirmation_code_id_seq; Type: SEQUENCE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE SEQUENCE cargo_tracker_e2e.confirmation_code_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: confirmation_code_id_seq; Type: SEQUENCE OWNED BY; Schema: cargo_tracker_e2e; Owner: -
--

ALTER SEQUENCE cargo_tracker_e2e.confirmation_code_id_seq OWNED BY cargo_tracker_e2e.confirmation_code.id;


--
-- Name: currency_rate; Type: TABLE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE TABLE cargo_tracker_e2e.currency_rate (
    id bigint NOT NULL,
    from_currency character varying(3) NOT NULL,
    to_currency character varying(3) NOT NULL,
    rate bigint NOT NULL,
    valid_from timestamp with time zone NOT NULL,
    valid_to timestamp with time zone NOT NULL,
    version integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT currency_rate_check CHECK (((from_currency)::text <> (to_currency)::text)),
    CONSTRAINT currency_rate_check1 CHECK ((valid_from < valid_to)),
    CONSTRAINT currency_rate_rate_check CHECK ((rate >= 0))
);


--
-- Name: currency_rate_id_seq; Type: SEQUENCE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE SEQUENCE cargo_tracker_e2e.currency_rate_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: currency_rate_id_seq; Type: SEQUENCE OWNED BY; Schema: cargo_tracker_e2e; Owner: -
--

ALTER SEQUENCE cargo_tracker_e2e.currency_rate_id_seq OWNED BY cargo_tracker_e2e.currency_rate.id;


--
-- Name: customs_declaration; Type: TABLE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE TABLE cargo_tracker_e2e.customs_declaration (
    id bigint NOT NULL,
    booking_id character varying(20) NOT NULL,
    hs_code character varying(10) NOT NULL,
    broker_name character varying(100) NOT NULL,
    declaration_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    version bigint DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT customs_declaration_broker_name_check CHECK (((char_length((broker_name)::text) >= 1) AND (char_length((broker_name)::text) <= 100))),
    CONSTRAINT customs_declaration_declaration_status_check CHECK (((declaration_status)::text = ANY ((ARRAY['PENDING'::character varying, 'CLEARED'::character varying, 'HELD'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT customs_declaration_hs_code_check CHECK ((((char_length((hs_code)::text) >= 6) AND (char_length((hs_code)::text) <= 10)) AND ((hs_code)::text ~ '^[0-9]+$'::text)))
);


--
-- Name: TABLE customs_declaration; Type: COMMENT; Schema: cargo_tracker_e2e; Owner: -
--

COMMENT ON TABLE cargo_tracker_e2e.customs_declaration IS '通関申告 (US27 IT3)。1 予約 = 0..1 通関情報。Handling Context 実装時に handling_activity_id 等を ALTER で追加予定。';


--
-- Name: COLUMN customs_declaration.hs_code; Type: COMMENT; Schema: cargo_tracker_e2e; Owner: -
--

COMMENT ON COLUMN cargo_tracker_e2e.customs_declaration.hs_code IS 'Harmonized System code (6-10 桁の数字)。';


--
-- Name: COLUMN customs_declaration.declaration_status; Type: COMMENT; Schema: cargo_tracker_e2e; Owner: -
--

COMMENT ON COLUMN cargo_tracker_e2e.customs_declaration.declaration_status IS 'PENDING / CLEARED / HELD / REJECTED。状態遷移ルールは現状アプリ側で強制しない。';


--
-- Name: customs_declaration_id_seq; Type: SEQUENCE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE SEQUENCE cargo_tracker_e2e.customs_declaration_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customs_declaration_id_seq; Type: SEQUENCE OWNED BY; Schema: cargo_tracker_e2e; Owner: -
--

ALTER SEQUENCE cargo_tracker_e2e.customs_declaration_id_seq OWNED BY cargo_tracker_e2e.customs_declaration.id;


--
-- Name: estimate; Type: TABLE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE TABLE cargo_tracker_e2e.estimate (
    id bigint NOT NULL,
    estimate_id uuid NOT NULL,
    shipper_id bigint NOT NULL,
    origin_unlocode character varying(5) NOT NULL,
    destination_unlocode character varying(5) NOT NULL,
    deadline timestamp with time zone NOT NULL,
    cargo_type character varying(20) NOT NULL,
    weight_kg numeric NOT NULL,
    estimate_status character varying(20) NOT NULL,
    version bigint DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT estimate_cargo_type_check CHECK (((cargo_type)::text = ANY ((ARRAY['GENERAL'::character varying, 'HAZARDOUS'::character varying, 'REFRIGERATED'::character varying])::text[]))),
    CONSTRAINT estimate_status_check CHECK (((estimate_status)::text = ANY ((ARRAY['Created'::character varying, 'Expired'::character varying])::text[]))),
    CONSTRAINT estimate_weight_positive CHECK ((weight_kg > (0)::numeric))
);


--
-- Name: estimate_id_seq; Type: SEQUENCE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE SEQUENCE cargo_tracker_e2e.estimate_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: estimate_id_seq; Type: SEQUENCE OWNED BY; Schema: cargo_tracker_e2e; Owner: -
--

ALTER SEQUENCE cargo_tracker_e2e.estimate_id_seq OWNED BY cargo_tracker_e2e.estimate.id;


--
-- Name: handling_activity; Type: TABLE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE TABLE cargo_tracker_e2e.handling_activity (
    id bigint NOT NULL,
    booking_id character varying(20) NOT NULL,
    event_type character varying(30) NOT NULL,
    event_completion_time timestamp with time zone NOT NULL,
    location_unlocode character varying(5) NOT NULL,
    voyage_number character varying(20),
    operator_name character varying(200) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_voyage_number_for_load_unload CHECK ((((event_type)::text <> ALL ((ARRAY['LOAD'::character varying, 'UNLOAD'::character varying])::text[])) OR (voyage_number IS NOT NULL))),
    CONSTRAINT handling_activity_event_type_check CHECK (((event_type)::text = ANY ((ARRAY['RECEIVE'::character varying, 'LOAD'::character varying, 'UNLOAD'::character varying, 'CUSTOMS'::character varying, 'CLAIM'::character varying])::text[])))
);


--
-- Name: handling_activity_id_seq; Type: SEQUENCE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE SEQUENCE cargo_tracker_e2e.handling_activity_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: handling_activity_id_seq; Type: SEQUENCE OWNED BY; Schema: cargo_tracker_e2e; Owner: -
--

ALTER SEQUENCE cargo_tracker_e2e.handling_activity_id_seq OWNED BY cargo_tracker_e2e.handling_activity.id;


--
-- Name: itinerary; Type: TABLE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE TABLE cargo_tracker_e2e.itinerary (
    id bigint NOT NULL,
    itinerary_id uuid NOT NULL,
    booking_id character varying(20) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: itinerary_id_seq; Type: SEQUENCE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE SEQUENCE cargo_tracker_e2e.itinerary_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: itinerary_id_seq; Type: SEQUENCE OWNED BY; Schema: cargo_tracker_e2e; Owner: -
--

ALTER SEQUENCE cargo_tracker_e2e.itinerary_id_seq OWNED BY cargo_tracker_e2e.itinerary.id;


--
-- Name: leg; Type: TABLE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE TABLE cargo_tracker_e2e.leg (
    id bigint NOT NULL,
    itinerary_id uuid NOT NULL,
    seq_number integer NOT NULL,
    load_location_unlocode character varying(5) NOT NULL,
    unload_location_unlocode character varying(5) NOT NULL,
    load_time timestamp with time zone NOT NULL,
    unload_time timestamp with time zone NOT NULL,
    voyage_number character varying(20) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT leg_check CHECK ((load_time < unload_time)),
    CONSTRAINT leg_seq_number_check CHECK ((seq_number >= 1))
);


--
-- Name: leg_id_seq; Type: SEQUENCE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE SEQUENCE cargo_tracker_e2e.leg_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: leg_id_seq; Type: SEQUENCE OWNED BY; Schema: cargo_tracker_e2e; Owner: -
--

ALTER SEQUENCE cargo_tracker_e2e.leg_id_seq OWNED BY cargo_tracker_e2e.leg.id;


--
-- Name: location; Type: TABLE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE TABLE cargo_tracker_e2e.location (
    unlocode character varying(5) NOT NULL,
    name character varying(255) NOT NULL,
    country character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT location_unlocode_format CHECK (((unlocode)::text ~ '^[A-Z]{2}[A-Z0-9]{3}$'::text))
);


--
-- Name: notification; Type: TABLE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE TABLE cargo_tracker_e2e.notification (
    id bigint NOT NULL,
    booking_id character varying(20) NOT NULL,
    channel character varying(20) NOT NULL,
    subject character varying(200) NOT NULL,
    body text NOT NULL,
    status character varying(20) DEFAULT 'Pending'::character varying NOT NULL,
    created_at timestamp with time zone NOT NULL,
    sent_at timestamp with time zone,
    failure_reason text,
    version integer DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT notification_channel_check CHECK (((channel)::text = ANY ((ARRAY['Log'::character varying, 'EmailMock'::character varying, 'PrintableHtml'::character varying])::text[]))),
    CONSTRAINT notification_status_check CHECK (((status)::text = ANY ((ARRAY['Pending'::character varying, 'Sent'::character varying, 'Failed'::character varying])::text[])))
);


--
-- Name: notification_id_seq; Type: SEQUENCE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE SEQUENCE cargo_tracker_e2e.notification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_id_seq; Type: SEQUENCE OWNED BY; Schema: cargo_tracker_e2e; Owner: -
--

ALTER SEQUENCE cargo_tracker_e2e.notification_id_seq OWNED BY cargo_tracker_e2e.notification.id;


--
-- Name: pricing_rule; Type: TABLE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE TABLE cargo_tracker_e2e.pricing_rule (
    id bigint NOT NULL,
    currency character varying(3) NOT NULL,
    base_rate bigint NOT NULL,
    distance_rate_per_km bigint NOT NULL,
    weight_rate_per_kg bigint NOT NULL,
    version integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT pricing_rule_base_rate_check CHECK ((base_rate >= 0)),
    CONSTRAINT pricing_rule_distance_rate_per_km_check CHECK ((distance_rate_per_km >= 0)),
    CONSTRAINT pricing_rule_weight_rate_per_kg_check CHECK ((weight_rate_per_kg >= 0))
);


--
-- Name: pricing_rule_id_seq; Type: SEQUENCE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE SEQUENCE cargo_tracker_e2e.pricing_rule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pricing_rule_id_seq; Type: SEQUENCE OWNED BY; Schema: cargo_tracker_e2e; Owner: -
--

ALTER SEQUENCE cargo_tracker_e2e.pricing_rule_id_seq OWNED BY cargo_tracker_e2e.pricing_rule.id;


--
-- Name: route_candidate; Type: TABLE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE TABLE cargo_tracker_e2e.route_candidate (
    id bigint NOT NULL,
    estimate_id bigint NOT NULL,
    rank integer NOT NULL,
    transit_days integer NOT NULL,
    estimated_cost numeric NOT NULL,
    voyage_numbers text NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT route_candidate_cost_nonneg CHECK ((estimated_cost >= (0)::numeric)),
    CONSTRAINT route_candidate_rank_nonneg CHECK ((rank >= 0)),
    CONSTRAINT route_candidate_transit_positive CHECK ((transit_days >= 1))
);


--
-- Name: route_candidate_id_seq; Type: SEQUENCE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE SEQUENCE cargo_tracker_e2e.route_candidate_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: route_candidate_id_seq; Type: SEQUENCE OWNED BY; Schema: cargo_tracker_e2e; Owner: -
--

ALTER SEQUENCE cargo_tracker_e2e.route_candidate_id_seq OWNED BY cargo_tracker_e2e.route_candidate.id;


--
-- Name: schema_migrations; Type: TABLE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE TABLE cargo_tracker_e2e.schema_migrations (
    version character varying NOT NULL
);


--
-- Name: session; Type: TABLE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE TABLE cargo_tracker_e2e.session (
    id bigint NOT NULL,
    session_token character varying(64) NOT NULL,
    user_id bigint NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    last_used_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: session_id_seq; Type: SEQUENCE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE SEQUENCE cargo_tracker_e2e.session_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: session_id_seq; Type: SEQUENCE OWNED BY; Schema: cargo_tracker_e2e; Owner: -
--

ALTER SEQUENCE cargo_tracker_e2e.session_id_seq OWNED BY cargo_tracker_e2e.session.id;


--
-- Name: shipper; Type: TABLE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE TABLE cargo_tracker_e2e.shipper (
    id bigint NOT NULL,
    shipper_id character varying(20) NOT NULL,
    name character varying(255) NOT NULL,
    email character varying(255) NOT NULL,
    address character varying(500) NOT NULL,
    shipper_kind character varying(20) NOT NULL,
    corporate_number character varying(13),
    contract_rank character varying(20),
    version bigint DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT shipper_contract_rank_check CHECK (((contract_rank IS NULL) OR ((contract_rank)::text = ANY ((ARRAY['Bronze'::character varying, 'Silver'::character varying, 'Gold'::character varying])::text[])))),
    CONSTRAINT shipper_corporate_fields CHECK (((((shipper_kind)::text = 'Individual'::text) AND (corporate_number IS NULL) AND (contract_rank IS NULL)) OR (((shipper_kind)::text = 'Corporate'::text) AND (corporate_number IS NOT NULL) AND (contract_rank IS NOT NULL)))),
    CONSTRAINT shipper_corporate_number_format CHECK (((corporate_number IS NULL) OR ((corporate_number)::text ~ '^[0-9]{13}$'::text))),
    CONSTRAINT shipper_kind_check CHECK (((shipper_kind)::text = ANY ((ARRAY['Individual'::character varying, 'Corporate'::character varying])::text[]))),
    CONSTRAINT shipper_shipper_id_format CHECK (((shipper_id)::text ~ '^SHP-[A-Z0-9]{6}$'::text))
);


--
-- Name: shipper_id_seq; Type: SEQUENCE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE SEQUENCE cargo_tracker_e2e.shipper_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: shipper_id_seq; Type: SEQUENCE OWNED BY; Schema: cargo_tracker_e2e; Owner: -
--

ALTER SEQUENCE cargo_tracker_e2e.shipper_id_seq OWNED BY cargo_tracker_e2e.shipper.id;


--
-- Name: tracking_activity; Type: TABLE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE TABLE cargo_tracker_e2e.tracking_activity (
    id bigint NOT NULL,
    tracking_number character varying(20) NOT NULL,
    booking_id character varying(20) NOT NULL,
    transport_status character varying(30) DEFAULT 'TsNotReceived'::character varying NOT NULL,
    version integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT tracking_activity_transport_status_check CHECK (((transport_status)::text = ANY ((ARRAY['TsNotReceived'::character varying, 'TsReceived'::character varying, 'TsLoaded'::character varying, 'TsOnboardCarrier'::character varying, 'TsUnloaded'::character varying, 'TsAwaitingClaim'::character varying, 'TsClaimed'::character varying, 'TsInException'::character varying, 'TsUnknown'::character varying])::text[])))
);


--
-- Name: tracking_activity_id_seq; Type: SEQUENCE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE SEQUENCE cargo_tracker_e2e.tracking_activity_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tracking_activity_id_seq; Type: SEQUENCE OWNED BY; Schema: cargo_tracker_e2e; Owner: -
--

ALTER SEQUENCE cargo_tracker_e2e.tracking_activity_id_seq OWNED BY cargo_tracker_e2e.tracking_activity.id;


--
-- Name: user_roles; Type: TABLE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE TABLE cargo_tracker_e2e.user_roles (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    role character varying(20) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT user_roles_role_check CHECK (((role)::text = ANY ((ARRAY['Shipper'::character varying, 'Consignee'::character varying, 'Sales'::character varying, 'Router'::character varying, 'Tracker'::character varying, 'Handler'::character varying, 'Accountant'::character varying, 'MasterAdmin'::character varying])::text[])))
);


--
-- Name: user_roles_id_seq; Type: SEQUENCE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE SEQUENCE cargo_tracker_e2e.user_roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_roles_id_seq; Type: SEQUENCE OWNED BY; Schema: cargo_tracker_e2e; Owner: -
--

ALTER SEQUENCE cargo_tracker_e2e.user_roles_id_seq OWNED BY cargo_tracker_e2e.user_roles.id;


--
-- Name: users; Type: TABLE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE TABLE cargo_tracker_e2e.users (
    id bigint NOT NULL,
    user_id character varying(50) NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(60) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE SEQUENCE cargo_tracker_e2e.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: cargo_tracker_e2e; Owner: -
--

ALTER SEQUENCE cargo_tracker_e2e.users_id_seq OWNED BY cargo_tracker_e2e.users.id;


--
-- Name: voyage; Type: TABLE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE TABLE cargo_tracker_e2e.voyage (
    id bigint NOT NULL,
    voyage_number character varying(20) NOT NULL,
    version bigint DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: voyage_id_seq; Type: SEQUENCE; Schema: cargo_tracker_e2e; Owner: -
--

CREATE SEQUENCE cargo_tracker_e2e.voyage_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: voyage_id_seq; Type: SEQUENCE OWNED BY; Schema: cargo_tracker_e2e; Owner: -
--

ALTER SEQUENCE cargo_tracker_e2e.voyage_id_seq OWNED BY cargo_tracker_e2e.voyage.id;


--
-- Name: cargo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cargo (
    id bigint NOT NULL,
    booking_id character varying(20) NOT NULL,
    shipper_id bigint NOT NULL,
    origin_unlocode character varying(5) NOT NULL,
    destination_unlocode character varying(5) NOT NULL,
    deadline timestamp with time zone NOT NULL,
    booking_status character varying(20) NOT NULL,
    version bigint DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    cargo_type character varying(20) DEFAULT 'GENERAL'::character varying NOT NULL,
    hazardous_class character varying(10),
    un_number character varying(10),
    proper_shipping_name text,
    min_temperature numeric,
    max_temperature numeric,
    temperature_unit character varying(1),
    itinerary_id uuid,
    cancellation_rate numeric(4,3),
    cancellation_tier character varying(10),
    cancellation_calculated_at timestamp with time zone,
    confirmed_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    CONSTRAINT cargo_booking_id_format CHECK (((booking_id)::text ~ '^BK-[A-Z0-9]{6}$'::text)),
    CONSTRAINT cargo_booking_status_check CHECK (((booking_status)::text = ANY ((ARRAY['Draft'::character varying, 'Submitted'::character varying, 'RouteProposed'::character varying, 'Confirmed'::character varying, 'Closed'::character varying])::text[]))),
    CONSTRAINT cargo_cancellation_rate_check CHECK (((cancellation_rate IS NULL) OR ((cancellation_rate >= 0.000) AND (cancellation_rate <= 1.000)))),
    CONSTRAINT cargo_cancellation_tier_check CHECK (((cancellation_tier IS NULL) OR ((cancellation_tier)::text = ANY ((ARRAY['FREE'::character varying, 'PARTIAL'::character varying, 'FULL'::character varying])::text[])))),
    CONSTRAINT cargo_hazardous_fields CHECK (((((cargo_type)::text = 'HAZARDOUS'::text) AND (hazardous_class IS NOT NULL) AND (un_number IS NOT NULL) AND (proper_shipping_name IS NOT NULL)) OR ((cargo_type)::text <> 'HAZARDOUS'::text))),
    CONSTRAINT cargo_refrigerated_fields CHECK (((((cargo_type)::text = 'REFRIGERATED'::text) AND (min_temperature IS NOT NULL) AND (max_temperature IS NOT NULL) AND (temperature_unit IS NOT NULL) AND ((temperature_unit)::text = ANY ((ARRAY['C'::character varying, 'F'::character varying])::text[]))) OR ((cargo_type)::text <> 'REFRIGERATED'::text))),
    CONSTRAINT cargo_type_check CHECK (((cargo_type)::text = ANY ((ARRAY['GENERAL'::character varying, 'HAZARDOUS'::character varying, 'REFRIGERATED'::character varying])::text[])))
);


--
-- Name: cargo_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.cargo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cargo_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.cargo_id_seq OWNED BY public.cargo.id;


--
-- Name: carrier_movement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.carrier_movement (
    id bigint NOT NULL,
    voyage_id bigint NOT NULL,
    seq_number integer NOT NULL,
    departure_location_unlocode character varying(5) NOT NULL,
    arrival_location_unlocode character varying(5) NOT NULL,
    departure_time timestamp with time zone NOT NULL,
    arrival_time timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT carrier_movement_time_order CHECK ((departure_time < arrival_time))
);


--
-- Name: carrier_movement_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.carrier_movement_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: carrier_movement_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.carrier_movement_id_seq OWNED BY public.carrier_movement.id;


--
-- Name: confirmation_code; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.confirmation_code (
    id bigint NOT NULL,
    booking_id character varying(20) NOT NULL,
    issued_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    attempt_count integer DEFAULT 0 NOT NULL,
    version integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    code_hash character varying(72) NOT NULL,
    CONSTRAINT confirmation_code_attempt_count_check CHECK (((attempt_count >= 0) AND (attempt_count <= 5)))
);


--
-- Name: confirmation_code_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.confirmation_code_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: confirmation_code_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.confirmation_code_id_seq OWNED BY public.confirmation_code.id;


--
-- Name: currency_rate; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.currency_rate (
    id bigint NOT NULL,
    from_currency character varying(3) NOT NULL,
    to_currency character varying(3) NOT NULL,
    rate bigint NOT NULL,
    valid_from timestamp with time zone NOT NULL,
    valid_to timestamp with time zone NOT NULL,
    version integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT currency_rate_check CHECK (((from_currency)::text <> (to_currency)::text)),
    CONSTRAINT currency_rate_check1 CHECK ((valid_from < valid_to)),
    CONSTRAINT currency_rate_rate_check CHECK ((rate >= 0))
);


--
-- Name: currency_rate_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.currency_rate_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: currency_rate_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.currency_rate_id_seq OWNED BY public.currency_rate.id;


--
-- Name: customs_declaration; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customs_declaration (
    id bigint NOT NULL,
    booking_id character varying(20) NOT NULL,
    hs_code character varying(10) NOT NULL,
    broker_name character varying(100) NOT NULL,
    declaration_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    version bigint DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT customs_declaration_broker_name_check CHECK (((char_length((broker_name)::text) >= 1) AND (char_length((broker_name)::text) <= 100))),
    CONSTRAINT customs_declaration_declaration_status_check CHECK (((declaration_status)::text = ANY ((ARRAY['PENDING'::character varying, 'CLEARED'::character varying, 'HELD'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT customs_declaration_hs_code_check CHECK ((((char_length((hs_code)::text) >= 6) AND (char_length((hs_code)::text) <= 10)) AND ((hs_code)::text ~ '^[0-9]+$'::text)))
);


--
-- Name: TABLE customs_declaration; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.customs_declaration IS '通関申告 (US27 IT3)。1 予約 = 0..1 通関情報。Handling Context 実装時に handling_activity_id 等を ALTER で追加予定。';


--
-- Name: COLUMN customs_declaration.hs_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customs_declaration.hs_code IS 'Harmonized System code (6-10 桁の数字)。';


--
-- Name: COLUMN customs_declaration.declaration_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customs_declaration.declaration_status IS 'PENDING / CLEARED / HELD / REJECTED。状態遷移ルールは現状アプリ側で強制しない。';


--
-- Name: customs_declaration_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customs_declaration_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customs_declaration_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.customs_declaration_id_seq OWNED BY public.customs_declaration.id;


--
-- Name: estimate; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.estimate (
    id bigint NOT NULL,
    estimate_id uuid NOT NULL,
    shipper_id bigint NOT NULL,
    origin_unlocode character varying(5) NOT NULL,
    destination_unlocode character varying(5) NOT NULL,
    deadline timestamp with time zone NOT NULL,
    cargo_type character varying(20) NOT NULL,
    weight_kg numeric NOT NULL,
    estimate_status character varying(20) NOT NULL,
    version bigint DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT estimate_cargo_type_check CHECK (((cargo_type)::text = ANY ((ARRAY['GENERAL'::character varying, 'HAZARDOUS'::character varying, 'REFRIGERATED'::character varying])::text[]))),
    CONSTRAINT estimate_status_check CHECK (((estimate_status)::text = ANY ((ARRAY['Created'::character varying, 'Expired'::character varying])::text[]))),
    CONSTRAINT estimate_weight_positive CHECK ((weight_kg > (0)::numeric))
);


--
-- Name: estimate_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.estimate_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: estimate_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.estimate_id_seq OWNED BY public.estimate.id;


--
-- Name: exception_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exception_record (
    id bigint NOT NULL,
    exception_id character varying(64) NOT NULL,
    tracking_number character varying(20) NOT NULL,
    exception_type character varying(20) NOT NULL,
    severity character varying(10) NOT NULL,
    detail_json jsonb NOT NULL,
    reporter_user_id character varying(64) NOT NULL,
    reporter_role character varying(20) NOT NULL,
    reported_at timestamp with time zone NOT NULL,
    resolved_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT er_reporter_role_nonempty CHECK ((length(TRIM(BOTH FROM reporter_role)) > 0)),
    CONSTRAINT er_reporter_user_id_nonempty CHECK ((length(TRIM(BOTH FROM reporter_user_id)) > 0)),
    CONSTRAINT er_tracking_number_format CHECK (((tracking_number)::text ~ '^[A-Z0-9]{8}$'::text)),
    CONSTRAINT exception_record_exception_type_check CHECK (((exception_type)::text = ANY ((ARRAY['DELAY'::character varying, 'DAMAGE'::character varying, 'LOSS'::character varying])::text[]))),
    CONSTRAINT exception_record_severity_check CHECK (((severity)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying])::text[])))
);


--
-- Name: exception_record_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.exception_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: exception_record_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.exception_record_id_seq OWNED BY public.exception_record.id;


--
-- Name: handling_activity; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.handling_activity (
    id bigint NOT NULL,
    booking_id character varying(20) NOT NULL,
    event_type character varying(30) NOT NULL,
    event_completion_time timestamp with time zone NOT NULL,
    location_unlocode character varying(5) NOT NULL,
    voyage_number character varying(20),
    operator_name character varying(200) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_voyage_number_for_load_unload CHECK ((((event_type)::text <> ALL ((ARRAY['LOAD'::character varying, 'UNLOAD'::character varying])::text[])) OR (voyage_number IS NOT NULL))),
    CONSTRAINT handling_activity_event_type_check CHECK (((event_type)::text = ANY ((ARRAY['RECEIVE'::character varying, 'LOAD'::character varying, 'UNLOAD'::character varying, 'CUSTOMS'::character varying, 'CLAIM'::character varying])::text[])))
);


--
-- Name: handling_activity_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.handling_activity_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: handling_activity_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.handling_activity_id_seq OWNED BY public.handling_activity.id;


--
-- Name: invoice; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoice (
    id bigint NOT NULL,
    invoice_number character varying(30) NOT NULL,
    booking_id character varying(20) NOT NULL,
    shipper_id character varying(20) NOT NULL,
    base_amount_value bigint NOT NULL,
    base_amount_currency character varying(3) NOT NULL,
    discount_rate numeric(5,4) DEFAULT 0 NOT NULL,
    final_amount_value bigint NOT NULL,
    final_amount_currency character varying(3) NOT NULL,
    tax_rate numeric(5,4) DEFAULT 0.1000 NOT NULL,
    tax_amount bigint DEFAULT 0 NOT NULL,
    payment_status character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    issued_at timestamp with time zone,
    due_date date,
    paid_at timestamp with time zone,
    payment_reference character varying(64),
    version integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT inv_amounts_nonnegative CHECK (((base_amount_value >= 0) AND (final_amount_value >= 0) AND (tax_amount >= 0))),
    CONSTRAINT invoice_payment_status_check CHECK (((payment_status)::text = ANY ((ARRAY['PENDING'::character varying, 'CONFIRMED'::character varying, 'OVERDUE'::character varying, 'REFUNDED'::character varying])::text[])))
);


--
-- Name: invoice_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.invoice_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: invoice_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.invoice_id_seq OWNED BY public.invoice.id;


--
-- Name: invoice_line_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoice_line_item (
    id bigint NOT NULL,
    invoice_id bigint NOT NULL,
    description character varying(200) NOT NULL,
    amount_value bigint NOT NULL,
    amount_currency character varying(3) NOT NULL,
    seq_number integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: invoice_line_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.invoice_line_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: invoice_line_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.invoice_line_item_id_seq OWNED BY public.invoice_line_item.id;


--
-- Name: itinerary; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.itinerary (
    id bigint NOT NULL,
    itinerary_id uuid NOT NULL,
    booking_id character varying(20) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: itinerary_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.itinerary_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: itinerary_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.itinerary_id_seq OWNED BY public.itinerary.id;


--
-- Name: leg; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.leg (
    id bigint NOT NULL,
    itinerary_id uuid NOT NULL,
    seq_number integer NOT NULL,
    load_location_unlocode character varying(5) NOT NULL,
    unload_location_unlocode character varying(5) NOT NULL,
    load_time timestamp with time zone NOT NULL,
    unload_time timestamp with time zone NOT NULL,
    voyage_number character varying(20) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT leg_check CHECK ((load_time < unload_time)),
    CONSTRAINT leg_seq_number_check CHECK ((seq_number >= 1))
);


--
-- Name: leg_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.leg_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: leg_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.leg_id_seq OWNED BY public.leg.id;


--
-- Name: location; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.location (
    unlocode character varying(5) NOT NULL,
    name character varying(255) NOT NULL,
    country character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT location_unlocode_format CHECK (((unlocode)::text ~ '^[A-Z]{2}[A-Z0-9]{3}$'::text))
);


--
-- Name: notification; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification (
    id bigint NOT NULL,
    booking_id character varying(20) NOT NULL,
    channel character varying(20) NOT NULL,
    subject character varying(200) NOT NULL,
    body text NOT NULL,
    status character varying(20) DEFAULT 'Pending'::character varying NOT NULL,
    created_at timestamp with time zone NOT NULL,
    sent_at timestamp with time zone,
    failure_reason text,
    version integer DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    notification_id uuid DEFAULT gen_random_uuid() NOT NULL,
    CONSTRAINT notification_channel_check CHECK (((channel)::text = ANY ((ARRAY['Log'::character varying, 'EmailMock'::character varying, 'PrintableHtml'::character varying])::text[]))),
    CONSTRAINT notification_status_check CHECK (((status)::text = ANY ((ARRAY['Pending'::character varying, 'Sent'::character varying, 'Failed'::character varying])::text[])))
);


--
-- Name: notification_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notification_id_seq OWNED BY public.notification.id;


--
-- Name: pricing_rule; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pricing_rule (
    id bigint NOT NULL,
    currency character varying(3) NOT NULL,
    base_rate bigint NOT NULL,
    distance_rate_per_km bigint NOT NULL,
    weight_rate_per_kg bigint NOT NULL,
    version integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT pricing_rule_base_rate_check CHECK ((base_rate >= 0)),
    CONSTRAINT pricing_rule_distance_rate_per_km_check CHECK ((distance_rate_per_km >= 0)),
    CONSTRAINT pricing_rule_weight_rate_per_kg_check CHECK ((weight_rate_per_kg >= 0))
);


--
-- Name: pricing_rule_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pricing_rule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pricing_rule_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pricing_rule_id_seq OWNED BY public.pricing_rule.id;


--
-- Name: route_candidate; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.route_candidate (
    id bigint NOT NULL,
    estimate_id bigint NOT NULL,
    rank integer NOT NULL,
    transit_days integer NOT NULL,
    estimated_cost numeric NOT NULL,
    voyage_numbers text NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT route_candidate_cost_nonneg CHECK ((estimated_cost >= (0)::numeric)),
    CONSTRAINT route_candidate_rank_nonneg CHECK ((rank >= 0)),
    CONSTRAINT route_candidate_transit_positive CHECK ((transit_days >= 1))
);


--
-- Name: route_candidate_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.route_candidate_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: route_candidate_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.route_candidate_id_seq OWNED BY public.route_candidate.id;


--
-- Name: schema_migrations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.schema_migrations (
    version character varying NOT NULL
);


--
-- Name: session; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.session (
    id bigint NOT NULL,
    session_token character varying(64) NOT NULL,
    user_id bigint NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    last_used_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: session_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.session_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: session_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.session_id_seq OWNED BY public.session.id;


--
-- Name: shipper; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.shipper (
    id bigint NOT NULL,
    shipper_id character varying(20) NOT NULL,
    name character varying(255) NOT NULL,
    email character varying(255) NOT NULL,
    address character varying(500) NOT NULL,
    shipper_kind character varying(20) NOT NULL,
    corporate_number character varying(13),
    contract_rank character varying(20),
    version bigint DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT shipper_contract_rank_check CHECK (((contract_rank IS NULL) OR ((contract_rank)::text = ANY ((ARRAY['Bronze'::character varying, 'Silver'::character varying, 'Gold'::character varying])::text[])))),
    CONSTRAINT shipper_corporate_fields CHECK (((((shipper_kind)::text = 'Individual'::text) AND (corporate_number IS NULL) AND (contract_rank IS NULL)) OR (((shipper_kind)::text = 'Corporate'::text) AND (corporate_number IS NOT NULL) AND (contract_rank IS NOT NULL)))),
    CONSTRAINT shipper_corporate_number_format CHECK (((corporate_number IS NULL) OR ((corporate_number)::text ~ '^[0-9]{13}$'::text))),
    CONSTRAINT shipper_kind_check CHECK (((shipper_kind)::text = ANY ((ARRAY['Individual'::character varying, 'Corporate'::character varying])::text[]))),
    CONSTRAINT shipper_shipper_id_format CHECK (((shipper_id)::text ~ '^SHP-[A-Z0-9]{6}$'::text))
);


--
-- Name: shipper_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.shipper_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: shipper_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.shipper_id_seq OWNED BY public.shipper.id;


--
-- Name: tracking_activity; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tracking_activity (
    id bigint NOT NULL,
    tracking_number character varying(20) NOT NULL,
    booking_id character varying(20) NOT NULL,
    transport_status character varying(30) DEFAULT 'TsNotReceived'::character varying NOT NULL,
    version integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT tracking_activity_transport_status_check CHECK (((transport_status)::text = ANY ((ARRAY['TsNotReceived'::character varying, 'TsReceived'::character varying, 'TsLoaded'::character varying, 'TsOnboardCarrier'::character varying, 'TsUnloaded'::character varying, 'TsAwaitingClaim'::character varying, 'TsClaimed'::character varying, 'TsInException'::character varying, 'TsUnknown'::character varying])::text[])))
);


--
-- Name: tracking_activity_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tracking_activity_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tracking_activity_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tracking_activity_id_seq OWNED BY public.tracking_activity.id;


--
-- Name: tracking_state_audit; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tracking_state_audit (
    id bigint NOT NULL,
    tracking_number character varying(20) NOT NULL,
    previous_status character varying(30) NOT NULL,
    new_status character varying(30) NOT NULL,
    reason text NOT NULL,
    changed_by character varying(64) NOT NULL,
    changed_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT tracking_state_audit_reason_check CHECK ((length(TRIM(BOTH FROM reason)) > 0)),
    CONSTRAINT tsa_state_change CHECK (((previous_status)::text <> (new_status)::text)),
    CONSTRAINT tsa_tracking_number_format CHECK (((tracking_number)::text ~ '^[A-Z0-9]{8}$'::text))
);


--
-- Name: tracking_state_audit_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tracking_state_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tracking_state_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tracking_state_audit_id_seq OWNED BY public.tracking_state_audit.id;


--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_roles (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    role character varying(20) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT user_roles_role_check CHECK (((role)::text = ANY ((ARRAY['Shipper'::character varying, 'Consignee'::character varying, 'Sales'::character varying, 'Router'::character varying, 'Tracker'::character varying, 'Handler'::character varying, 'Accountant'::character varying, 'MasterAdmin'::character varying])::text[])))
);


--
-- Name: user_roles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_roles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_roles_id_seq OWNED BY public.user_roles.id;


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    user_id character varying(50) NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(60) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: voyage; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.voyage (
    id bigint NOT NULL,
    voyage_number character varying(20) NOT NULL,
    version bigint DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: voyage_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.voyage_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: voyage_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.voyage_id_seq OWNED BY public.voyage.id;


--
-- Name: cargo id; Type: DEFAULT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.cargo ALTER COLUMN id SET DEFAULT nextval('cargo_tracker_e2e.cargo_id_seq'::regclass);


--
-- Name: carrier_movement id; Type: DEFAULT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.carrier_movement ALTER COLUMN id SET DEFAULT nextval('cargo_tracker_e2e.carrier_movement_id_seq'::regclass);


--
-- Name: confirmation_code id; Type: DEFAULT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.confirmation_code ALTER COLUMN id SET DEFAULT nextval('cargo_tracker_e2e.confirmation_code_id_seq'::regclass);


--
-- Name: currency_rate id; Type: DEFAULT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.currency_rate ALTER COLUMN id SET DEFAULT nextval('cargo_tracker_e2e.currency_rate_id_seq'::regclass);


--
-- Name: customs_declaration id; Type: DEFAULT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.customs_declaration ALTER COLUMN id SET DEFAULT nextval('cargo_tracker_e2e.customs_declaration_id_seq'::regclass);


--
-- Name: estimate id; Type: DEFAULT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.estimate ALTER COLUMN id SET DEFAULT nextval('cargo_tracker_e2e.estimate_id_seq'::regclass);


--
-- Name: handling_activity id; Type: DEFAULT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.handling_activity ALTER COLUMN id SET DEFAULT nextval('cargo_tracker_e2e.handling_activity_id_seq'::regclass);


--
-- Name: itinerary id; Type: DEFAULT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.itinerary ALTER COLUMN id SET DEFAULT nextval('cargo_tracker_e2e.itinerary_id_seq'::regclass);


--
-- Name: leg id; Type: DEFAULT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.leg ALTER COLUMN id SET DEFAULT nextval('cargo_tracker_e2e.leg_id_seq'::regclass);


--
-- Name: notification id; Type: DEFAULT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.notification ALTER COLUMN id SET DEFAULT nextval('cargo_tracker_e2e.notification_id_seq'::regclass);


--
-- Name: pricing_rule id; Type: DEFAULT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.pricing_rule ALTER COLUMN id SET DEFAULT nextval('cargo_tracker_e2e.pricing_rule_id_seq'::regclass);


--
-- Name: route_candidate id; Type: DEFAULT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.route_candidate ALTER COLUMN id SET DEFAULT nextval('cargo_tracker_e2e.route_candidate_id_seq'::regclass);


--
-- Name: session id; Type: DEFAULT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.session ALTER COLUMN id SET DEFAULT nextval('cargo_tracker_e2e.session_id_seq'::regclass);


--
-- Name: shipper id; Type: DEFAULT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.shipper ALTER COLUMN id SET DEFAULT nextval('cargo_tracker_e2e.shipper_id_seq'::regclass);


--
-- Name: tracking_activity id; Type: DEFAULT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.tracking_activity ALTER COLUMN id SET DEFAULT nextval('cargo_tracker_e2e.tracking_activity_id_seq'::regclass);


--
-- Name: user_roles id; Type: DEFAULT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.user_roles ALTER COLUMN id SET DEFAULT nextval('cargo_tracker_e2e.user_roles_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.users ALTER COLUMN id SET DEFAULT nextval('cargo_tracker_e2e.users_id_seq'::regclass);


--
-- Name: voyage id; Type: DEFAULT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.voyage ALTER COLUMN id SET DEFAULT nextval('cargo_tracker_e2e.voyage_id_seq'::regclass);


--
-- Name: cargo id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cargo ALTER COLUMN id SET DEFAULT nextval('public.cargo_id_seq'::regclass);


--
-- Name: carrier_movement id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carrier_movement ALTER COLUMN id SET DEFAULT nextval('public.carrier_movement_id_seq'::regclass);


--
-- Name: confirmation_code id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.confirmation_code ALTER COLUMN id SET DEFAULT nextval('public.confirmation_code_id_seq'::regclass);


--
-- Name: currency_rate id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.currency_rate ALTER COLUMN id SET DEFAULT nextval('public.currency_rate_id_seq'::regclass);


--
-- Name: customs_declaration id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customs_declaration ALTER COLUMN id SET DEFAULT nextval('public.customs_declaration_id_seq'::regclass);


--
-- Name: estimate id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.estimate ALTER COLUMN id SET DEFAULT nextval('public.estimate_id_seq'::regclass);


--
-- Name: exception_record id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exception_record ALTER COLUMN id SET DEFAULT nextval('public.exception_record_id_seq'::regclass);


--
-- Name: handling_activity id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.handling_activity ALTER COLUMN id SET DEFAULT nextval('public.handling_activity_id_seq'::regclass);


--
-- Name: invoice id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice ALTER COLUMN id SET DEFAULT nextval('public.invoice_id_seq'::regclass);


--
-- Name: invoice_line_item id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_line_item ALTER COLUMN id SET DEFAULT nextval('public.invoice_line_item_id_seq'::regclass);


--
-- Name: itinerary id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.itinerary ALTER COLUMN id SET DEFAULT nextval('public.itinerary_id_seq'::regclass);


--
-- Name: leg id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leg ALTER COLUMN id SET DEFAULT nextval('public.leg_id_seq'::regclass);


--
-- Name: notification id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification ALTER COLUMN id SET DEFAULT nextval('public.notification_id_seq'::regclass);


--
-- Name: pricing_rule id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pricing_rule ALTER COLUMN id SET DEFAULT nextval('public.pricing_rule_id_seq'::regclass);


--
-- Name: route_candidate id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.route_candidate ALTER COLUMN id SET DEFAULT nextval('public.route_candidate_id_seq'::regclass);


--
-- Name: session id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session ALTER COLUMN id SET DEFAULT nextval('public.session_id_seq'::regclass);


--
-- Name: shipper id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipper ALTER COLUMN id SET DEFAULT nextval('public.shipper_id_seq'::regclass);


--
-- Name: tracking_activity id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tracking_activity ALTER COLUMN id SET DEFAULT nextval('public.tracking_activity_id_seq'::regclass);


--
-- Name: tracking_state_audit id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tracking_state_audit ALTER COLUMN id SET DEFAULT nextval('public.tracking_state_audit_id_seq'::regclass);


--
-- Name: user_roles id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles ALTER COLUMN id SET DEFAULT nextval('public.user_roles_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Name: voyage id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.voyage ALTER COLUMN id SET DEFAULT nextval('public.voyage_id_seq'::regclass);


--
-- Name: cargo cargo_booking_id_key; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.cargo
    ADD CONSTRAINT cargo_booking_id_key UNIQUE (booking_id);


--
-- Name: cargo cargo_pkey; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.cargo
    ADD CONSTRAINT cargo_pkey PRIMARY KEY (id);


--
-- Name: carrier_movement carrier_movement_pkey; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.carrier_movement
    ADD CONSTRAINT carrier_movement_pkey PRIMARY KEY (id);


--
-- Name: carrier_movement carrier_movement_seq_unique; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.carrier_movement
    ADD CONSTRAINT carrier_movement_seq_unique UNIQUE (voyage_id, seq_number);


--
-- Name: confirmation_code confirmation_code_booking_id_key; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.confirmation_code
    ADD CONSTRAINT confirmation_code_booking_id_key UNIQUE (booking_id);


--
-- Name: confirmation_code confirmation_code_pkey; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.confirmation_code
    ADD CONSTRAINT confirmation_code_pkey PRIMARY KEY (id);


--
-- Name: currency_rate currency_rate_pkey; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.currency_rate
    ADD CONSTRAINT currency_rate_pkey PRIMARY KEY (id);


--
-- Name: customs_declaration customs_declaration_booking_id_key; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.customs_declaration
    ADD CONSTRAINT customs_declaration_booking_id_key UNIQUE (booking_id);


--
-- Name: customs_declaration customs_declaration_pkey; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.customs_declaration
    ADD CONSTRAINT customs_declaration_pkey PRIMARY KEY (id);


--
-- Name: estimate estimate_estimate_id_key; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.estimate
    ADD CONSTRAINT estimate_estimate_id_key UNIQUE (estimate_id);


--
-- Name: estimate estimate_pkey; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.estimate
    ADD CONSTRAINT estimate_pkey PRIMARY KEY (id);


--
-- Name: handling_activity handling_activity_pkey; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.handling_activity
    ADD CONSTRAINT handling_activity_pkey PRIMARY KEY (id);


--
-- Name: itinerary itinerary_itinerary_id_key; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.itinerary
    ADD CONSTRAINT itinerary_itinerary_id_key UNIQUE (itinerary_id);


--
-- Name: itinerary itinerary_pkey; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.itinerary
    ADD CONSTRAINT itinerary_pkey PRIMARY KEY (id);


--
-- Name: leg leg_itinerary_id_seq_number_key; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.leg
    ADD CONSTRAINT leg_itinerary_id_seq_number_key UNIQUE (itinerary_id, seq_number);


--
-- Name: leg leg_pkey; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.leg
    ADD CONSTRAINT leg_pkey PRIMARY KEY (id);


--
-- Name: location location_pkey; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.location
    ADD CONSTRAINT location_pkey PRIMARY KEY (unlocode);


--
-- Name: notification notification_pkey; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.notification
    ADD CONSTRAINT notification_pkey PRIMARY KEY (id);


--
-- Name: pricing_rule pricing_rule_currency_key; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.pricing_rule
    ADD CONSTRAINT pricing_rule_currency_key UNIQUE (currency);


--
-- Name: pricing_rule pricing_rule_pkey; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.pricing_rule
    ADD CONSTRAINT pricing_rule_pkey PRIMARY KEY (id);


--
-- Name: route_candidate route_candidate_pkey; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.route_candidate
    ADD CONSTRAINT route_candidate_pkey PRIMARY KEY (id);


--
-- Name: route_candidate route_candidate_rank_unique; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.route_candidate
    ADD CONSTRAINT route_candidate_rank_unique UNIQUE (estimate_id, rank);


--
-- Name: schema_migrations schema_migrations_pkey; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.schema_migrations
    ADD CONSTRAINT schema_migrations_pkey PRIMARY KEY (version);


--
-- Name: session session_pkey; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.session
    ADD CONSTRAINT session_pkey PRIMARY KEY (id);


--
-- Name: session session_session_token_key; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.session
    ADD CONSTRAINT session_session_token_key UNIQUE (session_token);


--
-- Name: shipper shipper_email_key; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.shipper
    ADD CONSTRAINT shipper_email_key UNIQUE (email);


--
-- Name: shipper shipper_pkey; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.shipper
    ADD CONSTRAINT shipper_pkey PRIMARY KEY (id);


--
-- Name: shipper shipper_shipper_id_key; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.shipper
    ADD CONSTRAINT shipper_shipper_id_key UNIQUE (shipper_id);


--
-- Name: tracking_activity tracking_activity_pkey; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.tracking_activity
    ADD CONSTRAINT tracking_activity_pkey PRIMARY KEY (id);


--
-- Name: tracking_activity tracking_activity_tracking_number_key; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.tracking_activity
    ADD CONSTRAINT tracking_activity_tracking_number_key UNIQUE (tracking_number);


--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (id);


--
-- Name: user_roles user_roles_user_id_role_key; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.user_roles
    ADD CONSTRAINT user_roles_user_id_role_key UNIQUE (user_id, role);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users users_user_id_key; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.users
    ADD CONSTRAINT users_user_id_key UNIQUE (user_id);


--
-- Name: voyage voyage_pkey; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.voyage
    ADD CONSTRAINT voyage_pkey PRIMARY KEY (id);


--
-- Name: voyage voyage_voyage_number_key; Type: CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.voyage
    ADD CONSTRAINT voyage_voyage_number_key UNIQUE (voyage_number);


--
-- Name: cargo cargo_booking_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cargo
    ADD CONSTRAINT cargo_booking_id_key UNIQUE (booking_id);


--
-- Name: cargo cargo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cargo
    ADD CONSTRAINT cargo_pkey PRIMARY KEY (id);


--
-- Name: carrier_movement carrier_movement_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carrier_movement
    ADD CONSTRAINT carrier_movement_pkey PRIMARY KEY (id);


--
-- Name: carrier_movement carrier_movement_seq_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carrier_movement
    ADD CONSTRAINT carrier_movement_seq_unique UNIQUE (voyage_id, seq_number);


--
-- Name: confirmation_code confirmation_code_booking_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.confirmation_code
    ADD CONSTRAINT confirmation_code_booking_id_key UNIQUE (booking_id);


--
-- Name: confirmation_code confirmation_code_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.confirmation_code
    ADD CONSTRAINT confirmation_code_pkey PRIMARY KEY (id);


--
-- Name: currency_rate currency_rate_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.currency_rate
    ADD CONSTRAINT currency_rate_pkey PRIMARY KEY (id);


--
-- Name: customs_declaration customs_declaration_booking_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customs_declaration
    ADD CONSTRAINT customs_declaration_booking_id_key UNIQUE (booking_id);


--
-- Name: customs_declaration customs_declaration_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customs_declaration
    ADD CONSTRAINT customs_declaration_pkey PRIMARY KEY (id);


--
-- Name: estimate estimate_estimate_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.estimate
    ADD CONSTRAINT estimate_estimate_id_key UNIQUE (estimate_id);


--
-- Name: estimate estimate_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.estimate
    ADD CONSTRAINT estimate_pkey PRIMARY KEY (id);


--
-- Name: exception_record exception_record_exception_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exception_record
    ADD CONSTRAINT exception_record_exception_id_key UNIQUE (exception_id);


--
-- Name: exception_record exception_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exception_record
    ADD CONSTRAINT exception_record_pkey PRIMARY KEY (id);


--
-- Name: handling_activity handling_activity_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.handling_activity
    ADD CONSTRAINT handling_activity_pkey PRIMARY KEY (id);


--
-- Name: invoice_line_item ili_seq_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_line_item
    ADD CONSTRAINT ili_seq_unique UNIQUE (invoice_id, seq_number);


--
-- Name: invoice invoice_booking_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice
    ADD CONSTRAINT invoice_booking_id_key UNIQUE (booking_id);


--
-- Name: invoice invoice_invoice_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice
    ADD CONSTRAINT invoice_invoice_number_key UNIQUE (invoice_number);


--
-- Name: invoice_line_item invoice_line_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_line_item
    ADD CONSTRAINT invoice_line_item_pkey PRIMARY KEY (id);


--
-- Name: invoice invoice_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice
    ADD CONSTRAINT invoice_pkey PRIMARY KEY (id);


--
-- Name: itinerary itinerary_itinerary_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.itinerary
    ADD CONSTRAINT itinerary_itinerary_id_key UNIQUE (itinerary_id);


--
-- Name: itinerary itinerary_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.itinerary
    ADD CONSTRAINT itinerary_pkey PRIMARY KEY (id);


--
-- Name: leg leg_itinerary_id_seq_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leg
    ADD CONSTRAINT leg_itinerary_id_seq_number_key UNIQUE (itinerary_id, seq_number);


--
-- Name: leg leg_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leg
    ADD CONSTRAINT leg_pkey PRIMARY KEY (id);


--
-- Name: location location_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.location
    ADD CONSTRAINT location_pkey PRIMARY KEY (unlocode);


--
-- Name: notification notification_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_pkey PRIMARY KEY (id);


--
-- Name: pricing_rule pricing_rule_currency_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pricing_rule
    ADD CONSTRAINT pricing_rule_currency_key UNIQUE (currency);


--
-- Name: pricing_rule pricing_rule_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pricing_rule
    ADD CONSTRAINT pricing_rule_pkey PRIMARY KEY (id);


--
-- Name: route_candidate route_candidate_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.route_candidate
    ADD CONSTRAINT route_candidate_pkey PRIMARY KEY (id);


--
-- Name: route_candidate route_candidate_rank_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.route_candidate
    ADD CONSTRAINT route_candidate_rank_unique UNIQUE (estimate_id, rank);


--
-- Name: schema_migrations schema_migrations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schema_migrations
    ADD CONSTRAINT schema_migrations_pkey PRIMARY KEY (version);


--
-- Name: session session_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session
    ADD CONSTRAINT session_pkey PRIMARY KEY (id);


--
-- Name: session session_session_token_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session
    ADD CONSTRAINT session_session_token_key UNIQUE (session_token);


--
-- Name: shipper shipper_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipper
    ADD CONSTRAINT shipper_email_key UNIQUE (email);


--
-- Name: shipper shipper_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipper
    ADD CONSTRAINT shipper_pkey PRIMARY KEY (id);


--
-- Name: shipper shipper_shipper_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipper
    ADD CONSTRAINT shipper_shipper_id_key UNIQUE (shipper_id);


--
-- Name: tracking_activity tracking_activity_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tracking_activity
    ADD CONSTRAINT tracking_activity_pkey PRIMARY KEY (id);


--
-- Name: tracking_activity tracking_activity_tracking_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tracking_activity
    ADD CONSTRAINT tracking_activity_tracking_number_key UNIQUE (tracking_number);


--
-- Name: tracking_state_audit tracking_state_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tracking_state_audit
    ADD CONSTRAINT tracking_state_audit_pkey PRIMARY KEY (id);


--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (id);


--
-- Name: user_roles user_roles_user_id_role_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_user_id_role_key UNIQUE (user_id, role);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users users_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_user_id_key UNIQUE (user_id);


--
-- Name: voyage voyage_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.voyage
    ADD CONSTRAINT voyage_pkey PRIMARY KEY (id);


--
-- Name: voyage voyage_voyage_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.voyage
    ADD CONSTRAINT voyage_voyage_number_key UNIQUE (voyage_number);


--
-- Name: cargo_booking_status_idx; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX cargo_booking_status_idx ON cargo_tracker_e2e.cargo USING btree (booking_status);


--
-- Name: cargo_cargo_type_idx; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX cargo_cargo_type_idx ON cargo_tracker_e2e.cargo USING btree (cargo_type);


--
-- Name: cargo_shipper_id_idx; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX cargo_shipper_id_idx ON cargo_tracker_e2e.cargo USING btree (shipper_id);


--
-- Name: carrier_movement_voyage_id_idx; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX carrier_movement_voyage_id_idx ON cargo_tracker_e2e.carrier_movement USING btree (voyage_id);


--
-- Name: estimate_shipper_id_idx; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX estimate_shipper_id_idx ON cargo_tracker_e2e.estimate USING btree (shipper_id);


--
-- Name: estimate_status_idx; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX estimate_status_idx ON cargo_tracker_e2e.estimate USING btree (estimate_status);


--
-- Name: idx_cargo_itinerary; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX idx_cargo_itinerary ON cargo_tracker_e2e.cargo USING btree (itinerary_id);


--
-- Name: idx_confirmation_code_booking; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX idx_confirmation_code_booking ON cargo_tracker_e2e.confirmation_code USING btree (booking_id);


--
-- Name: idx_currency_rate_lookup; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX idx_currency_rate_lookup ON cargo_tracker_e2e.currency_rate USING btree (from_currency, to_currency, valid_from);


--
-- Name: idx_customs_declaration_booking; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX idx_customs_declaration_booking ON cargo_tracker_e2e.customs_declaration USING btree (booking_id);


--
-- Name: idx_customs_declaration_status; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX idx_customs_declaration_status ON cargo_tracker_e2e.customs_declaration USING btree (declaration_status);


--
-- Name: idx_handling_activity_booking; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX idx_handling_activity_booking ON cargo_tracker_e2e.handling_activity USING btree (booking_id);


--
-- Name: idx_handling_activity_time; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX idx_handling_activity_time ON cargo_tracker_e2e.handling_activity USING btree (event_completion_time DESC);


--
-- Name: idx_itinerary_booking; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX idx_itinerary_booking ON cargo_tracker_e2e.itinerary USING btree (booking_id);


--
-- Name: idx_leg_voyage; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX idx_leg_voyage ON cargo_tracker_e2e.leg USING btree (voyage_number);


--
-- Name: idx_notification_booking; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX idx_notification_booking ON cargo_tracker_e2e.notification USING btree (booking_id, created_at DESC);


--
-- Name: idx_session_expires_at; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX idx_session_expires_at ON cargo_tracker_e2e.session USING btree (expires_at);


--
-- Name: idx_session_user; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX idx_session_user ON cargo_tracker_e2e.session USING btree (user_id);


--
-- Name: idx_tracking_activity_booking; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX idx_tracking_activity_booking ON cargo_tracker_e2e.tracking_activity USING btree (booking_id);


--
-- Name: route_candidate_estimate_id_idx; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX route_candidate_estimate_id_idx ON cargo_tracker_e2e.route_candidate USING btree (estimate_id);


--
-- Name: shipper_email_idx; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX shipper_email_idx ON cargo_tracker_e2e.shipper USING btree (email);


--
-- Name: user_roles_user_id_idx; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX user_roles_user_id_idx ON cargo_tracker_e2e.user_roles USING btree (user_id);


--
-- Name: users_email_idx; Type: INDEX; Schema: cargo_tracker_e2e; Owner: -
--

CREATE INDEX users_email_idx ON cargo_tracker_e2e.users USING btree (email);


--
-- Name: cargo_booking_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cargo_booking_status_idx ON public.cargo USING btree (booking_status);


--
-- Name: cargo_cargo_type_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cargo_cargo_type_idx ON public.cargo USING btree (cargo_type);


--
-- Name: cargo_shipper_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cargo_shipper_id_idx ON public.cargo USING btree (shipper_id);


--
-- Name: carrier_movement_voyage_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX carrier_movement_voyage_id_idx ON public.carrier_movement USING btree (voyage_id);


--
-- Name: estimate_shipper_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX estimate_shipper_id_idx ON public.estimate USING btree (shipper_id);


--
-- Name: estimate_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX estimate_status_idx ON public.estimate USING btree (estimate_status);


--
-- Name: idx_cargo_itinerary; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cargo_itinerary ON public.cargo USING btree (itinerary_id);


--
-- Name: idx_confirmation_code_booking; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_confirmation_code_booking ON public.confirmation_code USING btree (booking_id);


--
-- Name: idx_currency_rate_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_currency_rate_lookup ON public.currency_rate USING btree (from_currency, to_currency, valid_from);


--
-- Name: idx_customs_declaration_booking; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customs_declaration_booking ON public.customs_declaration USING btree (booking_id);


--
-- Name: idx_customs_declaration_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customs_declaration_status ON public.customs_declaration USING btree (declaration_status);


--
-- Name: idx_er_by_tracking_number; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_er_by_tracking_number ON public.exception_record USING btree (tracking_number, reported_at DESC);


--
-- Name: idx_er_by_type_severity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_er_by_type_severity ON public.exception_record USING btree (exception_type, severity);


--
-- Name: idx_er_unresolved; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_er_unresolved ON public.exception_record USING btree (resolved_at) WHERE (resolved_at IS NULL);


--
-- Name: idx_handling_activity_booking; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_handling_activity_booking ON public.handling_activity USING btree (booking_id);


--
-- Name: idx_handling_activity_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_handling_activity_time ON public.handling_activity USING btree (event_completion_time DESC);


--
-- Name: idx_invoice_by_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_invoice_by_status ON public.invoice USING btree (payment_status, due_date);


--
-- Name: idx_itinerary_booking; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_itinerary_booking ON public.itinerary USING btree (booking_id);


--
-- Name: idx_leg_voyage; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_leg_voyage ON public.leg USING btree (voyage_number);


--
-- Name: idx_notification_booking; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_booking ON public.notification USING btree (booking_id, created_at DESC);


--
-- Name: idx_notification_notification_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_notification_notification_id ON public.notification USING btree (notification_id);


--
-- Name: idx_session_expires_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_session_expires_at ON public.session USING btree (expires_at);


--
-- Name: idx_session_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_session_user ON public.session USING btree (user_id);


--
-- Name: idx_tracking_activity_booking; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tracking_activity_booking ON public.tracking_activity USING btree (booking_id);


--
-- Name: idx_tsa_changed_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tsa_changed_by ON public.tracking_state_audit USING btree (changed_by);


--
-- Name: idx_tsa_tracking_number_changed_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tsa_tracking_number_changed_at ON public.tracking_state_audit USING btree (tracking_number, changed_at DESC);


--
-- Name: route_candidate_estimate_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX route_candidate_estimate_id_idx ON public.route_candidate USING btree (estimate_id);


--
-- Name: shipper_email_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX shipper_email_idx ON public.shipper USING btree (email);


--
-- Name: user_roles_user_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX user_roles_user_id_idx ON public.user_roles USING btree (user_id);


--
-- Name: users_email_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX users_email_idx ON public.users USING btree (email);


--
-- Name: cargo cargo_destination_unlocode_fkey; Type: FK CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.cargo
    ADD CONSTRAINT cargo_destination_unlocode_fkey FOREIGN KEY (destination_unlocode) REFERENCES cargo_tracker_e2e.location(unlocode);


--
-- Name: cargo cargo_itinerary_id_fkey; Type: FK CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.cargo
    ADD CONSTRAINT cargo_itinerary_id_fkey FOREIGN KEY (itinerary_id) REFERENCES cargo_tracker_e2e.itinerary(itinerary_id);


--
-- Name: cargo cargo_origin_unlocode_fkey; Type: FK CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.cargo
    ADD CONSTRAINT cargo_origin_unlocode_fkey FOREIGN KEY (origin_unlocode) REFERENCES cargo_tracker_e2e.location(unlocode);


--
-- Name: cargo cargo_shipper_id_fkey; Type: FK CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.cargo
    ADD CONSTRAINT cargo_shipper_id_fkey FOREIGN KEY (shipper_id) REFERENCES cargo_tracker_e2e.shipper(id);


--
-- Name: carrier_movement carrier_movement_arrival_location_unlocode_fkey; Type: FK CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.carrier_movement
    ADD CONSTRAINT carrier_movement_arrival_location_unlocode_fkey FOREIGN KEY (arrival_location_unlocode) REFERENCES cargo_tracker_e2e.location(unlocode);


--
-- Name: carrier_movement carrier_movement_departure_location_unlocode_fkey; Type: FK CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.carrier_movement
    ADD CONSTRAINT carrier_movement_departure_location_unlocode_fkey FOREIGN KEY (departure_location_unlocode) REFERENCES cargo_tracker_e2e.location(unlocode);


--
-- Name: carrier_movement carrier_movement_voyage_id_fkey; Type: FK CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.carrier_movement
    ADD CONSTRAINT carrier_movement_voyage_id_fkey FOREIGN KEY (voyage_id) REFERENCES cargo_tracker_e2e.voyage(id) ON DELETE CASCADE;


--
-- Name: estimate estimate_destination_unlocode_fkey; Type: FK CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.estimate
    ADD CONSTRAINT estimate_destination_unlocode_fkey FOREIGN KEY (destination_unlocode) REFERENCES cargo_tracker_e2e.location(unlocode);


--
-- Name: estimate estimate_origin_unlocode_fkey; Type: FK CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.estimate
    ADD CONSTRAINT estimate_origin_unlocode_fkey FOREIGN KEY (origin_unlocode) REFERENCES cargo_tracker_e2e.location(unlocode);


--
-- Name: estimate estimate_shipper_id_fkey; Type: FK CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.estimate
    ADD CONSTRAINT estimate_shipper_id_fkey FOREIGN KEY (shipper_id) REFERENCES cargo_tracker_e2e.shipper(id);


--
-- Name: handling_activity handling_activity_location_unlocode_fkey; Type: FK CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.handling_activity
    ADD CONSTRAINT handling_activity_location_unlocode_fkey FOREIGN KEY (location_unlocode) REFERENCES cargo_tracker_e2e.location(unlocode);


--
-- Name: itinerary itinerary_booking_id_fkey; Type: FK CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.itinerary
    ADD CONSTRAINT itinerary_booking_id_fkey FOREIGN KEY (booking_id) REFERENCES cargo_tracker_e2e.cargo(booking_id);


--
-- Name: leg leg_itinerary_id_fkey; Type: FK CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.leg
    ADD CONSTRAINT leg_itinerary_id_fkey FOREIGN KEY (itinerary_id) REFERENCES cargo_tracker_e2e.itinerary(itinerary_id) ON DELETE CASCADE;


--
-- Name: leg leg_load_location_unlocode_fkey; Type: FK CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.leg
    ADD CONSTRAINT leg_load_location_unlocode_fkey FOREIGN KEY (load_location_unlocode) REFERENCES cargo_tracker_e2e.location(unlocode);


--
-- Name: leg leg_unload_location_unlocode_fkey; Type: FK CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.leg
    ADD CONSTRAINT leg_unload_location_unlocode_fkey FOREIGN KEY (unload_location_unlocode) REFERENCES cargo_tracker_e2e.location(unlocode);


--
-- Name: route_candidate route_candidate_estimate_id_fkey; Type: FK CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.route_candidate
    ADD CONSTRAINT route_candidate_estimate_id_fkey FOREIGN KEY (estimate_id) REFERENCES cargo_tracker_e2e.estimate(id) ON DELETE CASCADE;


--
-- Name: session session_user_id_fkey; Type: FK CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.session
    ADD CONSTRAINT session_user_id_fkey FOREIGN KEY (user_id) REFERENCES cargo_tracker_e2e.users(id) ON DELETE CASCADE;


--
-- Name: user_roles user_roles_user_id_fkey; Type: FK CONSTRAINT; Schema: cargo_tracker_e2e; Owner: -
--

ALTER TABLE ONLY cargo_tracker_e2e.user_roles
    ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES cargo_tracker_e2e.users(id) ON DELETE CASCADE;


--
-- Name: cargo cargo_destination_unlocode_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cargo
    ADD CONSTRAINT cargo_destination_unlocode_fkey FOREIGN KEY (destination_unlocode) REFERENCES public.location(unlocode);


--
-- Name: cargo cargo_itinerary_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cargo
    ADD CONSTRAINT cargo_itinerary_id_fkey FOREIGN KEY (itinerary_id) REFERENCES public.itinerary(itinerary_id);


--
-- Name: cargo cargo_origin_unlocode_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cargo
    ADD CONSTRAINT cargo_origin_unlocode_fkey FOREIGN KEY (origin_unlocode) REFERENCES public.location(unlocode);


--
-- Name: cargo cargo_shipper_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cargo
    ADD CONSTRAINT cargo_shipper_id_fkey FOREIGN KEY (shipper_id) REFERENCES public.shipper(id);


--
-- Name: carrier_movement carrier_movement_arrival_location_unlocode_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carrier_movement
    ADD CONSTRAINT carrier_movement_arrival_location_unlocode_fkey FOREIGN KEY (arrival_location_unlocode) REFERENCES public.location(unlocode);


--
-- Name: carrier_movement carrier_movement_departure_location_unlocode_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carrier_movement
    ADD CONSTRAINT carrier_movement_departure_location_unlocode_fkey FOREIGN KEY (departure_location_unlocode) REFERENCES public.location(unlocode);


--
-- Name: carrier_movement carrier_movement_voyage_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carrier_movement
    ADD CONSTRAINT carrier_movement_voyage_id_fkey FOREIGN KEY (voyage_id) REFERENCES public.voyage(id) ON DELETE CASCADE;


--
-- Name: estimate estimate_destination_unlocode_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.estimate
    ADD CONSTRAINT estimate_destination_unlocode_fkey FOREIGN KEY (destination_unlocode) REFERENCES public.location(unlocode);


--
-- Name: estimate estimate_origin_unlocode_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.estimate
    ADD CONSTRAINT estimate_origin_unlocode_fkey FOREIGN KEY (origin_unlocode) REFERENCES public.location(unlocode);


--
-- Name: estimate estimate_shipper_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.estimate
    ADD CONSTRAINT estimate_shipper_id_fkey FOREIGN KEY (shipper_id) REFERENCES public.shipper(id);


--
-- Name: handling_activity handling_activity_location_unlocode_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.handling_activity
    ADD CONSTRAINT handling_activity_location_unlocode_fkey FOREIGN KEY (location_unlocode) REFERENCES public.location(unlocode);


--
-- Name: invoice_line_item invoice_line_item_invoice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_line_item
    ADD CONSTRAINT invoice_line_item_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES public.invoice(id) ON DELETE CASCADE;


--
-- Name: itinerary itinerary_booking_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.itinerary
    ADD CONSTRAINT itinerary_booking_id_fkey FOREIGN KEY (booking_id) REFERENCES public.cargo(booking_id);


--
-- Name: leg leg_itinerary_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leg
    ADD CONSTRAINT leg_itinerary_id_fkey FOREIGN KEY (itinerary_id) REFERENCES public.itinerary(itinerary_id) ON DELETE CASCADE;


--
-- Name: leg leg_load_location_unlocode_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leg
    ADD CONSTRAINT leg_load_location_unlocode_fkey FOREIGN KEY (load_location_unlocode) REFERENCES public.location(unlocode);


--
-- Name: leg leg_unload_location_unlocode_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leg
    ADD CONSTRAINT leg_unload_location_unlocode_fkey FOREIGN KEY (unload_location_unlocode) REFERENCES public.location(unlocode);


--
-- Name: route_candidate route_candidate_estimate_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.route_candidate
    ADD CONSTRAINT route_candidate_estimate_id_fkey FOREIGN KEY (estimate_id) REFERENCES public.estimate(id) ON DELETE CASCADE;


--
-- Name: session session_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session
    ADD CONSTRAINT session_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_roles user_roles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict dbmate


--
-- Dbmate schema migrations
--

INSERT INTO public.schema_migrations (version) VALUES
    ('20260702120000'),
    ('20260702130000'),
    ('20260702130100'),
    ('20260702140000'),
    ('20260706120000'),
    ('20260706120100'),
    ('20260706120200'),
    ('20260706120300'),
    ('20260706120400'),
    ('20260706120500'),
    ('20260720100000'),
    ('20260720100100'),
    ('20260720100200'),
    ('20260803100000'),
    ('20260831100000'),
    ('20260831110000'),
    ('20260831110100'),
    ('20260831120000'),
    ('20260831130000'),
    ('20260831140000'),
    ('20260928100200'),
    ('20260928100300'),
    ('20260928100400'),
    ('20261012100000'),
    ('20261012100100');
