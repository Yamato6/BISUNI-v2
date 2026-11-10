import { pgTable, unique, integer, varchar, check, timestamp, foreignKey, index, numeric, time, date, char, primaryKey, pgView, pgEnum } from "drizzle-orm/pg-core"
import { sql } from "drizzle-orm"

export const aspirantStatus = pgEnum("aspirant_status", ['Pending', 'Accepted', 'Rejected'])
export const recordStatus = pgEnum("record_status", ['Active', 'Inactive'])
export const userRole = pgEnum("user_role", ['Admin', 'Teacher', 'Student', 'Aspirant'])
export const weekday = pgEnum("weekday", ['Mon', 'Tue', 'Wed', 'Thu', 'Fri'])


export const career = pgTable("career", {
	idCareer: integer("id_career").primaryKey().generatedAlwaysAsIdentity({ name: "career_id_career_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 2147483647, cache: 1 }),
	careerName: varchar("career_name", { length: 100 }).notNull(),
	status: recordStatus().default('Active').notNull(),
}, (table) => [
	unique("career_career_name_key").on(table.careerName),
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
	createdAt: timestamp("created_at", { withTimezone: true, mode: 'string' }).defaultNow().notNull(),
}, (table) => [
	unique("users_username_key").on(table.username),
	unique("users_email_key").on(table.email),
	check("users_email_lowercase", sql`(email)::text = lower((email)::text)`),
	check("users_password_is_bcrypt", sql`(password_hash)::text ~ '^\$2[aby]\$\d{2}\$.{53}$'::text`),
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

export const studentProfile = pgTable("student_profile", {
	idStudent: integer("id_student").primaryKey().generatedAlwaysAsIdentity({ name: "student_profile_id_student_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 2147483647, cache: 1 }),
	idUser: integer("id_user").notNull(),
	idCareer: integer("id_career").notNull(),
	semester: integer().default(1).notNull(),
	gpa: numeric({ precision: 4, scale:  2 }).default('0').notNull(),
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
	check("student_profile_semester_check", sql`(semester >= 1) AND (semester <= 12)`),
	check("student_profile_gpa_check", sql`(gpa >= (0)::numeric) AND (gpa <= (10)::numeric)`),
]);

export const schoolGroups = pgTable("school_groups", {
	idGroup: integer("id_group").primaryKey().generatedAlwaysAsIdentity({ name: "school_groups_id_group_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 2147483647, cache: 1 }),
	groupName: varchar("group_name", { length: 50 }).notNull(),
	idCareer: integer("id_career").notNull(),
	semester: integer().notNull(),
	idTutor: integer("id_tutor"),
	status: recordStatus().default('Active').notNull(),
}, (table) => [
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
	unique("school_groups_group_name_key").on(table.groupName),
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
}, (table) => [
	foreignKey({
			columns: [table.idSlot],
			foreignColumns: [timeSlots.idSlot],
			name: "schedule_id_slot_fkey"
		}),
	foreignKey({
			columns: [table.idGroup, table.idSubject, table.idTeacher],
			foreignColumns: [groupSubject.idGroup, groupSubject.idSubject, groupSubject.idTeacher],
			name: "schedule_id_group_id_subject_id_teacher_fkey"
		}).onUpdate("cascade").onDelete("cascade"),
	unique("schedule_group_clash").on(table.day, table.idSlot, table.idGroup),
	unique("schedule_teacher_clash").on(table.day, table.idSlot, table.idTeacher),
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
	check("subjects_weekly_hours_check", sql`weekly_hours > 0`),
]);

export const aspirantProfile = pgTable("aspirant_profile", {
	idAspirant: integer("id_aspirant").primaryKey().generatedAlwaysAsIdentity({ name: "aspirant_profile_id_aspirant_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 2147483647, cache: 1 }),
	idUser: integer("id_user").notNull(),
	curp: char({ length: 18 }).notNull(),
	idCareer: integer("id_career").notNull(),
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
	unique("aspirant_profile_id_user_key").on(table.idUser),
	unique("aspirant_profile_curp_key").on(table.curp),
	check("aspirant_curp_format", sql`curp ~ '^[A-Z]{4}[0-9]{6}[HMX][A-Z]{5}[A-Z0-9][0-9]$'::text`),
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
			columns: [table.idSubject, table.idTeacher],
			foreignColumns: [teacherSubject.idTeacher, teacherSubject.idSubject],
			name: "group_subject_id_teacher_id_subject_fkey"
		}),
	primaryKey({ columns: [table.idGroup, table.idSubject], name: "group_subject_pkey"}),
	unique("group_subject_id_group_id_subject_id_teacher_key").on(table.idGroup, table.idSubject, table.idTeacher),
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
}).as(sql`SELECT s.id_schedule, s.day, s.id_slot, ts.slot_number, ts.start_time, ts.end_time, s.id_teacher, s.id_group, g.group_name, s.id_subject, sub.subject_name FROM schedule s JOIN time_slots ts ON ts.id_slot = s.id_slot JOIN school_groups g ON g.id_group = s.id_group JOIN subjects sub ON sub.id_subject = s.id_subject`);