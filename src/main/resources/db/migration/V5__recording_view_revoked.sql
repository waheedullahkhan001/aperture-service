ALTER TABLE public.recordings
    ADD COLUMN view_revoked boolean NOT NULL DEFAULT false;
