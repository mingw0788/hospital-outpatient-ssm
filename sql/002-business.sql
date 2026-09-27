-- 核心业务表；MySQL 8.0+。NULL 生成列允许保留取消历史，同时约束有效业务唯一。
CREATE TABLE IF NOT EXISTS registration (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, patient_id BIGINT NOT NULL, schedule_id BIGINT NOT NULL,
 request_key VARCHAR(80) NOT NULL, status VARCHAR(20) NOT NULL, deadline DATETIME(6) NOT NULL,
 cancel_reason VARCHAR(500), created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 active_flag TINYINT GENERATED ALWAYS AS (CASE WHEN status IN ('RESERVED','BOOKED') THEN 1 ELSE NULL END) STORED,
 UNIQUE KEY uk_registration_request(patient_id,request_key), UNIQUE KEY uk_registration_active(patient_id,schedule_id,active_flag),
 KEY ix_registration_schedule(schedule_id,status), KEY ix_registration_patient(patient_id,created_at),
 FOREIGN KEY(patient_id) REFERENCES app_user(id), FOREIGN KEY(schedule_id) REFERENCES schedule(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS visit (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, registration_id BIGINT NOT NULL UNIQUE, patient_id BIGINT NOT NULL,
 doctor_id BIGINT NOT NULL, schedule_id BIGINT NOT NULL, queue_no INT NOT NULL, sort_no INT NOT NULL,
 status VARCHAR(20) NOT NULL, called_at DATETIME(6), started_at DATETIME(6), completed_at DATETIME(6),
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_visit_queue(schedule_id,queue_no), UNIQUE KEY uk_visit_sort(schedule_id,sort_no),
 KEY ix_visit_doctor(doctor_id,status,sort_no), KEY ix_visit_patient(patient_id,created_at),
 FOREIGN KEY(registration_id) REFERENCES registration(id), FOREIGN KEY(patient_id) REFERENCES app_user(id),
 FOREIGN KEY(doctor_id) REFERENCES doctor(id), FOREIGN KEY(schedule_id) REFERENCES schedule(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS medical_record (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, visit_id BIGINT NOT NULL UNIQUE, patient_id BIGINT NOT NULL, doctor_id BIGINT NOT NULL,
 chief_complaint VARCHAR(1000) NOT NULL DEFAULT '', history TEXT, past_history TEXT, allergies VARCHAR(1000),
 diagnosis VARCHAR(2000) NOT NULL DEFAULT '', advice TEXT, status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
 draft_items LONGTEXT, submitted_at DATETIME(6), created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(visit_id) REFERENCES visit(id), FOREIGN KEY(patient_id) REFERENCES app_user(id), FOREIGN KEY(doctor_id) REFERENCES doctor(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS prescription (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, visit_id BIGINT NOT NULL, patient_id BIGINT NOT NULL, doctor_id BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL, amount DECIMAL(10,2) NOT NULL, replaces_id BIGINT,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), dispensed_at DATETIME(6),
 active_flag TINYINT GENERATED ALWAYS AS (CASE WHEN status <> 'VOID' THEN 1 ELSE NULL END) STORED,
 UNIQUE KEY uk_prescription_active(visit_id,active_flag), KEY ix_prescription_status(status,created_at),
 FOREIGN KEY(visit_id) REFERENCES visit(id), FOREIGN KEY(patient_id) REFERENCES app_user(id),
 FOREIGN KEY(doctor_id) REFERENCES doctor(id), FOREIGN KEY(replaces_id) REFERENCES prescription(id), CHECK(amount>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS prescription_item (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, prescription_id BIGINT NOT NULL, drug_id BIGINT NOT NULL,
 drug_name VARCHAR(100) NOT NULL,spec VARCHAR(100),unit VARCHAR(30),unit_price DECIMAL(10,2) NOT NULL,
 quantity INT NOT NULL,usage_text VARCHAR(500) NOT NULL,
 UNIQUE KEY uk_prescription_drug(prescription_id,drug_id), FOREIGN KEY(prescription_id) REFERENCES prescription(id),
 FOREIGN KEY(drug_id) REFERENCES drug(id),CHECK(quantity>0),CHECK(unit_price>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS bill (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, registration_id BIGINT NOT NULL, patient_id BIGINT NOT NULL,
 prescription_id BIGINT, type VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL,
 amount DECIMAL(10,2) NOT NULL,deadline DATETIME(6) NOT NULL,created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 paid_at DATETIME(6),refund_at DATETIME(6),refund_requested BOOLEAN NOT NULL DEFAULT FALSE,
 registration_unique BIGINT GENERATED ALWAYS AS (CASE WHEN type='REGISTRATION' THEN registration_id ELSE NULL END) STORED,
 prescription_active BIGINT GENERATED ALWAYS AS (CASE WHEN type='PRESCRIPTION' AND status IN ('UNPAID','PAID') THEN prescription_id ELSE NULL END) STORED,
 UNIQUE KEY uk_bill_registration(registration_unique), UNIQUE KEY uk_bill_prescription(prescription_active),
 KEY ix_bill_expiry(status,deadline),KEY ix_bill_patient(patient_id,created_at),
 FOREIGN KEY(registration_id) REFERENCES registration(id),FOREIGN KEY(patient_id) REFERENCES app_user(id),
 FOREIGN KEY(prescription_id) REFERENCES prescription(id),CHECK(amount>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS payment_record (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,bill_id BIGINT NOT NULL UNIQUE,patient_id BIGINT NOT NULL,
 actor_id BIGINT,amount DECIMAL(10,2) NOT NULL,transaction_no VARCHAR(80) NOT NULL UNIQUE,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(bill_id) REFERENCES bill(id),FOREIGN KEY(patient_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS refund_record (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,bill_id BIGINT NOT NULL UNIQUE,patient_id BIGINT NOT NULL,
 actor_id BIGINT,amount DECIMAL(10,2) NOT NULL,transaction_no VARCHAR(80) NOT NULL UNIQUE,reason VARCHAR(500),
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(bill_id) REFERENCES bill(id),FOREIGN KEY(patient_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS queue_event (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,visit_id BIGINT NOT NULL,action VARCHAR(30) NOT NULL,
 actor_id BIGINT,request_key VARCHAR(80),created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_queue_request(visit_id,action,request_key), FOREIGN KEY(visit_id) REFERENCES visit(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
