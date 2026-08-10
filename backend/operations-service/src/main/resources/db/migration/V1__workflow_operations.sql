CREATE TABLE workflow_templates (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(180) NOT NULL,
    reference_type VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    template_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_workflow_template_version UNIQUE (company_id, code, template_version),
    CONSTRAINT ck_workflow_template_version CHECK (template_version > 0),
    CONSTRAINT ck_workflow_template_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED'))
);

CREATE TABLE workflow_template_steps (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES workflow_templates(id) ON DELETE CASCADE,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description TEXT,
    step_sequence INTEGER NOT NULL,
    required_step BOOLEAN NOT NULL DEFAULT TRUE,
    estimated_minutes INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_workflow_step_code UNIQUE (template_id, code),
    CONSTRAINT uk_workflow_step_sequence UNIQUE (template_id, step_sequence),
    CONSTRAINT ck_workflow_step_sequence CHECK (step_sequence > 0),
    CONSTRAINT ck_workflow_step_estimate CHECK (estimated_minutes IS NULL OR estimated_minutes >= 0)
);

CREATE TABLE work_executions (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    template_id UUID NOT NULL REFERENCES workflow_templates(id),
    template_code_snapshot VARCHAR(60) NOT NULL,
    template_name_snapshot VARCHAR(180) NOT NULL,
    template_version_snapshot INTEGER NOT NULL,
    reference_type VARCHAR(80) NOT NULL,
    reference_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_work_execution_reference UNIQUE (company_id, template_id, reference_type, reference_id),
    CONSTRAINT ck_work_execution_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);

CREATE TABLE work_execution_steps (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL REFERENCES work_executions(id) ON DELETE CASCADE,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description TEXT,
    step_sequence INTEGER NOT NULL,
    required_step BOOLEAN NOT NULL,
    estimated_minutes INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_work_execution_step_code UNIQUE (execution_id, code),
    CONSTRAINT uk_work_execution_step_sequence UNIQUE (execution_id, step_sequence),
    CONSTRAINT ck_work_execution_step_sequence CHECK (step_sequence > 0),
    CONSTRAINT ck_work_execution_step_estimate CHECK (estimated_minutes IS NULL OR estimated_minutes >= 0),
    CONSTRAINT ck_work_execution_step_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED', 'CANCELLED'))
);

CREATE INDEX idx_workflow_templates_company_status
    ON workflow_templates(company_id, status, reference_type);
CREATE INDEX idx_work_executions_company_status
    ON work_executions(company_id, status, created_at DESC);
CREATE INDEX idx_work_executions_reference
    ON work_executions(company_id, reference_type, reference_id);
