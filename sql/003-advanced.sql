-- Optional feature tables. Apply after 001-base.sql and 002-business.sql.
-- The database remains the source of truth; Redis is only the delivery transport.
CREATE TABLE IF NOT EXISTS advanced_guard (
  id INT PRIMARY KEY,
  description VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO advanced_guard(id, description) VALUES (1, 'queue admission'), (2, 'schedule generation');

CREATE TABLE IF NOT EXISTS registration_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  patient_id BIGINT NOT NULL,
  schedule_id BIGINT NOT NULL,
  request_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
  attempts INT NOT NULL DEFAULT 0,
  delivery_attempts INT NOT NULL DEFAULT 0,
  duplicate_deliveries INT NOT NULL DEFAULT 0,
  expires_at DATETIME(6) NOT NULL,
  next_attempt_at DATETIME(6) NOT NULL,
  last_delivered_at DATETIME(6) NULL,
  last_stream_id VARCHAR(64) NULL,
  result_registration_id BIGINT NULL,
  error_code VARCHAR(60) NULL,
  error_message VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  UNIQUE KEY uq_request_patient_key(patient_id,request_key),
  KEY ix_request_dispatch(status,next_attempt_at,last_delivered_at),
  KEY ix_request_expiry(status,expires_at),
  KEY ix_request_patient_created(patient_id,created_at),
  CONSTRAINT fk_request_patient FOREIGN KEY(patient_id) REFERENCES app_user(id),
  CONSTRAINT fk_request_schedule FOREIGN KEY(schedule_id) REFERENCES schedule(id),
  CONSTRAINT chk_request_status CHECK(status IN ('QUEUED','PROCESSING','RETRY','SUCCEEDED','FAILED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS schedule_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  doctor_id BIGINT NOT NULL,
  day_of_week INT NOT NULL,
  period VARCHAR(20) NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  total INT NOT NULL,
  fee DECIMAL(10,2) NOT NULL,
  effective_from DATE NOT NULL,
  effective_to DATE NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  KEY ix_template_doctor(doctor_id,enabled),
  CONSTRAINT fk_template_doctor FOREIGN KEY(doctor_id) REFERENCES doctor(id),
  CONSTRAINT chk_template_day CHECK(day_of_week BETWEEN 1 AND 7),
  CONSTRAINT chk_template_time CHECK(end_time > start_time),
  CONSTRAINT chk_template_total CHECK(total > 0),
  CONSTRAINT chk_template_fee CHECK(fee >= 0),
  CONSTRAINT chk_template_dates CHECK(effective_to >= effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS job_execution_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trigger_type VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  generated_count INT NOT NULL DEFAULT 0,
  skipped_count INT NOT NULL DEFAULT 0,
  error_message VARCHAR(500) NULL,
  started_at DATETIME(6) NOT NULL,
  finished_at DATETIME(6) NULL,
  KEY ix_job_started(started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
