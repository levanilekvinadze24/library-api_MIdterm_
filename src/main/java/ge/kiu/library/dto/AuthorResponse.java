package ge.kiu.library.dto;

import ge.kiu.library.entity.Author;

import java.time.LocalDate;

public record AuthorResponse(
        Long id,
        String firstName,
        String lastName,
        String fullName,
        String email,
        String nationality,
        LocalDate birthDate,
        String biography,
        int bookCount
) {
    public static AuthorResponse from(Author author) {
        return new AuthorResponse(
                author.getId(),
                author.getFirstName(),
                author.getLastName(),
                author.getFullName(),
                author.getEmail(),
                author.getNationality(),
                author.getBirthDate(),
                author.getBiography(),
                author.getBooks().size()
        );
    }
}
