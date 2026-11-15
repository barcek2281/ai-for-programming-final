-- Настройка экзамена: всем студентам одни и те же вопросы (true) или у каждого свой набор (false).
ALTER TABLE exams ADD COLUMN same_questions BOOLEAN NOT NULL DEFAULT false;
