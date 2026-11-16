package dev.sg.portfolio.auth;

/** Respuesta del futuro GET /api/auth/session; construir desde la sesión del servidor. */
public record AdminSessionResponse(boolean authenticated, String username) {

    public static AdminSessionResponse anonymous() {
        return new AdminSessionResponse(false, null);
    }
}
