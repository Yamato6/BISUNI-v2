import { relations } from "drizzle-orm/relations";
import { users, teacherProfile, studentProfile, career, schoolGroups, grades, groupSubject, aspirantProfile, enrollment, adminProfile, timeSlots, schedule, subjects, teacherSubject } from "./schema";

export const teacherProfileRelations = relations(teacherProfile, ({one, many}) => ({
	user: one(users, {
		fields: [teacherProfile.idUser],
		references: [users.idUser]
	}),
	schoolGroups: many(schoolGroups),
	teacherSubjects: many(teacherSubject),
}));

export const usersRelations = relations(users, ({many}) => ({
	teacherProfiles: many(teacherProfile),
	studentProfiles: many(studentProfile),
	aspirantProfiles: many(aspirantProfile),
	adminProfiles: many(adminProfile),
}));

export const studentProfileRelations = relations(studentProfile, ({one, many}) => ({
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
	grades: many(grades),
	enrollments: many(enrollment),
}));

export const careerRelations = relations(career, ({many}) => ({
	studentProfiles: many(studentProfile),
	schoolGroups: many(schoolGroups),
	subjects: many(subjects),
	aspirantProfiles: many(aspirantProfile),
}));

export const schoolGroupsRelations = relations(schoolGroups, ({one, many}) => ({
	studentProfiles: many(studentProfile),
	career: one(career, {
		fields: [schoolGroups.idCareer],
		references: [career.idCareer]
	}),
	teacherProfile: one(teacherProfile, {
		fields: [schoolGroups.idTutor],
		references: [teacherProfile.idTeacher]
	}),
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

export const groupSubjectRelations = relations(groupSubject, ({one, many}) => ({
	grades: many(grades),
	schedules: many(schedule),
	schoolGroup: one(schoolGroups, {
		fields: [groupSubject.idGroup],
		references: [schoolGroups.idGroup]
	}),
	subject: one(subjects, {
		fields: [groupSubject.idSubject],
		references: [subjects.idSubject]
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

export const aspirantProfileRelations = relations(aspirantProfile, ({one, many}) => ({
	enrollments: many(enrollment),
	user: one(users, {
		fields: [aspirantProfile.idUser],
		references: [users.idUser]
	}),
	career: one(career, {
		fields: [aspirantProfile.idCareer],
		references: [career.idCareer]
	}),
}));

export const adminProfileRelations = relations(adminProfile, ({one, many}) => ({
	enrollments: many(enrollment),
	user: one(users, {
		fields: [adminProfile.idUser],
		references: [users.idUser]
	}),
}));

export const scheduleRelations = relations(schedule, ({one}) => ({
	timeSlot: one(timeSlots, {
		fields: [schedule.idSlot],
		references: [timeSlots.idSlot]
	}),
	groupSubject: one(groupSubject, {
		fields: [schedule.idGroup],
		references: [groupSubject.idGroup]
	}),
}));

export const timeSlotsRelations = relations(timeSlots, ({many}) => ({
	schedules: many(schedule),
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