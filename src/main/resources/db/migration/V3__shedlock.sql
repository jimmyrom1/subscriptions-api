-- Tabla de ShedLock: una fila por tarea programada. Quien consigue actualizar lock_until es el
-- único que ejecuta la tarea; el resto de instancias se la salta.
--
-- TIMESTAMP (sin zona) a propósito: con usingDbTime() ShedLock escribe y compara
-- timezone('utc', now()), la hora UTC "de pared". Con TIMESTAMPTZ PostgreSQL la reinterpretaría
-- en la zona de la sesión y el bloqueo duraría horas de más o de menos.
CREATE TABLE shedlock (
    name       VARCHAR(64)  PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
