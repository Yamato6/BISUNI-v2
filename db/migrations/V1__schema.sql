-- =====================================================================
-- BISUNI v2 - Esquema PostgreSQL
-- Migrado desde MySQL (v1: DB/BISUNI_DB.sql). Fuente única de verdad del
-- esquema; lo aplica Flyway y lo consumen desktop/ (JDBC) y web/ (Drizzle).
--
-- Fase 5 (producto): se reescribió este V1 una sola vez, antes de tener datos
-- reales, para agregar periodos escolares, matrícula y estatus del alumno,
-- actas, asistencia, avisos y bitácora. A partir de aquí, todo cambio va en
-- una migración nueva (V2__..., V3__...).
--
-- Variables de sesión que leen los triggers (set_config(..., true) = solo
-- dura la transacción):
--   bisuni.actor  id_user de quien hace el cambio (bitácora)
--   bisuni.seed   'on' únicamente en el seed de desarrollo: desactiva la
--                 bitácora y las guardas de periodo para cargar historial
-- =====================================================================

-- ---------------------------------------------------------------------
-- Tipos enumerados
-- ---------------------------------------------------------------------
CREATE TYPE user_role             AS ENUM ('Admin', 'Teacher', 'Student', 'Aspirant');
CREATE TYPE record_status         AS ENUM ('Active', 'Inactive');
CREATE TYPE aspirant_status       AS ENUM ('Pending', 'Accepted', 'Rejected');
CREATE TYPE weekday               AS ENUM ('Mon', 'Tue', 'Wed', 'Thu', 'Fri');
CREATE TYPE period_status         AS ENUM ('Planned', 'Active', 'Closed');
-- Leave = baja temporal, Dropped = baja definitiva, Graduated = egresado
CREATE TYPE student_status        AS ENUM ('Active', 'Leave', 'Dropped', 'Graduated');
CREATE TYPE attendance_status     AS ENUM ('Present', 'Absent', 'Late', 'Excused');
CREATE TYPE announcement_audience AS ENUM ('All', 'Career', 'Group');

-- Calificación mínima aprobatoria (la usan las vistas; las apps tienen la misma constante)
CREATE FUNCTION passing_grade() RETURNS NUMERIC
    LANGUAGE sql IMMUTABLE AS $$ SELECT 6.0 $$;

-- ¿Se está cargando el seed de desarrollo?
CREATE FUNCTION bisuni_seeding() RETURNS BOOLEAN
    LANGUAGE sql STABLE AS $$ SELECT coalesce(current_setting('bisuni.seed', true), '') = 'on' $$;

-- ---------------------------------------------------------------------
-- USERS
-- password_hash: bcrypt ($2a$/$2b$), verificable desde Java y Node.
-- email se guarda normalizado en minúsculas.
-- must_change_password: el admin restableció la contraseña con una temporal.
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id_user              INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username             VARCHAR(50)  NOT NULL UNIQUE,
    password_hash        VARCHAR(255) NOT NULL,
    email                VARCHAR(150) NOT NULL UNIQUE,
    first_name           VARCHAR(50)  NOT NULL,
    last_name            VARCHAR(50)  NOT NULL,
    role                 user_role     NOT NULL,
    status               record_status NOT NULL DEFAULT 'Active',
    must_change_password BOOLEAN       NOT NULL DEFAULT false,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT users_password_is_bcrypt CHECK (password_hash ~ '^\$2[aby]\$\d{2}\$.{53}$')
);

-- ---------------------------------------------------------------------
-- CAREER
-- code: clave corta que forma la matrícula (2026IT0001).
-- ---------------------------------------------------------------------
CREATE TABLE career (
    id_career       INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    career_name     VARCHAR(100)  NOT NULL UNIQUE,
    code            VARCHAR(4)    NOT NULL UNIQUE,
    total_semesters INT           NOT NULL DEFAULT 9,
    status          record_status NOT NULL DEFAULT 'Active',
    CONSTRAINT career_code_format CHECK (code ~ '^[A-Z]{2,4}$'),
    CONSTRAINT career_total_semesters CHECK (total_semesters BETWEEN 1 AND 12)
);

-- ---------------------------------------------------------------------
-- ACADEMIC PERIOD (ciclo escolar)
--   Planned -> Active -> Closed. Solo un periodo Active a la vez.
--   admissions_open: la web acepta registros de aspirantes para este periodo.
-- ---------------------------------------------------------------------
CREATE TABLE academic_period (
    id_period       INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(20)   NOT NULL UNIQUE,
    start_date      DATE          NOT NULL,
    end_date        DATE          NOT NULL,
    status          period_status NOT NULL DEFAULT 'Planned',
    admissions_open BOOLEAN       NOT NULL DEFAULT false,
    CONSTRAINT period_dates CHECK (end_date > start_date),
    CONSTRAINT period_closed_admissions CHECK (NOT (admissions_open AND status = 'Closed'))
);
CREATE UNIQUE INDEX period_single_active ON academic_period ((true)) WHERE status = 'Active';

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

-- id_period: convocatoria (periodo) a la que aplica el aspirante.
CREATE TABLE aspirant_profile (
    id_aspirant      INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_user          INT  NOT NULL UNIQUE REFERENCES users (id_user),
    curp             CHAR(18) NOT NULL UNIQUE,
    id_career        INT  NOT NULL REFERENCES career (id_career),
    id_period        INT  NOT NULL REFERENCES academic_period (id_period),
    application_date DATE NOT NULL DEFAULT CURRENT_DATE,
    status           aspirant_status NOT NULL DEFAULT 'Pending',
    -- Formato oficial de CURP: 4 letras, fecha AAMMDD, sexo, estado, 3 consonantes, homoclave, dígito
    CONSTRAINT aspirant_curp_format CHECK (curp ~ '^[A-Z]{4}[0-9]{6}[HMX][A-Z]{5}[A-Z0-9][0-9]$')
);

-- ---------------------------------------------------------------------
-- SUBJECTS
-- weekly_hours = bloques de horario por semana (el escritorio no deja pasarse).
-- ---------------------------------------------------------------------
CREATE TABLE subjects (
    id_subject   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subject_name VARCHAR(100)  NOT NULL,
    credits      INT           NOT NULL CHECK (credits > 0),
    semester     INT           NOT NULL CHECK (semester BETWEEN 1 AND 12),
    weekly_hours INT           NOT NULL CHECK (weekly_hours BETWEEN 1 AND 45),
    id_career    INT           NOT NULL REFERENCES career (id_career),
    status       record_status NOT NULL DEFAULT 'Active',
    UNIQUE (id_career, subject_name)
);

-- ---------------------------------------------------------------------
-- SCHOOL GROUPS
-- Cada grupo pertenece a un periodo; el nombre se repite entre periodos
-- (cada ciclo tiene su IT-1A), pero no dentro del mismo.
-- ---------------------------------------------------------------------
CREATE TABLE school_groups (
    id_group   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_name VARCHAR(50)   NOT NULL,
    id_period  INT           NOT NULL REFERENCES academic_period (id_period),
    id_career  INT           NOT NULL REFERENCES career (id_career),
    semester   INT           NOT NULL CHECK (semester BETWEEN 1 AND 12),
    id_tutor   INT           REFERENCES teacher_profile (id_teacher),
    status     record_status NOT NULL DEFAULT 'Active',
    CONSTRAINT school_groups_period_name UNIQUE (id_period, group_name),
    -- Permite FKs compuestas que garantizan "alumno y grupo de la misma carrera"
    UNIQUE (id_group, id_career)
);
CREATE INDEX idx_group_period ON school_groups (id_period);

-- ---------------------------------------------------------------------
-- STUDENT PROFILE
-- id_group es el grupo ACTUAL o, si su periodo ya cerró, el último que cursó
-- hasta que la reinscripción lo pase al siguiente (única fuente de
-- pertenencia; v1 tenía además group_student, que se desincronizaba). El
-- historial de grupos vive en grades.id_group, y cada grupo está ligado a su periodo.
-- El promedio ya no se guarda: es la vista v_student_gpa.
-- ---------------------------------------------------------------------
CREATE TABLE student_profile (
    id_student        INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_user           INT            NOT NULL UNIQUE REFERENCES users (id_user),
    enrollment_number VARCHAR(12)    NOT NULL UNIQUE,
    id_career         INT            NOT NULL REFERENCES career (id_career),
    semester          INT            NOT NULL DEFAULT 1 CHECK (semester BETWEEN 1 AND 12),
    academic_status   student_status NOT NULL DEFAULT 'Active',
    id_group          INT,
    FOREIGN KEY (id_group, id_career) REFERENCES school_groups (id_group, id_career),
    CONSTRAINT student_enrollment_number_format CHECK (enrollment_number ~ '^[0-9]{4}[A-Z]{2,4}[0-9]{4}$'),
    -- Solo un alumno activo puede estar en un grupo
    CONSTRAINT student_group_requires_active CHECK (id_group IS NULL OR academic_status = 'Active')
);
CREATE INDEX idx_student_group ON student_profile (id_group);

-- Siguiente matrícula {año}{clave de carrera}{consecutivo de 4}, p. ej. 2026IT0012.
-- El candado por prefijo evita que dos admisiones simultáneas tomen el mismo número.
CREATE FUNCTION next_enrollment_number(p_career INT, p_year INT) RETURNS VARCHAR
    LANGUAGE plpgsql AS $$
DECLARE
    prefix TEXT;
    seq    INT;
BEGIN
    SELECT p_year::text || code INTO prefix FROM career WHERE id_career = p_career;
    IF prefix IS NULL THEN
        RAISE EXCEPTION 'No existe la carrera %', p_career;
    END IF;
    PERFORM pg_advisory_xact_lock(hashtext('enrollment_number:' || prefix));
    SELECT coalesce(max(right(enrollment_number, 4)::int), 0) + 1 INTO seq
    FROM student_profile
    WHERE enrollment_number LIKE prefix || '____' AND right(enrollment_number, 4) ~ '^[0-9]{4}$';
    RETURN prefix || lpad(seq::text, 4, '0');
END $$;

-- ---------------------------------------------------------------------
-- TEACHER_SUBJECT: materias que un profesor está habilitado para impartir
-- ---------------------------------------------------------------------
CREATE TABLE teacher_subject (
    id_teacher INT NOT NULL REFERENCES teacher_profile (id_teacher),
    id_subject INT NOT NULL REFERENCES subjects (id_subject),
    PRIMARY KEY (id_teacher, id_subject)
);

-- ---------------------------------------------------------------------
-- GROUP_SUBJECT: materias que cursa un grupo, QUIÉN las imparte y su ACTA.
-- id_teacher debe ser un profesor habilitado (FK compuesta a teacher_subject).
-- grades_closed_at/by: acta cerrada; ya no se pueden modificar calificaciones.
-- ---------------------------------------------------------------------
CREATE TABLE group_subject (
    id_group         INT NOT NULL REFERENCES school_groups (id_group),
    id_subject       INT NOT NULL REFERENCES subjects (id_subject),
    id_teacher       INT,
    grades_closed_at TIMESTAMPTZ,
    grades_closed_by INT REFERENCES users (id_user),
    PRIMARY KEY (id_group, id_subject),
    UNIQUE (id_group, id_subject, id_teacher),
    FOREIGN KEY (id_teacher, id_subject) REFERENCES teacher_subject (id_teacher, id_subject),
    CONSTRAINT group_subject_closed_by CHECK ((grades_closed_at IS NULL) = (grades_closed_by IS NULL))
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
--   * id_teacher se copia de group_subject y la FK compuesta (con
--     ON UPDATE CASCADE) lo mantiene sincronizado si cambia el profesor.
--   * UNIQUE (day, id_slot, id_group)   -> el grupo no tiene dos clases a la vez
--   * UNIQUE (day, id_slot, id_teacher) -> el profesor no está en dos grupos a la vez
-- Los grupos de periodos distintos no coinciden en el tiempo, pero un
-- profesor tampoco puede tener dos bloques iguales entre periodos: el
-- escritorio solo muestra y edita horarios del periodo elegido, y el horario
-- de un periodo cerrado queda como historial.
-- ---------------------------------------------------------------------
CREATE TABLE schedule (
    id_schedule INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    day         weekday NOT NULL,
    id_slot     INT     NOT NULL REFERENCES time_slots (id_slot),
    id_group    INT     NOT NULL,
    id_subject  INT     NOT NULL,
    id_teacher  INT     NOT NULL,
    id_period   INT     NOT NULL REFERENCES academic_period (id_period),
    FOREIGN KEY (id_group, id_subject, id_teacher)
        REFERENCES group_subject (id_group, id_subject, id_teacher)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT schedule_group_clash   UNIQUE (day, id_slot, id_group),
    CONSTRAINT schedule_teacher_clash UNIQUE (id_period, day, id_slot, id_teacher)
);

-- schedule.id_period se copia del grupo (para que el choque de profesor sea por periodo)
CREATE FUNCTION schedule_set_period() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    SELECT id_period INTO NEW.id_period FROM school_groups WHERE id_group = NEW.id_group;
    RETURN NEW;
END $$;
CREATE TRIGGER schedule_set_period BEFORE INSERT OR UPDATE OF id_group ON schedule
    FOR EACH ROW EXECUTE FUNCTION schedule_set_period();

-- Horario de profesor: vista derivada (sustituye a schedule_teacher de v1)
CREATE VIEW v_teacher_schedule AS
SELECT s.id_schedule, s.day, s.id_slot, ts.slot_number, ts.start_time, ts.end_time,
       s.id_teacher, s.id_group, g.group_name, s.id_subject, sub.subject_name, s.id_period
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
-- final_grade es columna generada (promedio de los 3 parciales).
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

-- ---------------------------------------------------------------------
-- ATTENDANCE: una marca por alumno, clase (grupo + materia) y fecha.
-- ---------------------------------------------------------------------
CREATE TABLE attendance (
    id_attendance INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_student    INT               NOT NULL REFERENCES student_profile (id_student),
    id_group      INT               NOT NULL,
    id_subject    INT               NOT NULL,
    class_date    DATE              NOT NULL,
    status        attendance_status NOT NULL,
    recorded_by   INT               REFERENCES users (id_user),
    recorded_at   TIMESTAMPTZ       NOT NULL DEFAULT now(),
    CONSTRAINT attendance_unique UNIQUE (id_student, id_group, id_subject, class_date),
    FOREIGN KEY (id_group, id_subject) REFERENCES group_subject (id_group, id_subject)
);
CREATE INDEX idx_attendance_class ON attendance (id_group, id_subject, class_date);

-- ---------------------------------------------------------------------
-- ANNOUNCEMENT: avisos para todos, una carrera o un grupo.
-- ---------------------------------------------------------------------
CREATE TABLE announcement (
    id_announcement INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title           VARCHAR(120)          NOT NULL,
    body            TEXT                  NOT NULL,
    audience        announcement_audience NOT NULL,
    id_career       INT REFERENCES career (id_career),
    id_group        INT REFERENCES school_groups (id_group) ON DELETE CASCADE,
    author_user     INT NOT NULL REFERENCES users (id_user),
    published_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    CONSTRAINT announcement_audience_target CHECK (
        (audience = 'All'    AND id_career IS NULL     AND id_group IS NULL) OR
        (audience = 'Career' AND id_career IS NOT NULL AND id_group IS NULL) OR
        (audience = 'Group'  AND id_group  IS NOT NULL AND id_career IS NULL)),
    CONSTRAINT announcement_expiry CHECK (expires_at IS NULL OR expires_at > published_at),
    CONSTRAINT announcement_not_blank CHECK (length(trim(title)) > 0 AND length(trim(body)) > 0)
);
CREATE INDEX idx_announcement_published ON announcement (published_at DESC);

-- ---------------------------------------------------------------------
-- GUARDAS DE PERIODO (triggers). Usan RAISE ... CONSTRAINT para que las
-- apps traduzcan el error por nombre, igual que una restricción normal.
-- ---------------------------------------------------------------------

-- Calificaciones: solo en el periodo activo y con el acta abierta.
CREATE FUNCTION grades_guard() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
    r      RECORD;
    st     period_status;
    closed TIMESTAMPTZ;
BEGIN
    IF bisuni_seeding() THEN
        RETURN coalesce(NEW, OLD);
    END IF;
    r := coalesce(NEW, OLD);
    SELECT p.status, gs.grades_closed_at INTO st, closed
    FROM group_subject gs
    JOIN school_groups g    ON g.id_group  = gs.id_group
    JOIN academic_period p  ON p.id_period = g.id_period
    WHERE gs.id_group = r.id_group AND gs.id_subject = r.id_subject;
    IF st IS DISTINCT FROM 'Active' THEN
        RAISE EXCEPTION 'Solo se capturan calificaciones del periodo activo'
            USING ERRCODE = 'check_violation', CONSTRAINT = 'grades_period_not_active';
    END IF;
    IF closed IS NOT NULL THEN
        RAISE EXCEPTION 'El acta de esta materia ya está cerrada'
            USING ERRCODE = 'check_violation', CONSTRAINT = 'grades_acta_closed';
    END IF;
    RETURN coalesce(NEW, OLD);
END $$;
CREATE TRIGGER grades_guard BEFORE INSERT OR UPDATE OR DELETE ON grades
    FOR EACH ROW EXECUTE FUNCTION grades_guard();

-- Asistencia: solo en el periodo activo y dentro de sus fechas.
CREATE FUNCTION attendance_guard() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
    r RECORD;
    p RECORD;
BEGIN
    IF bisuni_seeding() THEN
        RETURN coalesce(NEW, OLD);
    END IF;
    r := coalesce(NEW, OLD);
    SELECT ap.status, ap.start_date, ap.end_date INTO p
    FROM school_groups g JOIN academic_period ap ON ap.id_period = g.id_period
    WHERE g.id_group = r.id_group;
    IF p.status IS DISTINCT FROM 'Active' THEN
        RAISE EXCEPTION 'Solo se registra asistencia del periodo activo'
            USING ERRCODE = 'check_violation', CONSTRAINT = 'attendance_period_not_active';
    END IF;
    IF r.class_date NOT BETWEEN p.start_date AND p.end_date THEN
        RAISE EXCEPTION 'La fecha está fuera del periodo'
            USING ERRCODE = 'check_violation', CONSTRAINT = 'attendance_date_out_of_period';
    END IF;
    RETURN coalesce(NEW, OLD);
END $$;
CREATE TRIGGER attendance_guard BEFORE INSERT OR UPDATE OR DELETE ON attendance
    FOR EACH ROW EXECUTE FUNCTION attendance_guard();

-- Alumnos: no se puede asignar a un grupo de un periodo cerrado.
CREATE FUNCTION student_group_guard() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.id_group IS NOT NULL AND NOT bisuni_seeding()
       AND (TG_OP = 'INSERT' OR NEW.id_group IS DISTINCT FROM OLD.id_group)
       AND EXISTS (SELECT 1 FROM school_groups g JOIN academic_period p ON p.id_period = g.id_period
                   WHERE g.id_group = NEW.id_group AND p.status = 'Closed') THEN
        RAISE EXCEPTION 'El grupo pertenece a un periodo cerrado'
            USING ERRCODE = 'check_violation', CONSTRAINT = 'student_group_period_closed';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER student_group_guard BEFORE INSERT OR UPDATE OF id_group ON student_profile
    FOR EACH ROW EXECUTE FUNCTION student_group_guard();

-- ---------------------------------------------------------------------
-- AUDIT LOG (bitácora)
-- La llenan triggers, así ningún camino (escritorio, web o SQL directo) se
-- queda sin registro. actor_user sale de bisuni.actor (NULL = sin sesión).
-- details: {"campo": {"antes": ..., "despues": ...}} solo con lo que cambió.
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
    id_audit   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_user INT REFERENCES users (id_user),
    action     VARCHAR(10) NOT NULL,   -- insert | update | delete
    entity     VARCHAR(40) NOT NULL,
    entity_id  VARCHAR(60) NOT NULL,
    details    JSONB       NOT NULL DEFAULT '{}'
);
CREATE INDEX idx_audit_at ON audit_log (at DESC);
CREATE INDEX idx_audit_entity ON audit_log (entity, entity_id);

-- Argumentos: entidad, columnas a vigilar, columnas que forman el id.
CREATE FUNCTION audit_row() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
    entity  TEXT   := TG_ARGV[0];
    watched TEXT[] := TG_ARGV[1]::text[];
    keys    TEXT[] := TG_ARGV[2]::text[];
    o       JSONB  := CASE WHEN TG_OP <> 'INSERT' THEN to_jsonb(OLD) END;
    n       JSONB  := CASE WHEN TG_OP <> 'DELETE' THEN to_jsonb(NEW) END;
    d       JSONB;
    id      TEXT;
    actor   INT;
BEGIN
    IF bisuni_seeding() THEN
        RETURN NULL;
    END IF;
    SELECT coalesce(jsonb_object_agg(k, jsonb_build_object('antes', o -> k, 'despues', n -> k)), '{}')
    INTO d
    FROM unnest(watched) AS k
    WHERE (o -> k) IS DISTINCT FROM (n -> k);
    IF TG_OP = 'UPDATE' AND d = '{}' THEN
        RETURN NULL;
    END IF;
    -- Nunca guardar hashes de contraseña
    IF d ? 'password_hash' THEN
        d := (d - 'password_hash') || '{"password_hash": "cambiada"}';
    END IF;
    SELECT string_agg(coalesce(n, o) ->> c, ':' ORDER BY ord) INTO id
    FROM unnest(keys) WITH ORDINALITY AS t(c, ord);
    actor := nullif(current_setting('bisuni.actor', true), '')::int;
    INSERT INTO audit_log (actor_user, action, entity, entity_id, details)
    VALUES (actor, lower(TG_OP), entity, id, d);
    RETURN NULL;
END $$;

CREATE TRIGGER audit_users AFTER INSERT OR UPDATE ON users FOR EACH ROW EXECUTE FUNCTION audit_row(
    'users', '{email,first_name,last_name,role,status,password_hash,must_change_password}', '{id_user}');
CREATE TRIGGER audit_career AFTER INSERT OR UPDATE ON career FOR EACH ROW EXECUTE FUNCTION audit_row(
    'career', '{career_name,code,total_semesters,status}', '{id_career}');
CREATE TRIGGER audit_period AFTER INSERT OR UPDATE ON academic_period FOR EACH ROW EXECUTE FUNCTION audit_row(
    'academic_period', '{name,start_date,end_date,status,admissions_open}', '{id_period}');
CREATE TRIGGER audit_aspirant AFTER UPDATE ON aspirant_profile FOR EACH ROW EXECUTE FUNCTION audit_row(
    'aspirant_profile', '{status,id_career}', '{id_aspirant}');
CREATE TRIGGER audit_student AFTER INSERT OR UPDATE ON student_profile FOR EACH ROW EXECUTE FUNCTION audit_row(
    'student_profile', '{enrollment_number,id_career,semester,academic_status,id_group}', '{id_student}');
CREATE TRIGGER audit_group_subject AFTER INSERT OR UPDATE OR DELETE ON group_subject FOR EACH ROW EXECUTE FUNCTION audit_row(
    'group_subject', '{id_teacher,grades_closed_at}', '{id_group,id_subject}');
CREATE TRIGGER audit_grades AFTER INSERT OR UPDATE OR DELETE ON grades FOR EACH ROW EXECUTE FUNCTION audit_row(
    'grades', '{parcial1,parcial2,parcial3}', '{id_student,id_group,id_subject}');

-- ---------------------------------------------------------------------
-- VISTAS de consulta (las comparten escritorio y web)
-- ---------------------------------------------------------------------

-- Promedio general: materias con calificación final (los 3 parciales).
CREATE VIEW v_student_gpa AS
SELECT sp.id_student,
       round(avg(g.final_grade), 2) AS gpa,
       count(g.final_grade)::int    AS graded_subjects
FROM student_profile sp
LEFT JOIN grades g ON g.id_student = sp.id_student AND g.final_grade IS NOT NULL
GROUP BY sp.id_student;

-- Kárdex: cada materia cursada (con calificación) o en curso (grupo actual).
-- result: Passed | Failed | InProgress. Una materia es definitiva cuando su
-- acta se cerró o su periodo terminó.
CREATE VIEW v_kardex AS
WITH taken AS (
    SELECT id_student, id_group, id_subject FROM grades
    UNION
    SELECT sp.id_student, gs.id_group, gs.id_subject
    FROM student_profile sp
    JOIN group_subject gs ON gs.id_group = sp.id_group
)
SELECT t.id_student, p.id_period, p.name AS period_name, p.start_date AS period_start, p.status AS period_status,
       g.id_group, g.group_name, g.semester AS group_semester,
       s.id_subject, s.subject_name, s.credits, s.semester AS subject_semester,
       gr.parcial1, gr.parcial2, gr.parcial3, gr.final_grade,
       (gs.grades_closed_at IS NOT NULL OR p.status = 'Closed') AS is_final,
       CASE
           WHEN gs.grades_closed_at IS NULL AND p.status <> 'Closed' THEN 'InProgress'
           WHEN gr.final_grade >= passing_grade() THEN 'Passed'
           ELSE 'Failed'
       END AS result
FROM taken t
JOIN school_groups g    ON g.id_group  = t.id_group
JOIN academic_period p  ON p.id_period = g.id_period
JOIN subjects s         ON s.id_subject = t.id_subject
JOIN group_subject gs   ON gs.id_group = t.id_group AND gs.id_subject = t.id_subject
LEFT JOIN grades gr     ON gr.id_student = t.id_student AND gr.id_group = t.id_group AND gr.id_subject = t.id_subject;

-- Resumen de asistencia por alumno y clase. Retardos y justificadas no son falta.
CREATE VIEW v_attendance_summary AS
SELECT id_student, id_group, id_subject,
       count(*)::int                                     AS sessions,
       (count(*) FILTER (WHERE status = 'Absent'))::int  AS absences,
       (count(*) FILTER (WHERE status = 'Late'))::int    AS lates,
       (count(*) FILTER (WHERE status = 'Excused'))::int AS excused,
       round(100.0 * count(*) FILTER (WHERE status = 'Absent') / count(*), 1) AS absence_pct
FROM attendance
GROUP BY id_student, id_group, id_subject;
