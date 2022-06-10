CREATE SCHEMA IF NOT EXISTS APPLICATION;

CREATE TABLE APPLICATION.DOCUMENT_COMPARISON (
    ID BIGSERIAL NOT NULL,
    A_ID bigint NOT NULL,
    B_ID bigint NOT NULL,
    INFO jsonb NOT NULL,
    CONSTRAINT PK_DOCUMENT_COMPARISON PRIMARY KEY (ID)
);


CREATE TABLE APPLICATION.DOCUMENT_COMPARISON_SIMILARITY (
    ID BIGSERIAL NOT NULL,
    TYPE text,
    A_PAGE integer NOT NULL,
    B_PAGE integer NOT NULL,
    IMPORTANCE integer NOT NULL,
    COMPARISON_ID bigint NOT NULL,
    CONSTRAINT PK_DOCUMENT_COMPARISON_SIMILARITY PRIMARY KEY (ID)
);


CREATE TABLE APPLICATION.DOCUMENT_COMPARISON_TEST (
    ID BIGSERIAL NOT NULL,
    NUM_THREADS integer NOT NULL,
    SCRIPT_VERSION text NOT NULL,
    HOST text,
    INFO jsonb,
    CONSTRAINT PK_DOCUMENT_COMPARISON_TEST PRIMARY KEY (ID)
);


CREATE TABLE APPLICATION.DOCUMENT_COMPARISON_STATS (
    ID BIGSERIAL NOT NULL,
    TEST_ID bigint NOT NULL,
    A_ID bigint NOT NULL,
    B_ID bigint NOT NULL,
    T bigint NOT NULL,
    A_SIZE bigint,
    B_SIZE bigint,
    INFO jsonb,
    CONSTRAINT PK_DOCUMENT_COMPARISON_STATS PRIMARY KEY (ID)
);



ALTER TABLE APPLICATION.DOCUMENT_COMPARISON ADD FOREIGN KEY (A_ID) REFERENCES APPLICATION.DOCUMENT(ID)
    ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE APPLICATION.DOCUMENT_COMPARISON ADD FOREIGN KEY (B_ID) REFERENCES APPLICATION.DOCUMENT(ID)
    ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE APPLICATION.DOCUMENT_COMPARISON_SIMILARITY ADD FOREIGN KEY (COMPARISON_ID) REFERENCES APPLICATION.DOCUMENT_COMPARISON(ID)
    ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE APPLICATION.DOCUMENT_COMPARISON_STATS ADD FOREIGN KEY (TEST_ID) REFERENCES APPLICATION.DOCUMENT_COMPARISON_TEST(ID)
    ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE APPLICATION.DOCUMENT_COMPARISON_STATS ADD FOREIGN KEY (A_ID) REFERENCES APPLICATION.DOCUMENT(ID)
    ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE APPLICATION.DOCUMENT_COMPARISON_STATS ADD FOREIGN KEY (B_ID) REFERENCES APPLICATION.DOCUMENT(ID)
    ON DELETE NO ACTION ON UPDATE NO ACTION;


CREATE INDEX IDX_DOCUMENT_COMPARISON_DOCUMENT ON APPLICATION.DOCUMENT_COMPARISON(A_ID);
CREATE INDEX IDX_DOCUMENT_COMPARISON_DOCUMENT ON APPLICATION.DOCUMENT_COMPARISON(B_ID);

CREATE INDEX IDX_DOCUMENT_COMPARISON_SIMILARITY_DOCUMENT_COMPARISON ON APPLICATION.DOCUMENT_COMPARISON_SIMILARITY(COMPARISON_ID);

CREATE INDEX IDX_DOCUMENT_COMPARISON_STATS_DOCUMENT_COMPARISON_TEST ON APPLICATION.DOCUMENT_COMPARISON_STATS(TEST_ID);

CREATE INDEX IDX_DOCUMENT_COMPARISON_STATS_DOCUMENT ON APPLICATION.DOCUMENT_COMPARISON_STATS(A_ID);
CREATE INDEX IDX_DOCUMENT_COMPARISON_STATS_DOCUMENT ON APPLICATION.DOCUMENT_COMPARISON_STATS(B_ID);

ALTER TABLE APPLICATION.DOCUMENT_COMPARISON ADD CONSTRAINT unique_doc_compare UNIQUE (A_ID, B_ID);