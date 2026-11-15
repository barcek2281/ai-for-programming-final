package kz.vivamind.course;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Тема (раздел, неделя) курса. К ней привязаны материалы, цели обучения и карточки вопросов. */
@Entity
@Table(name = "course_topics")
@Getter
@Setter
@NoArgsConstructor
public class CourseTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long courseId;
    private String title;
    private String description;
    private int position;
}
