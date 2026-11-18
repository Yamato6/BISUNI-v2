-- =====================================================================
-- Crea el PRIMER administrador en una base nueva SIN datos de demostración
-- (instalación real). Con el seed no hace falta: ya trae admin.master@bisuni.edu.
--
-- Uso (lo corre el dueño de la base, p. ej. el usuario bisuni):
--   psql -h localhost -U bisuni -d bisuni \
--        -v email=direccion@escuela.edu -v nombre=Ana -v apellido="López Ruiz" \
--        -v pass=una-contraseña-temporal -f db/primer-admin.sql
--
-- La contraseña es temporal: el escritorio pide cambiarla en el primer inicio de sesión.
-- El hash bcrypt lo genera pgcrypto (extensión "trusted": la puede crear el dueño de la base).
-- =====================================================================
\set ON_ERROR_STOP on

CREATE EXTENSION IF NOT EXISTS pgcrypto;

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE role = 'Admin') THEN
        RAISE EXCEPTION 'Ya existe un administrador; los demás se crean desde el escritorio (Usuarios).';
    END IF;
END $$;

WITH new_user AS (
    INSERT INTO users (username, password_hash, email, first_name, last_name, role, must_change_password)
    VALUES (split_part(lower(:'email'), '@', 1),
            crypt(:'pass', gen_salt('bf', 10)),
            lower(:'email'),
            :'nombre',
            :'apellido',
            'Admin',
            true)
    RETURNING id_user
)
INSERT INTO admin_profile (id_user)
SELECT id_user FROM new_user;

COMMIT;

SELECT format('Administrador creado: %s (cambia la contraseña al entrar al escritorio)', lower(:'email')) AS listo;
