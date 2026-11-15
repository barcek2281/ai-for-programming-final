package kz.vivamind.appeal;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Апелляция студента на подтверждённую оценку. По её итогам балл нельзя понизить. */
@Entity
@Table(name = "appeals")
@Getter
@Setter
@NoArgsConstructor
public class Appeal {

    public enum Status {OPEN, RESOLVED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;
    private String reason;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String resolution;
    private Instant createdAt = Instant.now();
    private Instant resolvedAt;
}
