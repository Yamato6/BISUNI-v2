import { relations } from "drizzle-orm/relations";
import { users, aspirantProfile, career, academicPeriod, teacherProfile, adminProfile, schoolGroups, studentProfile, grades, groupSubject, enrollment, timeSlots, schedule, auditLog, announcement, attendance, subjects, teacherSubject } from "./schema";

export const aspirantProfileRelations = relations(aspirantProfile, ({one, many}) => ({
	user: one(users, {
		fields: [aspirantProfile.idUser],
		references: [users.idUser]
	}),
	career: one(career, {
		fields: [aspirantProfile.idCareer],
		references: [career.idCareer]
	}),
	academicPeriod: one(academicPeriod, {
		fields: [aspirantProfile.idPeriod],
		references: [academicPeriod.idPeriod]
	}),
	enrollments: many(enrollment),
}));

export const usersRelations = relations(users, ({many}) => ({
	aspirantProfiles: many(aspirantProfile),
	teacherProfiles: many(teacherProfile),
	adminProfiles: many(adminProfile),
	auditLogs: many(auditLog),
	announcements: many(announcement),
	attendances: many(attendance),
	studentProfiles: many(studentProfile),
	groupSubjects: many(groupSubject),
}));

export const careerRelations = relations(career, ({many}) => ({
	aspirantProfiles: many(aspirantProfile),
	schoolGroups: many(schoolGroups),
	announcements: many(announcement),
	studentProfiles: many(studentProfile),
	subjects: many(subjects),
}));

export const academicPeriodRelations = relations(academicPeriod, ({many}) => ({
	aspirantProfiles: many(aspirantProfile),
	schoolGroups: many(schoolGroups),
	schedules: many(schedule),
}));

export const teacherProfileRelations = relations(teacherProfile, ({one, many}) => ({
	user: one(users, {
		fields: [teacherProfile.idUser],
		references: [users.idUser]
	}),
	schoolGroups: many(schoolGroups),
	teacherSubjects: many(teacherSubject),
}));

export const adminProfileRelations = relations(adminProfile, ({one, many}) => ({
	user: one(users, {
		fields: [adminProfile.idUser],
		references: [users.idUser]
	}),
	enrollments: many(enrollment),
}));

export const schoolGroupsRelations = relations(schoolGroups, ({one, many}) => ({
	academicPeriod: one(academicPeriod, {
		fields: [schoolGroups.idPeriod],
		references: [academicPeriod.idPeriod]
	}),
	career: one(career, {
		fields: [schoolGroups.idCareer],
		references: [career.idCareer]
	}),
	teacherProfile: one(teacherProfile, {
		fields: [schoolGroups.idTutor],
		references: [teacherProfile.idTeacher]
	}),
	announcements: many(announcement),
	studentProfiles: many(studentProfile),
	groupSubjects: many(groupSubject),
}));

export const gradesRelations = relations(grades, ({one}) => ({
	studentProfile: one(studentProfile, {
		fields: [grades.idStudent],
		references: [studentProfile.idStudent]
	}),
	groupSubject: one(groupSubject, {
		fields: [grades.idGroup],
		references: [groupSubject.idGroup]
	}),
}));

export const studentProfileRelations = relations(studentProfile, ({one, many}) => ({
	grades: many(grades),
	enrollments: many(enrollment),
	attendances: many(attendance),
	user: one(users, {
		fields: [studentProfile.idUser],
		references: [users.idUser]
	}),
	career: one(career, {
		fields: [studentProfile.idCareer],
		references: [career.idCareer]
	}),
	schoolGroup: one(schoolGroups, {
		fields: [studentProfile.idCareer],
		references: [schoolGroups.idGroup]
	}),
}));

export const groupSubjectRelations = relations(groupSubject, ({one, many}) => ({
	grades: many(grades),
	schedules: many(schedule),
	attendances: many(attendance),
	schoolGroup: one(schoolGroups, {
		fields: [groupSubject.idGroup],
		references: [schoolGroups.idGroup]
	}),
	subject: one(subjects, {
		fields: [groupSubject.idSubject],
		references: [subjects.idSubject]
	}),
	user: one(users, {
		fields: [groupSubject.gradesClosedBy],
		references: [users.idUser]
	}),
	teacherSubject: one(teacherSubject, {
		fields: [groupSubject.idSubject],
		references: [teacherSubject.idTeacher]
	}),
}));

export const enrollmentRelations = relations(enrollment, ({one}) => ({
	aspirantProfile: one(aspirantProfile, {
		fields: [enrollment.idAspirant],
		references: [aspirantProfile.idAspirant]
	}),
	studentProfile: one(studentProfile, {
		fields: [enrollment.idStudent],
		references: [studentProfile.idStudent]
	}),
	adminProfile: one(adminProfile, {
		fields: [enrollment.approvedBy],
		references: [adminProfile.idAdmin]
	}),
}));

export const scheduleRelations = relations(schedule, ({one}) => ({
	timeSlot: one(timeSlots, {
		fields: [schedule.idSlot],
		references: [timeSlots.idSlot]
	}),
	academicPeriod: one(academicPeriod, {
		fields: [schedule.idPeriod],
		references: [academicPeriod.idPeriod]
	}),
	groupSubject: one(groupSubject, {
		fields: [schedule.idGroup],
		references: [groupSubject.idGroup]
	}),
}));

export const timeSlotsRelations = relations(timeSlots, ({many}) => ({
	schedules: many(schedule),
}));

export const auditLogRelations = relations(auditLog, ({one}) => ({
	user: one(users, {
		fields: [auditLog.actorUser],
		references: [users.idUser]
	}),
}));

export const announcementRelations = relations(announcement, ({one}) => ({
	career: one(career, {
		fields: [announcement.idCareer],
		references: [career.idCareer]
	}),
	schoolGroup: one(schoolGroups, {
		fields: [announcement.idGroup],
		references: [schoolGroups.idGroup]
	}),
	user: one(users, {
		fields: [announcement.authorUser],
		references: [users.idUser]
	}),
}));

export const attendanceRelations = relations(attendance, ({one}) => ({
	studentProfile: one(studentProfile, {
		fields: [attendance.idStudent],
		references: [studentProfile.idStudent]
	}),
	user: one(users, {
		fields: [attendance.recordedBy],
		references: [users.idUser]
	}),
	groupSubject: one(groupSubject, {
		fields: [attendance.idGroup],
		references: [groupSubject.idGroup]
	}),
}));

export const subjectsRelations = relations(subjects, ({one, many}) => ({
	career: one(career, {
		fields: [subjects.idCareer],
		references: [career.idCareer]
	}),
	teacherSubjects: many(teacherSubject),
	groupSubjects: many(groupSubject),
}));

export const teacherSubjectRelations = relations(teacherSubject, ({one, many}) => ({
	teacherProfile: one(teacherProfile, {
		fields: [teacherSubject.idTeacher],
		references: [teacherProfile.idTeacher]
	}),
	subject: one(subjects, {
		fields: [teacherSubject.idSubject],
		references: [subjects.idSubject]
	}),
	groupSubjects: many(groupSubject),
}));