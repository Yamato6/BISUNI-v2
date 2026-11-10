-- =====================================================================
-- BISUNI v2 - Datos de prueba (SOLO DESARROLLO)
-- Portado de v1 (DB/BISUNI_DB_DATA.sql) y ampliado en la Fase 5. Migración
-- "repeatable" de Flyway: se vuelve a ejecutar cuando cambia este archivo,
-- por eso empieza con TRUNCATE.
--
-- Todas las cuentas usan la contraseña: password123  (hash bcrypt abajo)
--
-- Escenario:
--   * Periodo 2026-1 (cerrado): la generación de IT de enero cursó IT-1A.
--   * Periodo 2026-2 (activo, convocatoria abierta): IT-1A, IT-1B, BA-1A,
--     IM-1A e IP-1A de primer semestre, y IT-2A con la generación de enero.
--   * Las horas del horario coinciden con weekly_hours de cada materia.
--   * Hay reprobadas en el historial, una baja definitiva, asistencia con un
--     alumno en riesgo (student053) y avisos de ejemplo.
--
-- bisuni.seed = 'on' desactiva la bitácora y las guardas de periodo mientras
-- se carga el historial (solo dura esta transacción).
-- =====================================================================

SELECT set_config('bisuni.seed', 'on', true);

TRUNCATE audit_log, announcement, attendance, grades, enrollment, schedule, group_subject,
         teacher_subject, student_profile, school_groups, subjects, aspirant_profile,
         teacher_profile, admin_profile, academic_period, career, users
RESTART IDENTITY CASCADE;

-- ---------------------------------------------------------------------
-- CAREERS (el plan de estudios del seed llega a 4 semestres)
-- ---------------------------------------------------------------------
INSERT INTO career (id_career, career_name, code, total_semesters) OVERRIDING SYSTEM VALUE VALUES
    (1, 'Information Technology',  'IT', 4),
    (2, 'Business Administration', 'BA', 4),
    (3, 'Industrial Maintenance',  'IM', 4),
    (4, 'Industrial Processes',    'IP', 4);

-- ---------------------------------------------------------------------
-- ACADEMIC PERIODS
-- ---------------------------------------------------------------------
INSERT INTO academic_period (id_period, name, start_date, end_date, status, admissions_open) OVERRIDING SYSTEM VALUE VALUES
    (1, '2026-1', '2026-01-12', '2026-06-26', 'Closed', false),
    (2, '2026-2', '2026-08-17', '2026-12-18', 'Active', true);

-- ---------------------------------------------------------------------
-- USERS
-- ---------------------------------------------------------------------
INSERT INTO users (id_user, username, password_hash, email, first_name, last_name, role)
OVERRIDING SYSTEM VALUE
SELECT v.id, v.username, '$2b$10$dBVhXUfjbLZ./s1iqnh7Z.QTdJWw1Umm1yoO2LWON8mQ71.PIQvSO',
       v.email, v.first_name, v.last_name, v.role::user_role
FROM (VALUES
    -- Admins
    ( 1, 'admin.master',  'admin.master@bisuni.edu',     'Antonio',  'Ramírez',  'Admin'),
    ( 2, 'admin.control', 'control.school@bisuni.edu',   'Beatriz',  'Cortés',   'Admin'),
    -- Teachers
    ( 3, 'jperez',        'juan.perez@bisuni.edu',       'Juan',     'Pérez',    'Teacher'),
    ( 4, 'lmartinez',     'laura.martinez@bisuni.edu',   'Laura',    'Martínez', 'Teacher'),
    ( 5, 'rlopez',        'ricardo.lopez@bisuni.edu',    'Ricardo',  'López',    'Teacher'),
    ( 6, 'mgarcia',       'maria.garcia@bisuni.edu',     'María',    'García',   'Teacher'),
    ( 7, 'ecastillo',     'ernesto.castillo@bisuni.edu', 'Ernesto',  'Castillo', 'Teacher'),
    ( 8, 'storres',       'silvia.torres@bisuni.edu',    'Silvia',   'Torres',   'Teacher'),
    ( 9, 'rdiaz',         'roberto.diaz@bisuni.edu',     'Roberto',  'Díaz',     'Teacher'),
    (10, 'egarcia',       'eduardo.garcia@bisuni.edu',   'Eduardo',  'García',   'Teacher'),
    (11, 'jruiz',         'jorge.ruiz@bisuni.edu',       'Jorge',    'Ruiz',     'Teacher'),
    (12, 'apan',          'ana.pantoja@bisuni.edu',      'Ana',      'Pantoja',  'Teacher'),
    -- Students (50)
    (13, 'student001', 'student001@bisuni.edu', 'Carlos',    'Hernández', 'Student'),
    (14, 'student002', 'student002@bisuni.edu', 'Daniela',   'Torres',    'Student'),
    (15, 'student003', 'student003@bisuni.edu', 'Miguel',    'Serrano',   'Student'),
    (16, 'student004', 'student004@bisuni.edu', 'Valeria',   'Morales',   'Student'),
    (17, 'student005', 'student005@bisuni.edu', 'Javier',    'Soto',      'Student'),
    (18, 'student006', 'student006@bisuni.edu', 'Fernanda',  'Reyes',     'Student'),
    (19, 'student007', 'student007@bisuni.edu', 'Luis',      'Romero',    'Student'),
    (20, 'student008', 'student008@bisuni.edu', 'Andrea',    'Vega',      'Student'),
    (21, 'student009', 'student009@bisuni.edu', 'Santiago',  'Flores',    'Student'),
    (22, 'student010', 'student010@bisuni.edu', 'Mariana',   'Luna',      'Student'),
    (23, 'student011', 'student011@bisuni.edu', 'Diego',     'Navarro',   'Student'),
    (24, 'student012', 'student012@bisuni.edu', 'Paola',     'Castro',    'Student'),
    (25, 'student013', 'student013@bisuni.edu', 'Héctor',    'Campos',    'Student'),
    (26, 'student014', 'student014@bisuni.edu', 'Jimena',    'Rubio',     'Student'),
    (27, 'student015', 'student015@bisuni.edu', 'José',      'Aguilar',   'Student'),
    (28, 'student016', 'student016@bisuni.edu', 'Isabella',  'Salazar',   'Student'),
    (29, 'student017', 'student017@bisuni.edu', 'Mauricio',  'Palacios',  'Student'),
    (30, 'student018', 'student018@bisuni.edu', 'Alejandra', 'Mendoza',   'Student'),
    (31, 'student019', 'student019@bisuni.edu', 'Emilio',    'Peralta',   'Student'),
    (32, 'student020', 'student020@bisuni.edu', 'Natalia',   'del Río',   'Student'),
    (33, 'student021', 'student021@bisuni.edu', 'Ángel',     'Solís',     'Student'),
    (34, 'student022', 'student022@bisuni.edu', 'Carolina',  'Montes',    'Student'),
    (35, 'student023', 'student023@bisuni.edu', 'Iván',      'Franco',    'Student'),
    (36, 'student024', 'student024@bisuni.edu', 'Ximena',    'Acosta',    'Student'),
    (37, 'student025', 'student025@bisuni.edu', 'Óscar',     'León',      'Student'),
    (38, 'student026', 'student026@bisuni.edu', 'Sofía',     'Rivas',     'Student'),
    (39, 'student027', 'student027@bisuni.edu', 'Marco',     'Barajas',   'Student'),
    (40, 'student028', 'student028@bisuni.edu', 'Camila',    'Alfaro',    'Student'),
    (41, 'student029', 'student029@bisuni.edu', 'Kevin',     'Peña',      'Student'),
    (42, 'student030', 'student030@bisuni.edu', 'Sara',      'Villalobos','Student'),
    (43, 'student031', 'student031@bisuni.edu', 'Bruno',     'Esparza',   'Student'),
    (44, 'student032', 'student032@bisuni.edu', 'Ariana',    'Olivares',  'Student'),
    (45, 'student033', 'student033@bisuni.edu', 'Mateo',     'Gómez',     'Student'),
    (46, 'student034', 'student034@bisuni.edu', 'Renata',    'Loera',     'Student'),
    (47, 'student035', 'student035@bisuni.edu', 'Adrián',    'Zamora',    'Student'),
    (48, 'student036', 'student036@bisuni.edu', 'Liliana',   'Paredes',   'Student'),
    (49, 'student037', 'student037@bisuni.edu', 'Pablo',     'Carrillo',  'Student'),
    (50, 'student038', 'student038@bisuni.edu', 'Regina',    'Rojas',     'Student'),
    (51, 'student039', 'student039@bisuni.edu', 'Tomás',     'Castañeda', 'Student'),
    (52, 'student040', 'student040@bisuni.edu', 'Victoria',  'Rosales',   'Student'),
    (53, 'student041', 'student041@bisuni.edu', 'Arturo',    'Galván',    'Student'),
    (54, 'student042', 'student042@bisuni.edu', 'Nicole',    'Bermúdez',  'Student'),
    (55, 'student043', 'student043@bisuni.edu', 'Sebastián', 'Miranda',   'Student'),
    (56, 'student044', 'student044@bisuni.edu', 'Lucía',     'Zarate',    'Student'),
    (57, 'student045', 'student045@bisuni.edu', 'Hugo',      'Salgado',   'Student'),
    (58, 'student046', 'student046@bisuni.edu', 'Sandra',    'Delgado',   'Student'),
    (59, 'student047', 'student047@bisuni.edu', 'Emmanuel',  'Aranda',    'Student'),
    (60, 'student048', 'student048@bisuni.edu', 'Patricia',  'Molina',    'Student'),
    (61, 'student049', 'student049@bisuni.edu', 'Erick',     'Beltrán',   'Student'),
    (62, 'student050', 'student050@bisuni.edu', 'Julia',     'Nieves',    'Student'),
    -- Aspirants (63-66 pendientes; 67-68 ya aceptados -> rol Student)
    (63, 'aspirant01', 'aspirant01@bisuni.edu', 'Andrea',   'Belmont',  'Aspirant'),
    (64, 'aspirant02', 'aspirant02@bisuni.edu', 'Samuel',   'Ibarra',   'Aspirant'),
    (65, 'aspirant03', 'aspirant03@bisuni.edu', 'Michelle', 'Treviño',  'Aspirant'),
    (66, 'aspirant04', 'aspirant04@bisuni.edu', 'César',    'Mejía',    'Aspirant'),
    (67, 'aspirant05', 'aspirant05@bisuni.edu', 'Claudia',  'Bautista', 'Student'),
    (68, 'aspirant06', 'aspirant06@bisuni.edu', 'Fabián',   'Vallejo',  'Student'),
    -- Generación 2026-1 de IT (historial): cursaron IT-1A en 2026-1 y hoy están en IT-2A
    (69, 'student051', 'student051@bisuni.edu', 'Gabriel',   'Ortega',    'Student'),
    (70, 'student052', 'student052@bisuni.edu', 'Elena',     'Cabrera',   'Student'),
    (71, 'student053', 'student053@bisuni.edu', 'Rodrigo',   'Fuentes',   'Student'),
    (72, 'student054', 'student054@bisuni.edu', 'Montserrat','Ibarra',    'Student'),
    (73, 'student055', 'student055@bisuni.edu', 'Alan',      'Quintero',  'Student'),
    (74, 'student056', 'student056@bisuni.edu', 'Paulina',   'Duarte',    'Student'),
    (75, 'student057', 'student057@bisuni.edu', 'Ricardo',   'Varela',    'Student'),
    (76, 'student058', 'student058@bisuni.edu', 'Daniela',   'Lozano',    'Student'),
    (77, 'student059', 'student059@bisuni.edu', 'Fernando',  'Cervantes', 'Student'),
    (78, 'student060', 'student060@bisuni.edu', 'Mónica',    'Guerrero',  'Student')
) AS v (id, username, email, first_name, last_name, role);

-- ---------------------------------------------------------------------
-- PROFILES
-- ---------------------------------------------------------------------
INSERT INTO admin_profile (id_admin, id_user, position, last_access) OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 'Head of Academic Services',  now()),
    (2, 2, 'School Control Coordinator', now());

INSERT INTO teacher_profile (id_teacher, id_user, specialty, phone) OVERRIDING SYSTEM VALUE VALUES
    ( 1,  3, 'Software Engineering',   '+52-449-555-1000'),
    ( 2,  4, 'Databases',              '+52-449-555-1001'),
    ( 3,  5, 'Computer Networks',      '+52-449-555-1002'),
    ( 4,  6, 'Mathematics',            '+52-449-555-1003'),
    ( 5,  7, 'Physics',                '+52-449-555-1004'),
    ( 6,  8, 'Accounting',             '+52-449-555-1005'),
    ( 7,  9, 'Industrial Maintenance', '+52-449-555-1006'),
    ( 8, 10, 'Manufacturing',          '+52-449-555-1007'),
    ( 9, 11, 'Quality Systems',        '+52-449-555-1008'),
    (10, 12, 'Project Management',     '+52-449-555-1009');

-- ---------------------------------------------------------------------
-- GROUPS
-- ---------------------------------------------------------------------
INSERT INTO school_groups (id_group, group_name, id_period, id_career, semester, id_tutor) OVERRIDING SYSTEM VALUE VALUES
    -- 2026-2 (activo)
    (1, 'IT-1A', 2, 1, 1, 1),
    (2, 'IT-1B', 2, 1, 1, 2),
    (3, 'BA-1A', 2, 2, 1, 6),
    (4, 'IM-1A', 2, 3, 1, 7),
    (5, 'IP-1A', 2, 4, 1, 8),
    (7, 'IT-2A', 2, 1, 2, 2),
    -- 2026-1 (cerrado)
    (6, 'IT-1A', 1, 1, 1, 1);

-- ---------------------------------------------------------------------
-- STUDENT PROFILES
--   1-50  (usuarios 13-62): primer semestre, 10 por grupo (grupos 1-5)
--   51-52 (usuarios 67-68): aspirantes aceptados, aún sin grupo
--   53-62 (usuarios 69-78): generación 2026-1, hoy en IT-2A; el 62 se dio de baja
-- Matrícula {año}{clave}{consecutivo}: la generación de enero va primero.
-- ---------------------------------------------------------------------
INSERT INTO student_profile (id_student, id_user, enrollment_number, id_career, semester, academic_status, id_group)
OVERRIDING SYSTEM VALUE
SELECT st.id_student, st.id_user,
       '2026' || c.code || lpad((row_number() OVER (PARTITION BY st.id_career ORDER BY st.cohort, st.id_student))::text, 4, '0'),
       st.id_career, st.semester, st.status::student_status, st.id_group
FROM (
    SELECT i, 12 + i,
           CASE (i - 1) / 10 WHEN 0 THEN 1 WHEN 1 THEN 1 WHEN 2 THEN 2 WHEN 3 THEN 3 ELSE 4 END,
           1, 'Active', (i - 1) / 10 + 1, 2
    FROM generate_series(1, 50) AS i
    UNION ALL
    VALUES (51, 67, 1, 1, 'Active', NULL::int, 3),
           (52, 68, 2, 1, 'Active', NULL::int, 3)
    UNION ALL
    SELECT i, 16 + i, 1, 2,
           CASE WHEN i = 62 THEN 'Dropped' ELSE 'Active' END,
           CASE WHEN i = 62 THEN NULL ELSE 7 END, 1
    FROM generate_series(53, 62) AS i
) AS st (id_student, id_user, id_career, semester, status, id_group, cohort)
JOIN career c ON c.id_career = st.id_career;

-- ---------------------------------------------------------------------
-- SUBJECTS (32). weekly_hours = bloques por semana en el horario.
-- ---------------------------------------------------------------------
INSERT INTO subjects (id_subject, subject_name, credits, semester, weekly_hours, id_career) OVERRIDING SYSTEM VALUE VALUES
    -- IT
    ( 1, 'Programming Fundamentals',        4, 1, 5, 1),
    ( 2, 'Discrete Mathematics',            4, 1, 4, 1),
    ( 3, 'Database Basics',                 4, 2, 4, 1),
    ( 4, 'Web Development I',               3, 2, 4, 1),
    ( 5, 'Object-Oriented Programming',     5, 3, 5, 1),
    ( 6, 'Networks I',                      4, 3, 4, 1),
    ( 7, 'Software Engineering I',          4, 4, 4, 1),
    ( 8, 'Operating Systems',               4, 4, 4, 1),
    -- BA
    ( 9, 'Introduction to Business',        3, 1, 4, 2),
    (10, 'Basic Accounting',                4, 1, 4, 2),
    (11, 'Business Mathematics',            4, 2, 4, 2),
    (12, 'Marketing Fundamentals',          3, 2, 4, 2),
    (13, 'Financial Analysis',              4, 3, 4, 2),
    (14, 'Human Resources Management',      3, 3, 3, 2),
    (15, 'Business Law',                    3, 4, 3, 2),
    (16, 'Project Management',              4, 4, 4, 2),
    -- IM
    (17, 'Technical Drawing',               3, 1, 3, 3),
    (18, 'Electricity and Electronics',     4, 1, 4, 3),
    (19, 'Industrial Safety',               3, 2, 3, 3),
    (20, 'Mechanical Maintenance I',        4, 2, 4, 3),
    (21, 'Hydraulic and Pneumatic Systems', 4, 3, 4, 3),
    (22, 'Predictive Maintenance',          3, 3, 3, 3),
    (23, 'Maintenance Management',          4, 4, 4, 3),
    (24, 'Automation Basics',               4, 4, 4, 3),
    -- IP
    (25, 'Manufacturing Processes I',       4, 1, 4, 4),
    (26, 'Materials Science',               4, 1, 4, 4),
    (27, 'Metrology',                       3, 2, 3, 4),
    (28, 'Quality Control I',               4, 2, 4, 4),
    (29, 'Manufacturing Processes II',      4, 3, 4, 4),
    (30, 'Production Planning',             3, 3, 3, 4),
    (31, 'Quality Systems',                 4, 4, 4, 4),
    (32, 'Lean Manufacturing',              3, 4, 3, 4);

-- ---------------------------------------------------------------------
-- TEACHER_SUBJECT (habilitación)
-- ---------------------------------------------------------------------
INSERT INTO teacher_subject (id_teacher, id_subject) VALUES
    (1,  1),   -- Juan:    Programming Fundamentals
    (4,  2),   -- María:   Discrete Mathematics
    (2,  3),   -- Laura:   Database Basics
    (3,  4),   -- Ricardo: Web Development I
    (6,  9),   -- Silvia:  Introduction to Business
    (6, 10),   -- Silvia:  Basic Accounting
    (7, 17),   -- Roberto: Technical Drawing
    (7, 18),   -- Roberto: Electricity and Electronics
    (8, 25),   -- Eduardo: Manufacturing Processes I
    (8, 26);   -- Eduardo: Materials Science

-- ---------------------------------------------------------------------
-- GROUP_SUBJECT (con profesor asignado). Las actas de 2026-1 están cerradas.
-- ---------------------------------------------------------------------
INSERT INTO group_subject (id_group, id_subject, id_teacher, grades_closed_at, grades_closed_by) VALUES
    (1,  1, 1, NULL, NULL), (1,  2, 4, NULL, NULL),
    (2,  1, 1, NULL, NULL), (2,  2, 4, NULL, NULL),
    (3,  9, 6, NULL, NULL), (3, 10, 6, NULL, NULL),
    (4, 17, 7, NULL, NULL), (4, 18, 7, NULL, NULL),
    (5, 25, 8, NULL, NULL), (5, 26, 8, NULL, NULL),
    (7,  3, 2, NULL, NULL), (7,  4, 3, NULL, NULL),
    (6,  1, 1, '2026-06-19 13:00-06', 3), (6,  2, 4, '2026-06-22 10:30-06', 6);

-- ---------------------------------------------------------------------
-- ASPIRANTS (CURPs ficticias con formato válido), convocatoria 2026-2
-- ---------------------------------------------------------------------
INSERT INTO aspirant_profile (id_aspirant, id_user, curp, id_career, id_period, application_date, status) OVERRIDING SYSTEM VALUE VALUES
    (1, 63, 'BEXA070312MASLND01', 1, 2, '2026-07-10', 'Pending'),
    (2, 64, 'IAXS061120HASBMM02', 2, 2, '2026-07-11', 'Pending'),
    (3, 65, 'TEXM070503MASRCH03', 3, 2, '2026-07-12', 'Pending'),
    (4, 66, 'MEXC060915HASJSR04', 4, 2, '2026-07-13', 'Pending'),
    (5, 67, 'BAXC070101MASTLL05', 1, 2, '2026-07-14', 'Accepted'),
    (6, 68, 'VAXF060722HASLBB06', 2, 2, '2026-07-15', 'Accepted');

INSERT INTO enrollment (id_aspirant, id_student, enrollment_date, approved_by) VALUES
    (5, 51, '2026-08-03', 1),
    (6, 52, '2026-08-03', 1);

-- ---------------------------------------------------------------------
-- SCHEDULE: cada materia ocupa su bloque los primeros weekly_hours días
-- (lunes a viernes). IT-1B comparte profesores con IT-1A, así que invierte
-- los bloques de sus dos materias. id_period lo pone un trigger.
-- ---------------------------------------------------------------------
INSERT INTO schedule (day, id_slot, id_group, id_subject, id_teacher)
SELECT d.day, plan.slot, gs.id_group, gs.id_subject, gs.id_teacher
FROM (VALUES
    (1,  1, 1), (1,  2, 2),
    (2,  2, 1), (2,  1, 2),
    (3,  9, 1), (3, 10, 2),
    (4, 17, 1), (4, 18, 2),
    (5, 25, 1), (5, 26, 2),
    (6,  1, 1), (6,  2, 2),
    (7,  3, 3), (7,  4, 4)
) AS plan (id_group, id_subject, slot)
JOIN group_subject gs USING (id_group, id_subject)
JOIN subjects sub ON sub.id_subject = gs.id_subject
CROSS JOIN LATERAL unnest(enum_range(NULL::weekday)) WITH ORDINALITY AS d(day, n)
WHERE d.n <= sub.weekly_hours;

-- ---------------------------------------------------------------------
-- GRADES (final_grade se calcula solo)
-- ---------------------------------------------------------------------
-- 2026-2: IT-1A, materias 1 y 2, alumnos 1 a 10
INSERT INTO grades (id_student, id_group, id_subject, parcial1, parcial2, parcial3)
SELECT g.id_student, 1, sub.id_subject, g.p1, g.p2, g.p3
FROM (VALUES
    ( 1, 8.0, 8.0, 8.5), ( 2, 8.5, 8.5, 8.5), ( 3, 9.0, 8.0, 8.5),
    ( 4, 8.0, 8.5, 8.5), ( 5, 8.5, 8.0, 8.5), ( 6, 9.0, 8.5, 8.5),
    ( 7, 8.0, 8.0, 8.5), ( 8, 8.5, 8.5, 8.5), ( 9, 9.0, 8.0, 8.5),
    (10, 8.0, 8.5, 8.5)
) AS g (id_student, p1, p2, p3)
CROSS JOIN (VALUES (1), (2)) AS sub (id_subject);

-- 2026-1 (historial, actas cerradas): IT-1A del periodo pasado, alumnos 53 a 62
INSERT INTO grades (id_student, id_group, id_subject, parcial1, parcial2, parcial3) VALUES
    (53, 6, 1, 9.0, 9.5, 9.0), (53, 6, 2, 8.5, 9.0, 8.0),
    (54, 6, 1, 8.0, 7.5, 8.5), (54, 6, 2, 7.0, 7.5, 8.0),
    (55, 6, 1, 7.0, 6.5, 7.0), (55, 6, 2, 6.0, 5.0, 6.5),
    (56, 6, 1, 9.5, 10,  9.5), (56, 6, 2, 9.0, 9.5, 10),
    (57, 6, 1, 8.0, 8.0, 8.0), (57, 6, 2, 7.5, 8.0, 7.0),
    (58, 6, 1, 6.5, 7.0, 7.5), (58, 6, 2, 4.0, 5.5, 5.0),   -- reprobó Discrete Mathematics
    (59, 6, 1, 8.5, 8.0, 9.0), (59, 6, 2, 8.0, 8.5, 8.0),
    (60, 6, 1, 7.5, 8.0, 7.0), (60, 6, 2, 6.5, 7.0, 6.0),
    (61, 6, 1, 9.0, 8.5, 8.5), (61, 6, 2, 9.0, 9.0, 8.5),
    (62, 6, 1, 5.0, 4.5, 5.5), (62, 6, 2, 3.5, 5.0, 4.0);   -- reprobó ambas y se dio de baja

-- 2026-2: IT-2A, primer parcial de Database Basics (en curso)
INSERT INTO grades (id_student, id_group, id_subject, parcial1)
SELECT i, 7, 3, 7.0 + (i % 4) * 0.5 FROM generate_series(53, 61) AS i;

-- ---------------------------------------------------------------------
-- ATTENDANCE (2026-2, IT-1A e IT-2A): una marca por día con clase.
-- student053 (id 55) falta seguido para que aparezca en riesgo.
-- ---------------------------------------------------------------------
INSERT INTO attendance (id_student, id_group, id_subject, class_date, status, recorded_by)
SELECT sp.id_student, c.id_group, c.id_subject, d::date,
       CASE
           WHEN sp.id_student = 55 AND extract(doy FROM d)::int % 3 = 0 THEN 'Absent'
           WHEN (sp.id_student * 7 + extract(doy FROM d)::int) % 29 = 0 THEN 'Absent'
           WHEN (sp.id_student * 5 + extract(doy FROM d)::int) % 31 = 0 THEN 'Late'
           WHEN (sp.id_student * 3 + extract(doy FROM d)::int) % 37 = 0 THEN 'Excused'
           ELSE 'Present'
       END::attendance_status,
       tp.id_user
FROM (SELECT DISTINCT id_group, id_subject, id_teacher, day FROM schedule WHERE id_group IN (1, 7)) AS c
JOIN student_profile sp ON sp.id_group = c.id_group
JOIN teacher_profile tp ON tp.id_teacher = c.id_teacher
CROSS JOIN generate_series('2026-08-17'::date, '2026-11-06'::date, interval '1 day') AS d
WHERE to_char(d, 'Dy') = c.day::text;

-- ---------------------------------------------------------------------
-- ANNOUNCEMENTS
-- ---------------------------------------------------------------------
INSERT INTO announcement (title, body, audience, id_career, id_group, author_user, published_at, expires_at) VALUES
    ('Bienvenida al periodo 2026-2',
     'Las clases del periodo 2026-2 iniciaron el 17 de agosto. Revisa tu horario y tu grupo en el portal.',
     'All', NULL, NULL, 1, now() - interval '80 days', NULL),
    ('Aviso vencido',
     'Este aviso ya venció y no debe mostrarse.',
     'All', NULL, NULL, 2, now() - interval '120 days', now() - interval '90 days'),
    ('Semana de Tecnologías de la Información',
     'Habrá conferencias y talleres para la carrera de IT, con valor curricular. Consulta el programa en control escolar.',
     'Career', 1, NULL, 2, now() - interval '10 days', now() + interval '60 days'),
    ('Segundo parcial de Programming Fundamentals',
     'El examen del segundo parcial será el próximo viernes en el bloque de 07:00. Traigan su proyecto impreso.',
     'Group', NULL, 1, 3, now() - interval '2 days', now() + interval '30 days');

-- ---------------------------------------------------------------------
-- Reajustar secuencias de identidad tras insertar ids explícitos
-- ---------------------------------------------------------------------
SELECT setval(pg_get_serial_sequence('users',            'id_user'),     (SELECT max(id_user)     FROM users));
SELECT setval(pg_get_serial_sequence('career',           'id_career'),   (SELECT max(id_career)   FROM career));
SELECT setval(pg_get_serial_sequence('academic_period',  'id_period'),   (SELECT max(id_period)   FROM academic_period));
SELECT setval(pg_get_serial_sequence('admin_profile',    'id_admin'),    (SELECT max(id_admin)    FROM admin_profile));
SELECT setval(pg_get_serial_sequence('teacher_profile',  'id_teacher'),  (SELECT max(id_teacher)  FROM teacher_profile));
SELECT setval(pg_get_serial_sequence('school_groups',    'id_group'),    (SELECT max(id_group)    FROM school_groups));
SELECT setval(pg_get_serial_sequence('student_profile',  'id_student'),  (SELECT max(id_student)  FROM student_profile));
SELECT setval(pg_get_serial_sequence('subjects',         'id_subject'),  (SELECT max(id_subject)  FROM subjects));
SELECT setval(pg_get_serial_sequence('aspirant_profile', 'id_aspirant'), (SELECT max(id_aspirant) FROM aspirant_profile));

SELECT set_config('bisuni.seed', '', true);
