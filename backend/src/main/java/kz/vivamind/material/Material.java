package kz.vivamind.material;

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
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;

/** PDF-материал темы курса. Сам файл лежит в S3, фрагменты с эмбеддингами — в vector_store. */
@Entity
@Table(name = "materials")
@Getter
@Setter
@NoArgsConstructor
public class Material {

    public enum Status {UPLOADED, PROCESSING, READY, FAILED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long courseId;
    private Long topicId;
    private String fileName;

    @JsonIgnore
    private String s3Key;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String error;
    private Integer pages;
    private Instant uploadedAt = Instant.now();
}
