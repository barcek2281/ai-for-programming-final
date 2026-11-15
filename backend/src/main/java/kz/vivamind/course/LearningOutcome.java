package kz.vivamind.course;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Цель обучения (LO) темы курса. По ней генерируются карточки вопросов. */
@Entity
@Table(name = "learning_outcomes")
@Getter
@Setter
@NoArgsConstructor
public class LearningOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long topicId;
    private String code;
    private String text;
    private int position;
}
