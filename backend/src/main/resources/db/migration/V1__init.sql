CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
  id            BIGSERIAL PRIMARY KEY,
  email         VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  full_name     VARCHAR(255) NOT NULL,
  role          VARCHAR(20)  NOT NULL,              -- ADMIN | TEACHER | STUDENT
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE courses (
  id          BIGSERIAL PRIMARY KEY,
  teacher_id  BIGINT       NOT NULL REFERENCES users(id),
  title       VARCHAR(255) NOT NULL,
  description TEXT,
  language    VARCHAR(5)   NOT NULL,                -- kk | ru | en
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE course_topics (                       -- темы (разделы) курса
  id          BIGSERIAL PRIMARY KEY,
  course_id   BIGINT       NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  title       VARCHAR(255) NOT NULL,                -- «Неделя 3: Constructive alignment»
  description TEXT,
  position    INT          NOT NULL
);

CREATE TABLE learning_outcomes (
  id        BIGSERIAL PRIMARY KEY,
  topic_id  BIGINT      NOT NULL REFERENCES course_topics(id) ON DELETE CASCADE,
  code      VARCHAR(20) NOT NULL,                   -- LO1, LO2 ...
  text      TEXT        NOT NULL,
  position  INT         NOT NULL
);

CREATE TABLE materials (
  id          BIGSERIAL PRIMARY KEY,
  course_id   BIGINT       NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  topic_id    BIGINT       NOT NULL REFERENCES course_topics(id) ON DELETE CASCADE,
  file_name   VARCHAR(255) NOT NULL,
  s3_key      VARCHAR(512) NOT NULL,
  status      VARCHAR(20)  NOT NULL,                -- UPLOADED | PROCESSING | READY | FAILED
  error       TEXT,
  pages       INT,
  uploaded_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Фрагменты материалов с эмбеддингами хранит Spring AI PgVectorStore в своей таблице vector_store
-- (id uuid, content text, metadata json, embedding vector(1024)).
-- Её создаёт сам Spring AI (initialize-schema: true), в Flyway её НЕТ.
-- Метаданные фрагмента: courseId, topicId, materialId, fileName, page.

CREATE TABLE question_cards (
  id               BIGSERIAL PRIMARY KEY,
  course_id        BIGINT      NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  topic_id         BIGINT      NOT NULL REFERENCES course_topics(id) ON DELETE CASCADE,
  outcome_id       BIGINT      NOT NULL REFERENCES learning_outcomes(id) ON DELETE CASCADE,
  question         TEXT        NOT NULL,
  fundamentals     JSONB       NOT NULL,            -- ["...", ...] 3–5 ключевых идей
  misconceptions   JSONB       NOT NULL,            -- ["...", ...]
  follow_up_ideas  JSONB       NOT NULL,            -- [{"type":"WHY","text":"..."}]
  rubric           JSONB       NOT NULL,            -- {"0":"...","1":"...","2":"...","3":"...","4":"..."}
  source_chunk_ids JSONB       NOT NULL,            -- ["uuid", ...] id фрагментов из vector_store
  status           VARCHAR(20) NOT NULL,            -- DRAFT | APPROVED | REJECTED
  ai_generated     BOOLEAN     NOT NULL DEFAULT true,
  updated_by       BIGINT      REFERENCES users(id),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE exams (
  id                    BIGSERIAL PRIMARY KEY,
  course_id             BIGINT       NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  title                 VARCHAR(255) NOT NULL,
  mode                  VARCHAR(20)  NOT NULL,      -- PRACTICE | EXAM
  questions_count       INT          NOT NULL DEFAULT 4,
  min_followups         INT          NOT NULL DEFAULT 1,
  max_followups         INT          NOT NULL DEFAULT 3,
  answer_time_limit_sec INT          NOT NULL DEFAULT 120,
  access_code           VARCHAR(8)   NOT NULL UNIQUE,
  status                VARCHAR(20)  NOT NULL,      -- DRAFT | PUBLISHED | CLOSED
  created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE exam_topics (                         -- какие темы курса покрывает экзамен (1..N)
  exam_id  BIGINT NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
  topic_id BIGINT NOT NULL REFERENCES course_topics(id) ON DELETE CASCADE,
  PRIMARY KEY (exam_id, topic_id)
);

CREATE TABLE interview_sessions (
  id                BIGSERIAL PRIMARY KEY,
  exam_id           BIGINT      NOT NULL REFERENCES exams(id),
  student_id        BIGINT      NOT NULL REFERENCES users(id),
  status            VARCHAR(20) NOT NULL,           -- IN_PROGRESS | COMPLETED | AI_GRADED | CONFIRMED | ABANDONED
  consent_at        TIMESTAMPTZ NOT NULL,
  current_question_pos INT      NOT NULL DEFAULT 0,
  ai_score          NUMERIC(5,2),
  final_score       NUMERIC(5,2),
  ai_summary        JSONB,                          -- SessionSummary (раздел 8)
  needs_review      BOOLEAN     NOT NULL DEFAULT false,
  reviewed_by       BIGINT      REFERENCES users(id),
  reviewed_at       TIMESTAMPTZ,
  teacher_comment   TEXT,
  started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  finished_at       TIMESTAMPTZ
);

CREATE TABLE session_questions (
  id               BIGSERIAL PRIMARY KEY,
  session_id       BIGINT      NOT NULL REFERENCES interview_sessions(id) ON DELETE CASCADE,
  card_id          BIGINT      NOT NULL REFERENCES question_cards(id),
  position         INT         NOT NULL,
  state            VARCHAR(20) NOT NULL,            -- PENDING | ACTIVE | CLOSED
  followups_asked  INT         NOT NULL DEFAULT 0,
  absent_streak    INT         NOT NULL DEFAULT 0,
  ai_solo_level    INT,                             -- 0..4
  ai_confidence    NUMERIC(3,2),                    -- 0..1
  ai_grade         JSONB,                           -- QuestionGrade целиком
  final_solo_level INT,                             -- ставит преподаватель
  teacher_comment  TEXT
);

CREATE TABLE messages (
  id              BIGSERIAL PRIMARY KEY,
  session_id      BIGINT      NOT NULL REFERENCES interview_sessions(id) ON DELETE CASCADE,
  question_id     BIGINT      REFERENCES session_questions(id) ON DELETE CASCADE,
  author          VARCHAR(10) NOT NULL,             -- AI | STUDENT
  kind            VARCHAR(20) NOT NULL,             -- BASE_QUESTION | FOLLOW_UP | ANSWER | DONT_KNOW | TIMEOUT | SYSTEM
  follow_up_type  VARCHAR(20),                      -- WHY | EXAMPLE | WHAT_IF | FIND_ERROR | HINT
  text            TEXT        NOT NULL,
  input_mode      VARCHAR(10) NOT NULL DEFAULT 'TEXT', -- TEXT | VOICE (фаза 2)
  audio_s3_key    VARCHAR(512),                     -- фаза 2
  response_ms     INT,
  paste_attempts  INT         NOT NULL DEFAULT 0,
  tab_switches    INT         NOT NULL DEFAULT 0,
  turn_decision   JSONB,                            -- сырой TurnDecision, для аудита
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_messages_session ON messages (session_id, created_at);

CREATE TABLE appeals (
  id          BIGSERIAL PRIMARY KEY,
  session_id  BIGINT      NOT NULL REFERENCES interview_sessions(id) ON DELETE CASCADE,
  reason      TEXT        NOT NULL,
  status      VARCHAR(20) NOT NULL,                 -- OPEN | RESOLVED
  resolution  TEXT,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at TIMESTAMPTZ
);

CREATE TABLE llm_calls (                            -- аудит и стоимость, БЕЗ содержимого
  id                BIGSERIAL PRIMARY KEY,
  purpose           VARCHAR(20)  NOT NULL,          -- CARD_GEN | TURN | QUESTION_GRADE | SUMMARY | EMBED | STT
  model             VARCHAR(100) NOT NULL,
  session_id        BIGINT,
  prompt_tokens     INT,
  completion_tokens INT,
  latency_ms        INT,
  ok                BOOLEAN      NOT NULL,
  error             TEXT,
  created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
