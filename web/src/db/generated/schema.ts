import { pgTable, foreignKey, unique, check, integer, char, date, varchar, timestamp, uniqueIndex, boolean, index, numeric, time, bigint, jsonb, text, primaryKey, pgView, pgEnum } from "drizzle-orm/pg-core"
import { sql } from "drizzle-orm"

export const announcementAudience = pgEnum("announcement_audience", ['All', 'Career', 'Group'])
export const aspirantStatus = pgEnum("aspirant_status", ['Pending', 'Accepted', 'Rejected'])
export const attendanceStatus = pgEnum("attendance_status", ['Present', 'Absent', 'Late', 'Excused'])
export const periodStatus = pgEnum("period_status", ['Planned', 'Active', 'Closed'])
export const recordStatus = pgEnum("record_status", ['Active', 'Inactive'])
export const studentStatus = pgEnum("student_status", ['Active', 'Leave', 'Dropped', 'Graduated'])
export const userRole = pgEnum("user_role", ['Admin', 'Teacher', 'Student', 'Aspirant'])
export const weekday = pgEnum("weekday", ['Mon', 'Tue', 'Wed', 'Thu', 'Fri'])


export const aspirantProfile = pgTable("aspirant_profile", {
	idAspirant: integer("id_aspirant").primaryKey().generatedAlwaysAsIdentity({ name: "aspirant_profile_id_aspirant_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 2147483647, cache: 1 }),
	idUser: integer("id_user").notNull(),
	curp: char({ length: 18 }).notNull(),
	idCareer: integer("id_career").notNull(),
	idPeriod: integer("id_period").notNull(),
	applicationDate: date("application_date").default(sql`CURRENT_DATE`).notNull(),
	status: aspirantStatus().default('Pending').notNull(),
}, (table) => [
	foreignKey({
			columns: [table.idUser],
			foreignColumns: [users.idUser],
			name: "aspirant_profile_id_user_fkey"
		}),
	foreignKey({
			columns: [table.idCareer],
			foreignColumns: [career.idCareer],
			name: "aspirant_profile_id_career_fkey"
		}),
	foreignKey({
			columns: [table.idPeriod],
			foreignColumns: [academicPeriod.idPeriod],
			name: "aspirant_profile_id_period_fkey"
		}),
	unique("aspirant_profile_id_user_key").on(table.idUser),
	unique("aspirant_profile_curp_key").on(table.curp),
	check("aspirant_curp_format", sql`curp ~ '^[A-Z]{4}[0-9]{6}[HMX][A-Z]{5}[A-Z0-9][0-9]$'::text`),
]);

export const teacherProfile = pgTable("teacher_profile", {
	idTeacher: integer("id_teacher").primaryKey().generatedAlwaysAsIdentity({ name: "teacher_profile_id_teacher_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 2147483647, cache: 1 }),
	idUser: integer("id_user").notNull(),
	specialty: varchar({ length: 100 }),
	phone: varchar({ length: 20 }),
}, (table) => [
	foreignKey({
			columns: [table.idUser],
			foreignColumns: [users.idUser],
			name: "teacher_profile_id_user_fkey"
		}),
	unique("teacher_profile_id_user_key").on(table.idUser),
]);

export const adminProfile = pgTable("admin_profile", {
	idAdmin: integer("id_admin").primaryKey().generatedAlwaysAsIdentity({ name: "admin_profile_id_admin_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 2147483647, cache: 1 }),
	idUser: integer("id_user").notNull(),
	position: varchar({ length: 50 }),
	lastAccess: timestamp("last_access", { withTimezone: true, mode: 'string' }),
}, (table) => [
	foreignKey({
			columns: [table.idUser],
			foreignColumns: [users.idUser],
			name: "admin_profile_id_user_fkey"
		}),
	unique("admin_profile_id_user_key").on(table.idUser),
]);

export const academicPeriod = pgTable("academic_period", {
	idPeriod: integer("id_period").primaryKey().generatedAlwaysAsIdentity({ name: "academic_period_id_period_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 2147483647, cache: 1 }),
	name: varchar({ length: 20 }).notNull(),
	startDate: date("start_date").notNull(),
	endDate: date("end_date").notNull(),
	status: periodStatus().default('Planned').notNull(),
	admissionsOpen: boolean("admissions_open").default(false).notNull(),
}, (table) => [
	uniqueIndex("period_single_active").using("btree", sql`(true)`).where(sql`(status = 'Active'::period_status)`),
	unique("academic_period_name_key").on(table.name),
	check("period_dates", sql`end_date > start_date`),
	check("period_closed_admissions", sql`NOT (admissions_open AND (status = 'Closed'::period_status))`),
]);

export const schoolGroups = pgTable("school_groups", {
	idGroup: integer("id_group").primaryKey().generatedAlwaysAsIdentity({ name: "school_groups_id_group_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 2147483647, cache: 1 }),
	groupName: varchar("group_name", { length: 50 }).notNull(),
	idPeriod: integer("id_period").notNull(),
	idCareer: integer("id_career").notNull(),
	semester: integer().notNull(),
	idTutor: integer("id_tutor"),
	status: recordStatus().default('Active').notNull(),
}, (table) => [
	index("idx_group_period").using("btree", table.idPeriod.asc().nullsLast().op("int4_ops")),
	foreignKey({
			columns: [table.idPeriod],
			foreignColumns: [academicPeriod.idPeriod],
			name: "school_groups_id_period_fkey"
		}),
	foreignKey({
			columns: [table.idCareer],
			foreignColumns: [career.idCareer],
			name: "school_groups_id_career_fkey"
		}),
	foreignKey({
			columns: [table.idTutor],
			foreignColumns: [teacherProfile.idTeacher],
			name: "school_groups_id_tutor_fkey"
		}),
	unique("school_groups_id_group_id_career_key").on(table.idGroup, table.idCareer),
	unique("school_groups_period_name").on(table.groupName, table.idPeriod),
	check("school_groups_semester_check", sql`(semester >= 1) AND (semester <= 12)`),
]);

export const grades = pgTable("grades", {
	idGrade: integer("id_grade").primaryKey().generatedAlwaysAsIdentity({ name: "grades_id_grade_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 2147483647, cache: 1 }),
	idStudent: integer("id_student").notNull(),
	idGroup: integer("id_group").notNull(),
	idSubject: integer("id_subject").notNull(),
	parcial1: numeric({ precision: 4, scale:  2 }),
	parcial2: numeric({ precision: 4, scale:  2 }),
	parcial3: numeric({ precision: 4, scale:  2 }),
	finalGrade: numeric("final_grade", { precision: 4, scale:  2 }).generatedAlwaysAs(sql`round((((parcial1 + parcial2) + parcial3) / (3)::numeric), 2)`),
}, (table) => [
	index("idx_grades_group_subject").using("btree", table.idGroup.asc().nullsLast().op("int4_ops"), table.idSubject.asc().nullsLast().op("int4_ops")),
	foreignKey({
			columns: [table.idStudent],
			foreignColumns: [studentProfile.idStudent],
			name: "grades_id_student_fkey"
		}),
	foreignKey({
			columns: [table.idGroup, table.idSubject],
			foreignColumns: [groupSubject.idGroup, groupSubject.idSubject],
			name: "grades_id_group_id_subject_fkey"
		}),
	unique("grades_id_student_id_subject_id_group_key").on(table.idStudent, table.idGroup, table.idSubject),
	check("grades_parcial1_check", sql`(parcial1 >= (0)::numeric) AND (parcial1 <= (10)::numeric)`),
	check("grades_parcial2_check", sql`(parcial2 >= (0)::numeric) AND (parcial2 <= (10)::numeric)`),
	check("grades_parcial3_check", sql`(parcial3 >= (0)::numeric) AND (parcial3 <= (10)::numeric)`),
]);

export const timeSlots = pgTable("time_slots", {
	idSlot: integer("id_slot").primaryKey().generatedAlwaysAsIdentity({ name: "time_slots_id_slot_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 2147483647, cache: 1 }),
	slotNumber: integer("slot_number").notNull(),
	startTime: time("start_time").notNull(),
	endTime: time("end_time").notNull(),
}, (table) => [
	unique("time_slots_slot_number_key").on(table.slotNumber),
	check("time_slots_check", sql`end_time > start_time`),
]);

export const enrollment = pgTable("enrollment", {
	idEnrollment: integer("id_enrollment").primaryKey().generatedAlwaysAsIdentity({ name: "enrollment_id_enrollment_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 2147483647, cache: 1 }),
	idAspirant: integer("id_aspirant").notNull(),
	idStudent: integer("id_student").notNull(),
	enrollmentDate: date("enrollment_date").default(sql`CURRENT_DATE`).notNull(),
	approvedBy: integer("approved_by").notNull(),
}, (table) => [
	foreignKey({
			columns: [table.idAspirant],
			foreignColumns: [aspirantProfile.idAspirant],
			name: "enrollment_id_aspirant_fkey"
		}),
	foreignKey({
			columns: [table.idStudent],
			foreignColumns: [studentProfile.idStudent],
			name: "enrollment_id_student_fkey"
		}),
	foreignKey({
			columns: [table.approvedBy],
			foreignColumns: [adminProfile.idAdmin],
			name: "enrollment_approved_by_fkey"
		}),
	unique("enrollment_id_aspirant_key").on(table.idAspirant),
	unique("enrollment_id_student_key").on(table.idStudent),
]);

export const schedule = pgTable("schedule", {
	idSchedule: integer("id_schedule").primaryKey().generatedAlwaysAsIdentity({ name: "schedule_id_schedule_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 2147483647, cache: 1 }),
	day: weekday().notNull(),
	idSlot: integer("id_slot").notNull(),
	idGroup: integer("id_group").notNull(),
	idSubject: integer("id_subject").notNull(),
	idTeacher: integer("id_teacher").notNull(),
	idPeriod: integer("id_period").notNull(),
}, (table) => [
	foreignKey({
			columns: [table.idSlot],
			foreignColumns: [timeSlots.idSlot],
			name: "schedule_id_slot_fkey"
		}),
	foreignKey({
			columns: [table.idPeriod],
			foreignColumns: [academicPeriod.idPeriod],
			name: "schedule_id_period_fkey"
		}),
	foreignKey({
			columns: [table.idGroup, table.idSubject, table.idTeacher],
			foreignColumns: [groupSubject.idGroup, groupSubject.idSubject, groupSubject.idTeacher],
			name: "schedule_id_group_id_subject_id_teacher_fkey"
		}).onUpdate("cascade").onDelete("cascade"),
	unique("schedule_group_clash").on(table.day, table.idSlot, table.idGroup),
	unique("schedule_teacher_clash").on(table.day, table.idSlot, table.idTeacher, table.idPeriod),
]);

export const auditLog = pgTable("audit_log", {
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	idAudit: bigint("id_audit", { mode: "number" }).primaryKey().generatedAlwaysAsIdentity({ name: "audit_log_id_audit_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 9223372036854775807, cache: 1 }),
	at: timestamp({ withTimezone: true, mode: 'string' }).defaultNow().notNull(),
	actorUser: integer("actor_user"),
	action: varchar({ length: 10 }).notNull(),
	entity: varchar({ length: 40 }).notNull(),
	entityId: varchar("entity_id", { length: 60 }).notNull(),
	details: jsonb().default({}).notNull(),
}, (table) => [
	index("idx_audit_at").using("btree", table.at.desc().nullsFirst().op("timestamptz_ops")),
	index("idx_audit_entity").using("btree", table.entity.asc().nullsLast().op("text_ops"), table.entityId.asc().nullsLast().op("text_ops")),
	foreignKey({
			columns: [table.actorUser],
			foreignColumns: [users.idUser],
			name: "audit_log_actor_user_fkey"
		}),
]);

export const announcement = pgTable("announcement", {
	idAnnouncement: integer("id_announcement").primaryKey().generatedAlwaysAsIdentity({ name: "announcement_id_announcement_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 2147483647, cache: 1 }),
	title: varchar({ length: 120 }).notNull(),
	body: text().notNull(),
	audience: announcementAudience().notNull(),
	idCareer: integer("id_career"),
	idGroup: integer("id_group"),
	authorUser: integer("author_user").notNull(),
	publishedAt: timestamp("published_at", { withTimezone: true, mode: 'string' }).defaultNow().notNull(),
	expiresAt: timestamp("expires_at", { withTimezone: true, mode: 'string' }),
}, (table) => [
	index("idx_announcement_published").using("btree", table.publishedAt.desc().nullsFirst().op("timestamptz_ops")),
	foreignKey({
			columns: [table.idCareer],
			foreignColumns: [career.idCareer],
			name: "announcement_id_career_fkey"
		}),
	foreignKey({
			columns: [table.idGroup],
			foreignColumns: [schoolGroups.idGroup],
			name: "announcement_id_group_fkey"
		}).onDelete("cascade"),
	foreignKey({
			columns: [table.authorUser],
			foreignColumns: [users.idUser],
			name: "announcement_author_user_fkey"
		}),
	check("announcement_audience_target", sql`((audience = 'All'::announcement_audience) AND (id_career IS NULL) AND (id_group IS NULL)) OR ((audience = 'Career'::announcement_audience) AND (id_career IS NOT NULL) AND (id_group IS NULL)) OR ((audience = 'Group'::announcement_audience) AND (id_group IS NOT NULL) AND (id_career IS NULL))`),
	check("announcement_expiry", sql`(expires_at IS NULL) OR (expires_at > published_at)`),
	check("announcement_not_blank", sql`(length(TRIM(BOTH FROM title)) > 0) AND (length(TRIM(BOTH FROM body)) > 0)`),
]);

export const attendance = pgTable("attendance", {
	idAttendance: integer("id_attendance").primaryKey().generatedAlwaysAsIdentity({ name: "attendance_id_attendance_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 2147483647, cache: 1 }),
	idStudent: integer("id_student").notNull(),
	idGroup: integer("id_group").notNull(),
	idSubject: integer("id_subject").notNull(),
	classDate: date("class_date").notNull(),
	status: attendanceStatus().notNull(),
	recordedBy: integer("recorded_by"),
	recordedAt: timestamp("recorded_at", { withTimezone: true, mode: 'string' }).defaultNow().notNull(),
}, (table) => [
	index("idx_attendance_class").using("btree", table.idGroup.asc().nullsLast().op("int4_ops"), table.idSubject.asc().nullsLast().op("int4_ops"), table.classDate.asc().nullsLast().op("int4_ops")),
	foreignKey({
			columns: [table.idStudent],
			foreignColumns: [studentProfile.idStudent],
			name: "attendance_id_student_fkey"
		}),
	foreignKey({
			columns: [table.recordedBy],
			foreignColumns: [users.idUser],
			name: "attendance_recorded_by_fkey"
		}),
	foreignKey({
			columns: [table.idGroup, table.idSubject],
			foreignColumns: [groupSubject.idGroup, groupSubject.idSubject],
			name: "attendance_id_group_id_subject_fkey"
		}),
	unique("attendance_unique").on(table.idStudent, table.idGroup, table.idSubject, table.classDate),
]);

export const studentProfile = pgTable("student_profile", {
	idStudent: integer("id_student").primaryKey().generatedAlwaysAsIdentity({ name: "student_profile_id_student_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 2147483647, cache: 1 }),
	idUser: integer("id_user").notNull(),
	enrollmentNumber: varchar("enrollment_number", { length: 12 }).notNull(),
	idCareer: integer("id_career").notNull(),
	semester: integer().default(1).notNull(),
	academicStatus: studentStatus("academic_status").default('Active').notNull(),
	idGroup: integer("id_group"),
}, (table) => [
	index("idx_student_group").using("btree", table.idGroup.asc().nullsLast().op("int4_ops")),
	foreignKey({
			columns: [table.idUser],
			foreignColumns: [users.idUser],
			name: "student_profile_id_user_fkey"
		}),
	foreignKey({
			columns: [table.idCareer],
			foreignColumns: [career.idCareer],
			name: "student_profile_id_career_fkey"
		}),
	foreignKey({
			columns: [table.idCareer, table.idGroup],
			foreignColumns: [schoolGroups.idGroup, schoolGroups.idCareer],
			name: "student_profile_id_group_id_career_fkey"
		}),
	unique("student_profile_id_user_key").on(table.idUser),
	unique("student_profile_enrollment_number_key").on(table.enrollmentNumber),
	check("student_profile_semester_check", sql`(semester >= 1) AND (semester <= 12)`),
	check("student_enrollment_number_format", sql`(enrollment_number)::text ~ '^[0-9]{4}[A-Z]{2,4}[0-9]{4}$'::text`),
	check("student_group_requires_active", sql`(id_group IS NULL) OR (academic_status = 'Active'::student_status)`),
]);

export const subjects = pgTable("subjects", {
	idSubject: integer("id_subject").primaryKey().generatedAlwaysAsIdentity({ name: "subjects_id_subject_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 2147483647, cache: 1 }),
	subjectName: varchar("subject_name", { length: 100 }).notNull(),
	credits: integer().notNull(),
	semester: integer().notNull(),
	weeklyHours: integer("weekly_hours").notNull(),
	idCareer: integer("id_career").notNull(),
	status: recordStatus().default('Active').notNull(),
}, (table) => [
	foreignKey({
			columns: [table.idCareer],
			foreignColumns: [career.idCareer],
			name: "subjects_id_career_fkey"
		}),
	unique("subjects_id_career_subject_name_key").on(table.subjectName, table.idCareer),
	check("subjects_credits_check", sql`credits > 0`),
	check("subjects_semester_check", sql`(semester >= 1) AND (semester <= 12)`),
	check("subjects_weekly_hours_check", sql`(weekly_hours >= 1) AND (weekly_hours <= 45)`),
]);

export const career = pgTable("career", {
	idCareer: integer("id_career").primaryKey().generatedAlwaysAsIdentity({ name: "career_id_career_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 2147483647, cache: 1 }),
	careerName: varchar("career_name", { length: 100 }).notNull(),
	code: varchar({ length: 4 }).notNull(),
	totalSemesters: integer("total_semesters").default(9).notNull(),
	status: recordStatus().default('Active').notNull(),
}, (table) => [
	unique("career_career_name_key").on(table.careerName),
	unique("career_code_key").on(table.code),
	check("career_code_format", sql`(code)::text ~ '^[A-Z]{2,4}$'::text`),
	check("career_total_semesters", sql`(total_semesters >= 1) AND (total_semesters <= 12)`),
]);

export const users = pgTable("users", {
	idUser: integer("id_user").primaryKey().generatedAlwaysAsIdentity({ name: "users_id_user_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 2147483647, cache: 1 }),
	username: varchar({ length: 50 }).notNull(),
	passwordHash: varchar("password_hash", { length: 255 }).notNull(),
	email: varchar({ length: 150 }).notNull(),
	firstName: varchar("first_name", { length: 50 }).notNull(),
	lastName: varchar("last_name", { length: 50 }).notNull(),
	role: userRole().notNull(),
	status: recordStatus().default('Active').notNull(),
	mustChangePassword: boolean("must_change_password").default(false).notNull(),
	createdAt: timestamp("created_at", { withTimezone: true, mode: 'string' }).defaultNow().notNull(),
}, (table) => [
	unique("users_username_key").on(table.username),
	unique("users_email_key").on(table.email),
	check("users_email_lowercase", sql`(email)::text = lower((email)::text)`),
	check("users_password_is_bcrypt", sql`(password_hash)::text ~ '^\$2[aby]\$\d{2}\$.{53}$'::text`),
]);

export const teacherSubject = pgTable("teacher_subject", {
	idTeacher: integer("id_teacher").notNull(),
	idSubject: integer("id_subject").notNull(),
}, (table) => [
	foreignKey({
			columns: [table.idTeacher],
			foreignColumns: [teacherProfile.idTeacher],
			name: "teacher_subject_id_teacher_fkey"
		}),
	foreignKey({
			columns: [table.idSubject],
			foreignColumns: [subjects.idSubject],
			name: "teacher_subject_id_subject_fkey"
		}),
	primaryKey({ columns: [table.idTeacher, table.idSubject], name: "teacher_subject_pkey"}),
]);

export const groupSubject = pgTable("group_subject", {
	idGroup: integer("id_group").notNull(),
	idSubject: integer("id_subject").notNull(),
	idTeacher: integer("id_teacher"),
	gradesClosedAt: timestamp("grades_closed_at", { withTimezone: true, mode: 'string' }),
	gradesClosedBy: integer("grades_closed_by"),
}, (table) => [
	index("idx_group_subject_teacher").using("btree", table.idTeacher.asc().nullsLast().op("int4_ops")),
	foreignKey({
			columns: [table.idGroup],
			foreignColumns: [schoolGroups.idGroup],
			name: "group_subject_id_group_fkey"
		}),
	foreignKey({
			columns: [table.idSubject],
			foreignColumns: [subjects.idSubject],
			name: "group_subject_id_subject_fkey"
		}),
	foreignKey({
			columns: [table.gradesClosedBy],
			foreignColumns: [users.idUser],
			name: "group_subject_grades_closed_by_fkey"
		}),
	foreignKey({
			columns: [table.idSubject, table.idTeacher],
			foreignColumns: [teacherSubject.idTeacher, teacherSubject.idSubject],
			name: "group_subject_id_teacher_id_subject_fkey"
		}),
	primaryKey({ columns: [table.idGroup, table.idSubject], name: "group_subject_pkey"}),
	unique("group_subject_id_group_id_subject_id_teacher_key").on(table.idGroup, table.idSubject, table.idTeacher),
	check("group_subject_closed_by", sql`(grades_closed_at IS NULL) = (grades_closed_by IS NULL)`),
]);
export const vTeacherSchedule = pgView("v_teacher_schedule", {	idSchedule: integer("id_schedule"),
	day: weekday(),
	idSlot: integer("id_slot"),
	slotNumber: integer("slot_number"),
	startTime: time("start_time"),
	endTime: time("end_time"),
	idTeacher: integer("id_teacher"),
	idGroup: integer("id_group"),
	groupName: varchar("group_name", { length: 50 }),
	idSubject: integer("id_subject"),
	subjectName: varchar("subject_name", { length: 100 }),
	idPeriod: integer("id_period"),
}).as(sql`SELECT s.id_schedule, s.day, s.id_slot, ts.slot_number, ts.start_time, ts.end_time, s.id_teacher, s.id_group, g.group_name, s.id_subject, sub.subject_name, s.id_period FROM schedule s JOIN time_slots ts ON ts.id_slot = s.id_slot JOIN school_groups g ON g.id_group = s.id_group JOIN subjects sub ON sub.id_subject = s.id_subject`);

export const vStudentGpa = pgView("v_student_gpa", {	idStudent: integer("id_student"),
	gpa: numeric(),
	gradedSubjects: integer("graded_subjects"),
}).as(sql`SELECT sp.id_student, round(avg(g.final_grade), 2) AS gpa, count(g.final_grade)::integer AS graded_subjects FROM student_profile sp LEFT JOIN grades g ON g.id_student = sp.id_student AND g.final_grade IS NOT NULL GROUP BY sp.id_student`);

export const vKardex = pgView("v_kardex", {	idStudent: integer("id_student"),
	idPeriod: integer("id_period"),
	periodName: varchar("period_name", { length: 20 }),
	periodStart: date("period_start"),
	periodStatus: periodStatus("period_status"),
	idGroup: integer("id_group"),
	groupName: varchar("group_name", { length: 50 }),
	groupSemester: integer("group_semester"),
	idSubject: integer("id_subject"),
	subjectName: varchar("subject_name", { length: 100 }),
	credits: integer(),
	subjectSemester: integer("subject_semester"),
	parcial1: numeric({ precision: 4, scale:  2 }),
	parcial2: numeric({ precision: 4, scale:  2 }),
	parcial3: numeric({ precision: 4, scale:  2 }),
	finalGrade: numeric("final_grade", { precision: 4, scale:  2 }),
	isFinal: boolean("is_final"),
	result: text(),
}).as(sql`WITH taken AS ( SELECT grades.id_student, grades.id_group, grades.id_subject FROM grades UNION SELECT sp.id_student, gs_1.id_group, gs_1.id_subject FROM student_profile sp JOIN group_subject gs_1 ON gs_1.id_group = sp.id_group ) SELECT t.id_student, p.id_period, p.name AS period_name, p.start_date AS period_start, p.status AS period_status, g.id_group, g.group_name, g.semester AS group_semester, s.id_subject, s.subject_name, s.credits, s.semester AS subject_semester, gr.parcial1, gr.parcial2, gr.parcial3, gr.final_grade, gs.grades_closed_at IS NOT NULL OR p.status = 'Closed'::period_status AS is_final, CASE WHEN gs.grades_closed_at IS NULL AND p.status <> 'Closed'::period_status THEN 'InProgress'::text WHEN gr.final_grade >= passing_grade() THEN 'Passed'::text ELSE 'Failed'::text END AS result FROM taken t JOIN school_groups g ON g.id_group = t.id_group JOIN academic_period p ON p.id_period = g.id_period JOIN subjects s ON s.id_subject = t.id_subject JOIN group_subject gs ON gs.id_group = t.id_group AND gs.id_subject = t.id_subject LEFT JOIN grades gr ON gr.id_student = t.id_student AND gr.id_group = t.id_group AND gr.id_subject = t.id_subject`);

export const vAttendanceSummary = pgView("v_attendance_summary", {	idStudent: integer("id_student"),
	idGroup: integer("id_group"),
	idSubject: integer("id_subject"),
	sessions: integer(),
	absences: integer(),
	lates: integer(),
	excused: integer(),
	absencePct: numeric("absence_pct"),
}).as(sql`SELECT id_student, id_group, id_subject, count(*)::integer AS sessions, count(*) FILTER (WHERE status = 'Absent'::attendance_status)::integer AS absences, count(*) FILTER (WHERE status = 'Late'::attendance_status)::integer AS lates, count(*) FILTER (WHERE status = 'Excused'::attendance_status)::integer AS excused, round(100.0 * count(*) FILTER (WHERE status = 'Absent'::attendance_status)::numeric / count(*)::numeric, 1) AS absence_pct FROM attendance GROUP BY id_student, id_group, id_subject`);