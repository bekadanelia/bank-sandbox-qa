-- Runs once when the PostgreSQL container is created.
-- The test suite verifies data through this role, which can read everything and change nothing.
CREATE ROLE qa_readonly LOGIN PASSWORD 'qa_readonly';
GRANT pg_read_all_data TO qa_readonly;
