# HTTP API

All endpoints return JSON and require a bearer token.

## Create an order

```http
POST /v1/orders
Content-Type: application/json

{ "pickup": "52.52,13.40", "dropoff": "52.50,13.42" }
```

## Track a delivery

```bash
curl -H "Authorization: Bearer $TOKEN" https://orbit.example.com/v1/orders/42/track
```

> [!WARNING]
> Tracking positions are rounded to 50 m before they leave the service.
