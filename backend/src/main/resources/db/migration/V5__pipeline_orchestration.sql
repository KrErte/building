-- V5: Pipeline Orchestration Engine

CREATE TABLE IF NOT EXISTS pipelines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    current_step INTEGER DEFAULT 0,
    total_steps INTEGER DEFAULT 0,
    context_json TEXT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_pipelines_user_id ON pipelines(user_id);
CREATE INDEX idx_pipelines_project_id ON pipelines(project_id);
CREATE INDEX idx_pipelines_status ON pipelines(status);

CREATE TABLE IF NOT EXISTS pipeline_steps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id UUID NOT NULL REFERENCES pipelines(id) ON DELETE CASCADE,
    step_order INTEGER NOT NULL,
    step_type VARCHAR(50) NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    input_json TEXT,
    output_json TEXT,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);
CREATE INDEX idx_pipeline_steps_pipeline_id ON pipeline_steps(pipeline_id);
