SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;


CREATE SEQUENCE hibernate_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


SET default_tablespace = '';

SET default_with_oids = false;

CREATE SEQUENCE seq_launch_context
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE seq_launch_context_params
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE launch_context (
    launch_id bigint DEFAULT nextval('seq_launch_context'::regclass) NOT NULL PRIMARY KEY,
    username character varying(255),
    created_by character varying(255),
    created_at timestamp without time zone,
    client_id character varying(255)
);

CREATE TABLE launch_context_params (
    id bigint DEFAULT nextval('seq_launch_context_params'::regclass) NOT NULL PRIMARY KEY,
    launch_context bigint references launch_context(launch_id),  
    param_name character varying(255),
    param_value character varying(255)
);

CREATE TABLE resource_compartment (
    fhir_type character varying(255) NOT NULL,
    fhir_id character varying(255) NOT NULL,
    compartments character varying[] NOT NULL
);



CREATE SEQUENCE seq_resource_index_term
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



CREATE TABLE resource_index_term (
    id bigint DEFAULT nextval('seq_resource_index_term'::regclass) NOT NULL,
    fhir_id character varying(255) NOT NULL,
    fhir_type character varying(255) NOT NULL,
    search_param character varying(255) NOT NULL,
    version_id bigint NOT NULL,
    class character varying(255) NOT NULL,
    string_value text,
    composite_value character varying(255),
    date_max timestamp without time zone,
    date_min timestamp without time zone,
    token_code character varying(255),
    token_namespace character varying(255),
    token_text character varying(255),
    reference_id character varying(255),
    reference_is_external character varying(255),
    reference_type character varying(255),
    reference_version character varying(255),
    number_max real,
    number_min real
);



CREATE TABLE resource_version (
    version_id bigint NOT NULL,
    content text NOT NULL,
    fhir_id character varying(255) NOT NULL,
    fhir_type character varying(255) NOT NULL,
    rest_date timestamp without time zone NOT NULL,
    rest_operation character varying(255) NOT NULL
);


CREATE SEQUENCE seq_resource_compartment
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE seq_resource_version
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

SELECT pg_catalog.setval('hibernate_sequence', 1219157, true);



SELECT pg_catalog.setval('seq_resource_compartment', 13448, true);



SELECT pg_catalog.setval('seq_resource_index_term', 412599, true);



SELECT pg_catalog.setval('seq_resource_version', 107747, true);



ALTER TABLE ONLY resource_compartment
    ADD CONSTRAINT resource_compartment_pkey PRIMARY KEY (fhir_type, fhir_id);



ALTER TABLE ONLY resource_index_term
    ADD CONSTRAINT resource_index_term_pkey PRIMARY KEY (id);



ALTER TABLE ONLY resource_version
    ADD CONSTRAINT resource_version_pkey PRIMARY KEY (version_id);



CREATE INDEX logical_id ON resource_index_term USING btree (fhir_id, fhir_type);



CREATE INDEX logical_id_searchparam_value ON resource_index_term USING btree (fhir_type, search_param);



CREATE INDEX ref_logical_id ON resource_index_term USING btree (reference_id, reference_type);



CREATE INDEX search_param_reference ON resource_index_term USING btree (search_param, reference_type, reference_id);



CREATE INDEX search_param_string ON resource_index_term USING btree (search_param, string_value);



CREATE INDEX search_param_token ON resource_index_term USING btree (fhir_type, search_param, token_code, token_namespace);



CREATE INDEX term_version_id ON resource_index_term USING btree (version_id);



CREATE INDEX version_logical_id ON resource_version USING btree (fhir_type, fhir_id);



CREATE INDEX version_version_id ON resource_version USING btree (version_id);



REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM postgres;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO PUBLIC;

