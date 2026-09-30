-- FaceLog database initialization
-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Organizations
CREATE TABLE IF NOT EXISTS organizations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) UNIQUE NOT NULL,
    registration_code VARCHAR(50) UNIQUE,
    is_active BOOLEAN DEFAULT TRUE,
    recognition_threshold FLOAT DEFAULT 0.6,
    liveness_threshold FLOAT DEFAULT 0.5,
    sync_interval_minutes INTEGER DEFAULT 30,
    allow_manual_checkin BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Employees
CREATE TABLE IF NOT EXISTS employees (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    employee_code VARCHAR(50) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    department VARCHAR(100),
    face_embedding vector(512),
    embedding_version INTEGER DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(organization_id, employee_code)
);

CREATE INDEX idx_employees_org_active ON employees(organization_id, is_active);
CREATE INDEX idx_employees_embedding ON employees USING ivfflat (face_embedding vector_cosine_ops);

-- Devices
CREATE TABLE IF NOT EXISTS devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    device_name VARCHAR(255) NOT NULL,
    device_model VARCHAR(100),
    android_version VARCHAR(20),
    api_key_hash VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    app_version VARCHAR(20),
    battery_level INTEGER,
    last_heartbeat_at TIMESTAMP,
    last_sync_at TIMESTAMP,
    registered_at TIMESTAMP DEFAULT NOW(),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Attendance Records
CREATE TABLE IF NOT EXISTS attendance_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    employee_id UUID NOT NULL REFERENCES employees(id),
    device_id UUID NOT NULL REFERENCES devices(id),
    attendance_type VARCHAR(20) NOT NULL CHECK (attendance_type IN ('check_in', 'check_out')),
    confidence_score FLOAT NOT NULL,
    liveness_score FLOAT NOT NULL,
    captured_at TIMESTAMP NOT NULL,
    synced_at TIMESTAMP DEFAULT NOW(),
    idempotency_key VARCHAR(255) UNIQUE,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_attendance_employee_date ON attendance_records(employee_id, captured_at);
CREATE INDEX idx_attendance_device ON attendance_records(device_id, captured_at);
CREATE INDEX idx_attendance_idempotency ON attendance_records(idempotency_key);

-- Admin Users
CREATE TABLE IF NOT EXISTS admin_users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(20) DEFAULT 'admin',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Audit Logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID REFERENCES organizations(id),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id UUID,
    actor_id UUID,
    actor_type VARCHAR(20),
    details JSONB,
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_audit_org_date ON audit_logs(organization_id, created_at);

-- Seed a demo organization
INSERT INTO organizations (name, code, registration_code)
VALUES ('Demo Organization', 'DEMO', 'DEMO1234')
ON CONFLICT (code) DO NOTHING;
