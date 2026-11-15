package kz.vivamind.common;

import org.springframework.http.HttpStatus;

/** Функция запланирована (фаза 2), но в MVP не работает → HTTP 501. */
public class FeatureDisabledException extends ApiException {

    public FeatureDisabledException(String feature) {
        super(HttpStatus.NOT_IMPLEMENTED, "Функция «" + feature + "» в разработке");
    }
}
