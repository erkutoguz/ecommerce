ALTER TABLE payments
    ALTER COLUMN provider_payment_id SET NOT NULL,
    ALTER COLUMN checkout_url SET NOT NULL;
