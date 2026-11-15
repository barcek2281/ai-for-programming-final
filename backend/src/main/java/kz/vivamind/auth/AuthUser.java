package kz.vivamind.auth;

/** Текущий пользователь из JWT. Контроллеры получают его через @AuthenticationPrincipal. */
public record AuthUser(Long id, Role role) {
}
