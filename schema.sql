-- =============================================================================
-- PulseOJ Database Schema for Neon Serverless PostgreSQL
-- =============================================================================

-- Drop tables if needed (careful in production)
-- DROP TABLE IF EXISTS submissions CASCADE;
-- DROP TABLE IF EXISTS test_cases CASCADE;
-- DROP TABLE IF EXISTS problems CASCADE;
-- DROP TABLE IF EXISTS contestants CASCADE;

-- 1. Contestants Table (Managed by Teacher / Master Node)
CREATE TABLE IF NOT EXISTS contestants (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    is_locked BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Problems Table (ACM-ICPC Style Challenges)
CREATE TABLE IF NOT EXISTS problems (
    id SERIAL PRIMARY KEY,
    code VARCHAR(32) UNIQUE NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    time_limit_ms INT NOT NULL DEFAULT 1000,
    memory_limit_mb INT NOT NULL DEFAULT 256,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Test Cases Table (Hidden & Sample Test Cases)
CREATE TABLE IF NOT EXISTS test_cases (
    id SERIAL PRIMARY KEY,
    problem_id INT NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    test_order INT NOT NULL,
    input_data TEXT NOT NULL,
    expected_output TEXT NOT NULL,
    is_sample BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_problem_test_order UNIQUE (problem_id, test_order)
);

-- 4. Submissions Table (Audit Trail & Judging History)
CREATE TABLE IF NOT EXISTS submissions (
    id SERIAL PRIMARY KEY,
    contestant_id INT NOT NULL REFERENCES contestants(id),
    problem_id INT NOT NULL REFERENCES problems(id),
    language VARCHAR(20) NOT NULL,
    source_code TEXT NOT NULL,
    verdict VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    execution_time_ms BIGINT DEFAULT 0,
    memory_used_kb BIGINT DEFAULT 0,
    compiler_output TEXT,
    worker_id VARCHAR(100),
    submitted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    judged_at TIMESTAMP WITH TIME ZONE
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_submissions_contestant ON submissions(contestant_id);
CREATE INDEX IF NOT EXISTS idx_submissions_problem ON submissions(problem_id);
CREATE INDEX IF NOT EXISTS idx_submissions_verdict ON submissions(verdict);
CREATE INDEX IF NOT EXISTS idx_test_cases_problem ON test_cases(problem_id);

-- =============================================================================
-- Initial Sample Seed Data (For testing out-of-the-box)
-- =============================================================================

-- Default Contestant: username='student01', password='password123'
-- Hash: $2a$10$wE9K2j3P0M7p6gQ1q4Cj5e0y4c7P.z6J2I7rQ/6fN0iP1C.x.e7S (BCrypt)
INSERT INTO contestants (username, password_hash, full_name, is_locked)
VALUES 
    ('student01', '$2a$10$7vN3K10UqWJ21bS4j21mE.3lYtZ1C40Q6W4Z048j2.bK7g2jF0j8G', 'Nguyen Van A', FALSE),
    ('student02', '$2a$10$7vN3K10UqWJ21bS4j21mE.3lYtZ1C40Q6W4Z048j2.bK7g2jF0j8G', 'Tran Thi B', FALSE)
ON CONFLICT (username) DO NOTHING;

-- Problem 1: TWO_SUM
INSERT INTO problems (code, title, description, time_limit_ms, memory_limit_mb)
VALUES (
    'TWO_SUM',
    'Two Sum Problem',
    'Cho hai so nguyen A va B cach nhau boi dau cach tren mot dong. Hay in ra tong A + B tren mot dong duy nhat.\n\nInput:\nDong duy nhat chua 2 so nguyen A, B (-10^9 <= A, B <= 10^9).\n\nOutput:\nIn ra mot so nguyen duy nhat la tong A + B.',
    1000,
    256
) ON CONFLICT (code) DO NOTHING;

-- Test cases for TWO_SUM
INSERT INTO test_cases (problem_id, test_order, input_data, expected_output, is_sample)
SELECT p.id, 1, '2 3', '5', TRUE FROM problems p WHERE p.code = 'TWO_SUM'
ON CONFLICT (problem_id, test_order) DO NOTHING;

INSERT INTO test_cases (problem_id, test_order, input_data, expected_output, is_sample)
SELECT p.id, 2, '-10 10', '0', FALSE FROM problems p WHERE p.code = 'TWO_SUM'
ON CONFLICT (problem_id, test_order) DO NOTHING;

INSERT INTO test_cases (problem_id, test_order, input_data, expected_output, is_sample)
SELECT p.id, 3, '1000000 2000000', '3000000', FALSE FROM problems p WHERE p.code = 'TWO_SUM'
ON CONFLICT (problem_id, test_order) DO NOTHING;

-- Problem 2: FACTORIAL
INSERT INTO problems (code, title, description, time_limit_ms, memory_limit_mb)
VALUES (
    'FACTORIAL',
    'Tich Giai Thua N!',
    'Cho so nguyen N (0 <= N <= 12). Tinh N giai thua (N!).\n\nInput:\nMot so nguyen N duy nhat.\n\nOutput:\nGia tri N!.',
    1000,
    256
) ON CONFLICT (code) DO NOTHING;

INSERT INTO test_cases (problem_id, test_order, input_data, expected_output, is_sample)
SELECT p.id, 1, '5', '120', TRUE FROM problems p WHERE p.code = 'FACTORIAL'
ON CONFLICT (problem_id, test_order) DO NOTHING;

INSERT INTO test_cases (problem_id, test_order, input_data, expected_output, is_sample)
SELECT p.id, 2, '0', '1', FALSE FROM problems p WHERE p.code = 'FACTORIAL'
ON CONFLICT (problem_id, test_order) DO NOTHING;
