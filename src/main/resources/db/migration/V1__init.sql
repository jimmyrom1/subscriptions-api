CREATE TABLE plans (
  id                  BIGSERIAL PRIMARY KEY,
  code                VARCHAR(20)  NOT NULL UNIQUE,          -- STANDARD, PREMIUM, METAL
  name                VARCHAR(50)  NOT NULL,
  monthly_price_cents INT          NOT NULL CHECK (monthly_price_cents >= 0),
  active              BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE customers (
  id         BIGSERIAL PRIMARY KEY,
  email      VARCHAR(255) NOT NULL UNIQUE,
  full_name  VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE subscriptions (
  id                   BIGSERIAL PRIMARY KEY,
  customer_id          BIGINT      NOT NULL REFERENCES customers (id),
  plan_id              BIGINT      NOT NULL REFERENCES plans (id),
  status               VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED')),
  started_at           TIMESTAMPTZ NOT NULL,
  -- Número de periodos facturados. El fin de cada periodo se calcula como
  -- started_at + billing_cycle meses: así un alta el día 31 no "deriva" a día 28.
  billing_cycle        INT         NOT NULL DEFAULT 1 CHECK (billing_cycle >= 1),
  current_period_start TIMESTAMPTZ NOT NULL,
  current_period_end   TIMESTAMPTZ NOT NULL,
  cancelled_at         TIMESTAMPTZ,
  version              INT         NOT NULL DEFAULT 0,        -- bloqueo optimista
  CHECK (current_period_end > current_period_start)
);

-- Solo una suscripción activa por cliente, garantizado por la base de datos.
CREATE UNIQUE INDEX one_active_sub_per_customer
  ON subscriptions (customer_id) WHERE status = 'ACTIVE';

-- El scheduler busca las suscripciones activas que han vencido.
CREATE INDEX idx_subscriptions_due
  ON subscriptions (current_period_end) WHERE status = 'ACTIVE';

CREATE TABLE invoices (
  id              BIGSERIAL PRIMARY KEY,
  subscription_id BIGINT      NOT NULL REFERENCES subscriptions (id),
  kind            VARCHAR(20) NOT NULL CHECK (kind IN ('INITIAL', 'RENEWAL', 'PRORATION')),
  -- Puede ser negativo: un downgrade a mitad de periodo genera un abono.
  amount_cents    INT         NOT NULL,
  period_start    TIMESTAMPTZ NOT NULL,
  period_end      TIMESTAMPTZ NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Idempotencia: nunca se factura dos veces el mismo periodo de la misma suscripción.
-- Es parcial a propósito: un prorrateo puede empezar en el mismo instante que un periodo
-- (cambio de plan justo tras el alta) y no es un cobro duplicado.
CREATE UNIQUE INDEX one_invoice_per_period
  ON invoices (subscription_id, period_start) WHERE kind IN ('INITIAL', 'RENEWAL');

CREATE INDEX idx_invoices_subscription ON invoices (subscription_id);
