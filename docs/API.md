# Contrato inicial

API JSON bajo `/api`, sin autenticación. Precios y montos expresados en ARS.

## Búsqueda

`GET /api/instruments?query={texto}` busca coincidencias parciales, sin
distinguir mayúsculas, en ticker y nombre. Devuelve `200 []` sin coincidencias.

## Portfolio

`GET /api/users/{userId}/portfolio` devuelve efectivo, efectivo disponible,
valor total, rendimiento y posiciones. Se usan órdenes `FILLED`; `CASH_IN`
suma pesos, `CASH_OUT` los resta, y compra/venta mueve `size * price`.
Las órdenes LIMIT `NEW` reservan efectivo o acciones.

`totalValue = cashBalance + valor de mercado de las tenencias abiertas`.
El rendimiento es una aproximación: `(valor de mercado + ventas históricas -
compras históricas) / compras históricas * 100`; no es TIR.

## Órdenes

`POST /api/users/{userId}/orders`

```json
{"instrumentId": 47, "side": "BUY", "type": "MARKET", "quantity": 10}
```

`side`: `BUY`/`SELL`; `type`: `MARKET`/`LIMIT`. Informar exactamente uno de
`quantity` o `amount`. `amount` se convierte en acciones enteras por
`floor(amount / precio)`. `LIMIT` requiere `price` positivo; `MARKET` usa el
`close` más reciente y no admite precio en la solicitud. Una orden MARKET
válida queda `FILLED`; una LIMIT válida queda `NEW`. Fondos o tenencia
insuficientes se persisten como `REJECTED` y se informan en la respuesta.
La API no simula la ejecución posterior de órdenes LIMIT.

## Cancelación

`POST /api/users/{userId}/orders/{orderId}/cancel`; solamente cancela órdenes
`NEW`, que pasan a `CANCELLED`.

## Errores

Respuestas de error con `timestamp`, `status`, `error`, `message` y `path`.
Entrada inválida: 400; usuario/instrumento/orden inexistente: 404; conflicto
de estado o cotización faltante: 409.
