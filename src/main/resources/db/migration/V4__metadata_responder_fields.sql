ALTER TABLE public.metadata_samples
    ADD COLUMN horizontal_accuracy_m double precision,
    ADD COLUMN speed_mps             double precision,
    ADD COLUMN bearing_deg           double precision,
    ADD COLUMN altitude_m            double precision,
    ADD COLUMN battery_percent       integer;
