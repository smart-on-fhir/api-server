--
-- PostgreSQL database dump
--

SET statement_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;

--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner: 
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


SET search_path = public, pg_catalog;

--
-- Name: hibernate_sequence; Type: SEQUENCE; Schema: public; Owner: fhir
--

CREATE SEQUENCE hibernate_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.hibernate_sequence OWNER TO fhir;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: resource_compartment; Type: TABLE; Schema: public; Owner: fhir; Tablespace: 
--

CREATE TABLE resource_compartment (
    fhir_type character varying(255) NOT NULL,
    fhir_id character varying(255) NOT NULL,
    compartments character varying[] NOT NULL
);


ALTER TABLE public.resource_compartment OWNER TO fhir;

--
-- Name: seq_resource_index_term; Type: SEQUENCE; Schema: public; Owner: fhir
--

CREATE SEQUENCE seq_resource_index_term
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.seq_resource_index_term OWNER TO fhir;

--
-- Name: resource_index_term; Type: TABLE; Schema: public; Owner: fhir; Tablespace: 
--

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


ALTER TABLE public.resource_index_term OWNER TO fhir;

--
-- Name: resource_version; Type: TABLE; Schema: public; Owner: fhir; Tablespace: 
--

CREATE TABLE resource_version (
    version_id bigint NOT NULL,
    content text NOT NULL,
    fhir_id character varying(255) NOT NULL,
    fhir_type character varying(255) NOT NULL,
    rest_date timestamp without time zone NOT NULL,
    rest_operation character varying(255) NOT NULL
);


ALTER TABLE public.resource_version OWNER TO fhir;

--
-- Name: seq_resource_compartment; Type: SEQUENCE; Schema: public; Owner: fhir
--

CREATE SEQUENCE seq_resource_compartment
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.seq_resource_compartment OWNER TO fhir;

--
-- Name: seq_resource_version; Type: SEQUENCE; Schema: public; Owner: fhir
--

CREATE SEQUENCE seq_resource_version
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.seq_resource_version OWNER TO fhir;

--
-- Name: hibernate_sequence; Type: SEQUENCE SET; Schema: public; Owner: fhir
--

SELECT pg_catalog.setval('hibernate_sequence', 1219157, true);


--
-- Data for Name: resource_compartment; Type: TABLE DATA; Schema: public; Owner: fhir
--

COPY resource_compartment (fhir_type, fhir_id, compartments) FROM stdin;
\.


--
-- Data for Name: resource_index_term; Type: TABLE DATA; Schema: public; Owner: fhir
--

COPY resource_index_term (id, fhir_id, fhir_type, search_param, version_id, class, string_value, composite_value, date_max, date_min, token_code, token_namespace, token_text, reference_id, reference_is_external, reference_type, reference_version, number_max, number_min) FROM stdin;
\.


--
-- Data for Name: resource_version; Type: TABLE DATA; Schema: public; Owner: fhir
--

COPY resource_version (version_id, content, fhir_id, fhir_type, rest_date, rest_operation) FROM stdin;
\.


--
-- Name: seq_resource_compartment; Type: SEQUENCE SET; Schema: public; Owner: fhir
--

SELECT pg_catalog.setval('seq_resource_compartment', 13448, true);


--
-- Name: seq_resource_index_term; Type: SEQUENCE SET; Schema: public; Owner: fhir
--

SELECT pg_catalog.setval('seq_resource_index_term', 412599, true);


--
-- Name: seq_resource_version; Type: SEQUENCE SET; Schema: public; Owner: fhir
--

SELECT pg_catalog.setval('seq_resource_version', 107747, true);


--
-- Name: resource_compartment_pkey; Type: CONSTRAINT; Schema: public; Owner: fhir; Tablespace: 
--

ALTER TABLE ONLY resource_compartment
    ADD CONSTRAINT resource_compartment_pkey PRIMARY KEY (fhir_type, fhir_id);


--
-- Name: resource_index_term_pkey; Type: CONSTRAINT; Schema: public; Owner: fhir; Tablespace: 
--

ALTER TABLE ONLY resource_index_term
    ADD CONSTRAINT resource_index_term_pkey PRIMARY KEY (id);


--
-- Name: resource_version_pkey; Type: CONSTRAINT; Schema: public; Owner: fhir; Tablespace: 
--

ALTER TABLE ONLY resource_version
    ADD CONSTRAINT resource_version_pkey PRIMARY KEY (version_id);


--
-- Name: logical_id; Type: INDEX; Schema: public; Owner: fhir; Tablespace: 
--

CREATE INDEX logical_id ON resource_index_term USING btree (fhir_id, fhir_type);


--
-- Name: logical_id_searchparam_value; Type: INDEX; Schema: public; Owner: fhir; Tablespace: 
--

CREATE INDEX logical_id_searchparam_value ON resource_index_term USING btree (fhir_type, search_param);


--
-- Name: ref_logical_id; Type: INDEX; Schema: public; Owner: fhir; Tablespace: 
--

CREATE INDEX ref_logical_id ON resource_index_term USING btree (reference_id, reference_type);


--
-- Name: search_param_reference; Type: INDEX; Schema: public; Owner: fhir; Tablespace: 
--

CREATE INDEX search_param_reference ON resource_index_term USING btree (search_param, reference_type, reference_id);


--
-- Name: search_param_string; Type: INDEX; Schema: public; Owner: fhir; Tablespace: 
--

CREATE INDEX search_param_string ON resource_index_term USING btree (search_param, string_value);


--
-- Name: search_param_token; Type: INDEX; Schema: public; Owner: fhir; Tablespace: 
--

CREATE INDEX search_param_token ON resource_index_term USING btree (fhir_type, search_param, token_code, token_namespace);


--
-- Name: term_version_id; Type: INDEX; Schema: public; Owner: fhir; Tablespace: 
--

CREATE INDEX term_version_id ON resource_index_term USING btree (version_id);


--
-- Name: version_logical_id; Type: INDEX; Schema: public; Owner: fhir; Tablespace: 
--

CREATE INDEX version_logical_id ON resource_version USING btree (fhir_type, fhir_id);


--
-- Name: version_version_id; Type: INDEX; Schema: public; Owner: fhir; Tablespace: 
--

CREATE INDEX version_version_id ON resource_version USING btree (version_id);


--
-- Name: public; Type: ACL; Schema: -; Owner: postgres
--

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM postgres;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO PUBLIC;


--
-- PostgreSQL database dump complete
--

