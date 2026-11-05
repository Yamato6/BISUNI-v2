-- =====================================================================
-- BISUNI v2 - Esquema PostgreSQL
-- Migrado desde MySQL (v1: DB/BISUNI_DB.sql). Fuente única de verdad del
-- esquema; lo aplica Flyway y lo consumen desktop/ (JDBC) y web/ (Drizzle).
-- =====================================================================

-- ---------------------------------------------------------------------
-- Tipos enumerados (en v1 eran ENUM en línea de MySQL)
-- ---------------------------------------------------------------------
CREATE TYPE user_role       AS ENUM ('Admin', 'Teacher', 'Student', 'Aspirant');
CREATE TYPE record_status   AS ENUM ('Active', 'Inactive');
CREATE TYPE aspirant_status AS ENUM ('Pending', 'Accepted', 'Rejected');
CREATE TYPE weekday         AS ENUM ('Mon', 'Tue', 'Wed', 'Thu', 'Fri');

-- ---------------------------------------------------------------------
-- USERS
-- password_hash: bcrypt ($2a$/$2b$), verificable desde Java y Node.
-- email se guarda normalizado en minúsculas.
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id_user       INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    first_name    VARCHAR(50)  NOT NULL,
    last_name     VARCHAR(50)  NOT NULL,
    role          user_role     NOT NULL,
    status        record_status NOT NULL DEFAULT 'Active',
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT users_password_is_bcrypt CHECK (password_hash ~ '^\$2[aby]\$\d{2}\$.{53}$')
);

-- ---------------------------------------------------------------------
-- CAREER
-- ---------------------------------------------------------------------
CREATE TABLE career (
    id_career   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    career_name VARCHAR(100)  NOT NULL UNIQUE,
    status      record_status NOT NULL DEFAULT 'Active'
);

-- ---------------------------------------------------------------------
-- PERFILES (uno por usuario)
-- ---------------------------------------------------------------------
CREATE TABLE admin_profile (
    id_admin    INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_user     INT NOT NULL UNIQUE REFERENCES users (id_user),
    position    VARCHAR(50),
    last_access TIMESTAMPTZ
);

CREATE TABLE teacher_profile (
    id_teacher INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_user    INT NOT NULL UNIQUE REFERENCES users (id_user),
    specialty  VARCHAR(100),
    phone      VARCHAR(20)
);

CREATE TABLE aspirant_profile (
    id_aspirant      INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_user          INT  NOT NULL UNIQUE REFERENCES users (id_user),
    curp             CHAR(18) NOT NULL UNIQUE,
    id_career        INT  NOT NULL REFERENCES career (id_career),
    application_date DATE NOT NULL DEFAULT CURRENT_DATE,
    status           aspirant_status NOT NULL DEFAULT 'Pending',
    -- Formato oficial de CURP: 4 letras, fecha AAMMDD, sexo, estado, 3 consonantes, homoclave, dígito
    CONSTRAINT aspirant_curp_format CHECK (curp ~ '^[A-Z]{4}[0-9]{6}[HMX][A-Z]{5}[A-Z0-9][0-9]$')
);

-- ---------------------------------------------------------------------
-- SUBJECTS
-- ---------------------------------------------------------------------
CREATE TABLE subjects (
    id_subject   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subject_name VARCHAR(100)  NOT NULL,
    credits      INT           NOT NULL CHECK (credits > 0),
    semester     INT           NOT NULL CHECK (semester BETWEEN 1 AND 12),
    weekly_hours INT           NOT NULL CHECK (weekly_hours > 0),
    id_career    INT           NOT NULL REFERENCES career (id_career),
    status       record_status NOT NULL DEFAULT 'Active',
    UNIQUE (id_career, subject_name)
);

-- ---------------------------------------------------------------------
-- SCHOOL GROUPS
-- ---------------------------------------------------------------------
CREATE TABLE school_groups (
    id_group   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_name VARCHAR(50)   NOT NULL UNIQUE,
    id_career  INT           NOT NULL REFERENCES career (id_career),
    semester   INT           NOT NULL CHECK (semester BETWEEN 1 AND 12),
    id_tutor   INT           REFERENCES teacher_profile (id_teacher),
    status     record_status NOT NULL DEFAULT 'Active',
    -- Permite FKs compuestas que garantizan "alumno y grupo de la misma carrera"
    UNIQUE (id_group, id_career)
);

-- ---------------------------------------------------------------------
-- STUDENT PROFILE
-- id_group es la ÚNICA fuente de pertenencia a grupo (v1 tenía además la
-- tabla group_student, que se desincronizaba). La FK compuesta obliga a
-- que el grupo sea de la misma carrera que el alumno.
-- ---------------------------------------------------------------------
CREATE TABLE student_profile (
    id_student INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_user    INT          NOT NULL UNIQUE REFERENCES users (id_user),
    id_career  INT          NOT NULL REFERENCES career (id_career),
    semester   INT          NOT NULL DEFAULT 1 CHECK (semester BETWEEN 1 AND 12),
    gpa        NUMERIC(4,2) NOT NULL DEFAULT 0 CHECK (gpa BETWEEN 0 AND 10),
    id_group   INT,
    FOREIGN KEY (id_group, id_career) REFERENCES school_groups (id_group, id_career)
);
CREATE INDEX idx_student_group ON student_profile (id_group);

-- ---------------------------------------------------------------------
-- TEACHER_SUBJECT: materias que un profesor está habilitado para impartir
-- ---------------------------------------------------------------------
CREATE TABLE teacher_subject (
    id_teacher INT NOT NULL REFERENCES teacher_profile (id_teacher),
    id_subject INT NOT NULL REFERENCES subjects (id_subject),
    PRIMARY KEY (id_teacher, id_subject)
);

-- ---------------------------------------------------------------------
-- GROUP_SUBJECT: materias que cursa un grupo y QUIÉN las imparte.
-- id_teacher es nuevo en v2; debe ser un profesor habilitado para la
-- materia (FK compuesta a teacher_subject).
-- ---------------------------------------------------------------------
CREATE TABLE group_subject (
    id_group   INT NOT NULL REFERENCES school_groups (id_group),
    id_subject INT NOT NULL REFERENCES subjects (id_subject),
    id_teacher INT,
    PRIMARY KEY (id_group, id_subject),
    UNIQUE (id_group, id_subject, id_teacher),
    FOREIGN KEY (id_teacher, id_subject) REFERENCES teacher_subject (id_teacher, id_subject)
);
CREATE INDEX idx_group_subject_teacher ON group_subject (id_teacher);

-- ---------------------------------------------------------------------
-- TIME SLOTS (catálogo fijo, se inserta aquí y no en el seed)
-- ---------------------------------------------------------------------
CREATE TABLE time_slots (
    id_slot     INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slot_number INT  NOT NULL UNIQUE,
    start_time  TIME NOT NULL,
    end_time    TIME NOT NULL,
    CHECK (end_time > start_time)
);

INSERT INTO time_slots (slot_number, start_time, end_time) VALUES
    (1, '07:00', '08:00'), (2, '08:00', '09:00'), (3, '09:00', '10:00'),
    (4, '10:00', '11:00'), (5, '11:00', '12:00'), (6, '12:00', '13:00'),
    (7, '13:00', '14:00'), (8, '14:00', '15:00'), (9, '15:00', '16:00');

-- ---------------------------------------------------------------------
-- SCHEDULE (horario de grupo)
-- v1 tenía schedule_group y schedule_teacher por separado, duplicando la
-- información y sin forma de detectar choques. En v2 hay una sola tabla:
--   * id_teacher se copia de group_subject y la FK compuesta (con
--     ON UPDATE CASCADE) lo mantiene sincronizado si cambia el profesor.
--   * UNIQUE (day, id_slot, id_group)   -> el grupo no tiene dos clases a la vez
--   * UNIQUE (day, id_slot, id_teacher) -> el profesor no está en dos grupos a la vez
-- Para poder agendar, la materia del grupo debe tener profesor asignado.
-- ---------------------------------------------------------------------
CREATE TABLE schedule (
    id_schedule INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    day         weekday NOT NULL,
    id_slot     INT     NOT NULL REFERENCES time_slots (id_slot),
    id_group    INT     NOT NULL,
    id_subject  INT     NOT NULL,
    id_teacher  INT     NOT NULL,
    FOREIGN KEY (id_group, id_subject, id_teacher)
        REFERENCES group_subject (id_group, id_subject, id_teacher)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT schedule_group_clash   UNIQUE (day, id_slot, id_group),
    CONSTRAINT schedule_teacher_clash UNIQUE (day, id_slot, id_teacher)
);

-- Horario de profesor: vista derivada (sustituye a schedule_teacher de v1)
CREATE VIEW v_teacher_schedule AS
SELECT s.id_schedule, s.day, s.id_slot, ts.slot_number, ts.start_time, ts.end_time,
       s.id_teacher, s.id_group, g.group_name, s.id_subject, sub.subject_name
FROM schedule s
JOIN time_slots    ts  ON ts.id_slot     = s.id_slot
JOIN school_groups g   ON g.id_group     = s.id_group
JOIN subjects      sub ON sub.id_subject = s.id_subject;

-- ---------------------------------------------------------------------
-- ENROLLMENT: registro de aspirante aceptado -> alumno
-- ---------------------------------------------------------------------
CREATE TABLE enrollment (
    id_enrollment   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_aspirant     INT  NOT NULL UNIQUE REFERENCES aspirant_profile (id_aspirant),
    id_student      INT  NOT NULL UNIQUE REFERENCES student_profile (id_student),
    enrollment_date DATE NOT NULL DEFAULT CURRENT_DATE,
    approved_by     INT  NOT NULL REFERENCES admin_profile (id_admin)
);

-- ---------------------------------------------------------------------
-- GRADES
-- final_grade es columna generada (promedio de los 3 parciales), así ni
-- Java ni la web la calculan por su cuenta.
-- UNIQUE permite UPSERT (INSERT ... ON CONFLICT) al capturar.
-- La FK a group_subject impide calificar una materia que el grupo no cursa.
-- ---------------------------------------------------------------------
CREATE TABLE grades (
    id_grade    INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_student  INT NOT NULL REFERENCES student_profile (id_student),
    id_group    INT NOT NULL,
    id_subject  INT NOT NULL,
    parcial1    NUMERIC(4,2) CHECK (parcial1 BETWEEN 0 AND 10),
    parcial2    NUMERIC(4,2) CHECK (parcial2 BETWEEN 0 AND 10),
    parcial3    NUMERIC(4,2) CHECK (parcial3 BETWEEN 0 AND 10),
    final_grade NUMERIC(4,2) GENERATED ALWAYS AS (
        ROUND((parcial1 + parcial2 + parcial3) / 3, 2)
    ) STORED,
    UNIQUE (id_student, id_subject, id_group),
    FOREIGN KEY (id_group, id_subject) REFERENCES group_subject (id_group, id_subject)
);
CREATE INDEX idx_grades_group_subject ON grades (id_group, id_subject);
