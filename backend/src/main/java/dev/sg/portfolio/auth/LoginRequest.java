package dev.sg.portfolio.auth;

/** Entrada del futuro POST /api/auth/login. No autentica por sí misma. */
public record LoginRequest(String username, String password) {

    // Evita incluir la contraseña al registrar accidentalmente este objeto.
    @Override
    public String toString() {
        return "LoginRequest[credentials=REDACTED]";
    }
}
