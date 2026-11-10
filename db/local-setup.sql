-- =====================================================================
-- Crea el usuario y la base de BISUNI en un PostgreSQL instalado en la máquina
-- (alternativa al Postgres embebido de desarrollo). Se puede ejecutar varias veces.
--
-- Uso (PowerShell, desde la raíz del repo; pedirá la contraseña del usuario postgres):
--   $pw = (Select-String '^DB_PASSWORD=(.*)' .env).Matches[0].Groups[1].Value
--   & "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -U postgres -d postgres `
--       -v user=bisuni -v db=bisuni -v "pass=$pw" -f db/local-setup.sql
--
-- Después, aplicar migraciones y seed:  cd desktop; ./mvnw exec:java -Dexec.mainClass=mx.bisuni.desktop.dev.DbMigrate
-- =====================================================================

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'user', :'pass')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'user') \gexec

-- Si el usuario ya existía, se le actualiza la contraseña a la del .env.
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'user', :'pass') \gexec

SELECT format('CREATE DATABASE %I OWNER %I ENCODING ''UTF8''', :'db', :'user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'db') \gexec
