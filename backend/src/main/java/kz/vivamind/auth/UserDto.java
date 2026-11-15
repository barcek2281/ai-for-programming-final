package kz.vivamind.auth;

public record UserDto(Long id, String email, String fullName, Role role) {

    public static UserDto of(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }
}
