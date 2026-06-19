ALTER TABLE recording_segments
    ADD COLUMN source text NOT NULL DEFAULT 'STREAMED',
    ADD COLUMN quality text NULL;
