-- Add optional examples to questions. Existing rows remain NULL and keep all data.
ALTER TABLE question
  ADD COLUMN sample_input TEXT NULL AFTER description,
  ADD COLUMN sample_output TEXT NULL AFTER sample_input;