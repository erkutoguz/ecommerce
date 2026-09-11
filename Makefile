.PHONY: e2e-reset e2e-happy e2e-expiry stripe-listen

e2e-reset:
	bash scripts/e2e/reset.sh

e2e-happy:
	bash scripts/e2e/happy-path.sh

e2e-expiry:
	bash scripts/e2e/payment-expiry.sh

stripe-listen:
	stripe listen --latest --forward-to http://localhost:4002/payments/webhooks/stripe
