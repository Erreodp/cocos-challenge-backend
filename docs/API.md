# Referencia de la API

API REST JSON para consulta de activos, portfolios y órdenes del challenge Cocos.
Todos los precios y montos están expresados en pesos argentinos (ARS). No hay
autenticación: `userId` identifica al usuario de las operaciones.

## Inicio rápido

Desde la raíz del proyecto:

```bash
mvn spring-boot:run
```

La API escucha en `http://localhost:8080`. En el primer inicio crea
`./challenge-cocos.db` y carga su esquema y datos iniciales desde `docs/cocos-db`.
Para usar otra ruta, definir `CHALLENGE_DB_PATH` antes de iniciar la aplicación.
Una base existente se conserva y no se vuelve a sembrar.

La documentación interactiva está en
[`/swagger-ui/index.html`](http://localhost:8080/swagger-ui/index.html) y el
contrato OpenAPI JSON en [`/v3/api-docs`](http://localhost:8080/v3/api-docs).
También se puede importar la [colección Postman](postman/Cocos-Challenge.postman_collection.json).

## Convenciones

- Los endpoints están bajo `/api` y reciben/devuelven JSON donde corresponde.
- Los IDs deben identificar registros existentes.
- Fechas de órdenes se serializan como fecha y hora ISO-8601 local, por ejemplo
  `2026-11-23T16:30:00`.
- Los parámetros `userId`, `instrumentId` y `orderId` son números enteros.
- Los errores tienen el formato común documentado en [Errores](#errores).

## Buscar instrumentos

`GET /api/instruments?query={texto}`

Busca coincidencias parciales, sin distinguir mayúsculas, en ticker y nombre.
`query` es obligatorio y no puede estar vacío. Devuelve una lista vacía con
`200 OK` cuando no encuentra coincidencias. `lastPrice` y `previousClose` pueden
ser `null` si el instrumento no tiene cotización.

Ejemplo:

```http
GET /api/instruments?query=GGAL
```

```json
[
  {
    "id": 47,
    "ticker": "GGAL",
    "name": "Grupo Financiero Galicia",
    "type": "ACCIONES",
    "lastPrice": 925.85,
    "previousClose": 921.8
  }
]
```

Respuestas: `200 OK`, `400 Bad Request` si falta o está vacío `query`.

## Consultar portfolio

`GET /api/users/{userId}/portfolio`

El efectivo se calcula desde órdenes `FILLED`: `CASH_IN` suma, `CASH_OUT` resta,
las compras descuentan `size * price` y las ventas acreditan ese importe.
`availableCash` resta al efectivo las órdenes de compra LIMIT que siguen `NEW`.

Solo se consideran órdenes `FILLED` para las posiciones. La cantidad abierta es
la suma de compras menos ventas; las órdenes de venta LIMIT `NEW` se restan de
`availableQuantity`, pero no modifican la posición ejecutada.

Fórmulas:

```text
marketValue = quantity * close
totalValue = cashBalance + suma(marketValue de posiciones abiertas)
profitLoss = marketValue + ventas históricas - compras históricas
returnPercent = profitLoss / compras históricas * 100
dailyReturnPercent = (close - previousClose) / previousClose * 100
```

`returnPercent` es una aproximación acumulada por posición basada en los
movimientos históricos; no es una TIR ni un rendimiento ponderado por tiempo.
`dailyReturnPercent` usa la última cotización disponible. Si `previousClose` es
nulo o cero, devuelve `0.00`. Los porcentajes se redondean a dos decimales.
`totalValue` no incluye órdenes LIMIT `NEW` como posiciones o movimientos de
efectivo, aunque estas órdenes sí reservan efectivo o acciones disponibles.

Ejemplo ilustrativo:

```json
{
  "userId": 1,
  "accountNumber": "10001",
  "currency": "ARS",
  "totalValue": 124997.00,
  "cashBalance": 106480.00,
  "availableCash": 101880.00,
  "returnPercent": 2.87,
  "positions": [
    {
      "instrumentId": 47,
      "ticker": "GGAL",
      "name": "Grupo Financiero Galicia",
      "quantity": 20,
      "availableQuantity": 20,
      "marketPrice": 925.85,
      "marketValue": 18517.00,
      "profitLoss": 517.00,
      "returnPercent": 2.87,
      "dailyReturnPercent": 0.44
    }
  ]
}
```

Respuestas: `200 OK`, `404 Not Found` si no existe el usuario y `409 Conflict`
si una posición abierta no tiene cotización disponible.

## Enviar una orden

`POST /api/users/{userId}/orders`

El cuerpo debe incluir `instrumentId`, `side` y `type`, y exactamente uno de
`quantity` o `amount`.

| Campo | Requisito | Descripción |
|---|---|---|
| `instrumentId` | Obligatorio | ID de un instrumento que no sea `MONEDA`. |
| `side` | Obligatorio | `BUY` o `SELL`. |
| `type` | Obligatorio | `MARKET` o `LIMIT`. |
| `quantity` | Excluyente con `amount` | Cantidad entera positiva de acciones; no se admiten fracciones. |
| `amount` | Excluyente con `quantity` | Monto positivo en ARS con hasta dos decimales; calcula `floor(amount / precio)` acciones. |
| `price` | Condicional | Obligatorio y positivo, con hasta dos decimales, para `LIMIT`; debe omitirse para `MARKET`. |

Para una orden `MARKET`, el precio usado es el `close` más reciente. Para una
orden `LIMIT`, se usa el `price` solicitado. `MARKET` se registra como `FILLED`;
`LIMIT` se registra como `NEW`. No se simula la ejecución posterior de órdenes
LIMIT.

Los importes de entrada se limitan a dos decimales, de acuerdo con la precisión
monetaria del esquema provisto. Los totales de órdenes se acumulan con
`BigDecimal` en la aplicación para evitar sumas monetarias en punto flotante de
SQLite.

La validación de fondos/tenencias considera el saldo y la posición ejecutados,
menos las reservas de otras órdenes LIMIT `NEW`. Si no hay fondos o acciones
suficientes, la orden igual se persiste con estado `REJECTED` y se devuelve con
`201 Created`; revisar `status` y `rejectionReason` en el cuerpo.
Los envíos se procesan transaccionalmente y se serializan en SQLite para impedir
que órdenes simultáneas validen contra el mismo saldo o tenencia disponible.

Ejemplo de compra MARKET por cantidad:

```http
POST /api/users/1/orders
Content-Type: application/json
```

```json
{
  "instrumentId": 47,
  "side": "BUY",
  "type": "MARKET",
  "quantity": 10
}
```

Ejemplo de orden LIMIT:

```json
{
  "instrumentId": 47,
  "side": "BUY",
  "type": "LIMIT",
  "amount": 10000,
  "price": 900
}
```

Respuesta de orden ejecutada:

```json
{
  "id": 101,
  "instrumentId": 47,
  "userId": 1,
  "quantity": 10,
  "price": 925.85,
  "type": "MARKET",
  "side": "BUY",
  "status": "FILLED",
  "datetime": "2026-11-23T16:30:00",
  "rejectionReason": null
}
```

Respuesta de orden rechazada por fondos insuficientes (también `201 Created`):

```json
{
  "id": 102,
  "instrumentId": 47,
  "userId": 1,
  "quantity": 10,
  "price": 925.85,
  "type": "MARKET",
  "side": "BUY",
  "status": "REJECTED",
  "datetime": "2026-11-23T16:30:00",
  "rejectionReason": "Fondos insuficientes: disponibles 1, requeridos 9258.50"
}
```

Respuestas: `201 Created` para órdenes aceptadas o rechazadas por reglas de
fondos/tenencias; `400 Bad Request` para campos inválidos; `404 Not Found` si no
existe el usuario o instrumento; `409 Conflict` si no hay una cotización válida.
La falta de fondos/tenencias no es un error HTTP: queda registrada como `REJECTED`.

## Cancelar una orden

`POST /api/users/{userId}/orders/{orderId}/cancel`

Solo se cancelan órdenes `NEW`. La actualización en base condiciona el cambio al
estado `NEW` para evitar cancelar una orden que haya cambiado de estado entre la
lectura y la escritura; si ya no está `NEW`, responde `409 Conflict`.

Respuesta `200 OK`:

```json
{
  "id": 103,
  "instrumentId": 47,
  "userId": 1,
  "quantity": 10,
  "price": 900,
  "type": "LIMIT",
  "side": "BUY",
  "status": "CANCELLED",
  "datetime": "2026-11-23T16:31:00",
  "rejectionReason": null
}
```

Respuestas: `200 OK`, `404 Not Found` si el usuario u orden no existe o no
pertenece al usuario, `409 Conflict` si la orden no está `NEW`.

## Errores

Las respuestas de error usan el formato:

```json
{
  "timestamp": "2026-11-23T16:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Debe informar exactamente uno de quantity o amount",
  "path": "/api/users/1/orders"
}
```

| Código | Uso |
|---|---|
| `400 Bad Request` | Parámetros o cuerpo inválido, campos requeridos ausentes, enum inválido o `quantity`/`amount` incorrectos. |
| `404 Not Found` | Usuario, instrumento u orden inexistentes. |
| `409 Conflict` | Cotización requerida ausente o inválida; orden no cancelable o con cambio de estado concurrente. |
| `201 Created` con `status: REJECTED` | Orden persistida pero rechazada por falta de efectivo o acciones disponibles. |
