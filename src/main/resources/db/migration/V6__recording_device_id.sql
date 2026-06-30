-- Nullable: existing recordings have no device association; new ones populated at creation.
ALTER TABLE recordings ADD COLUMN device_id UUID;
