package dev.sg.portfolio.auth;

/** Respuesta uniforme de login fallido; no revela si el usuario existe o si falta configuración. */
public record AdminLoginErrorResponse(boolean authenticated, String username, String message) {

    public static AdminLoginErrorResponse invalidCredentials() {
        return new AdminLoginErrorResponse(false, null, "Credenciales inválidas.");
    }
}
