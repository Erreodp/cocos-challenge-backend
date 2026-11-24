# Decisiones de diseño y reglas de negocio

Este documento resume las decisiones necesarias para interpretar y revisar la
implementación. El detalle de requests, responses y códigos HTTP está en
[API.md](API.md); el alcance de origen está en [cocos-challenge](cocos-challenge).

## Stack y arquitectura

- La implementación usa Java 21, Spring Boot 3.5, JDBC y SQLite.
- El dominio no depende de Spring ni de JDBC. Los casos de uso dependen de
  puertos, y los controladores REST y adaptadores SQLite implementan los bordes
  del sistema.
- Se conserva el esquema provisto. En una base nueva, el inicializador adapta
  `SERIAL` a la sintaxis SQLite y carga el seed una vez. Una base con las cuatro
  tablas existentes se conserva; un esquema parcial impide el arranque.

## Órdenes

- `MARKET` toma el último `close` disponible y pasa a `FILLED` si tiene fondos o
  tenencia suficiente. `LIMIT` usa el precio indicado y pasa a `NEW` si queda
  aceptada. No se simula la ejecución posterior de órdenes LIMIT.
- Se informa exactamente uno de `quantity` o `amount`. La cantidad derivada de
  un monto es `floor(amount / price)`, tanto para compra como para venta; el
  precio aplicable es el cierre de mercado o el límite indicado.
- Montos y precios LIMIT admiten hasta dos decimales. Si la cantidad calculada
  desde `amount` es cero, la solicitud es inválida.
- Una orden que no tiene fondos o acciones suficientes se guarda como
  `REJECTED` y responde `201 Created`, según el contrato del challenge.
- Una compra LIMIT `NEW` reserva efectivo por `quantity * price`; una venta
  LIMIT `NEW` reserva acciones. Las reservas reducen los saldos disponibles,
  pero no alteran efectivo ejecutado, posiciones ejecutadas ni el valor total
  del portfolio. Cancelar libera la reserva.
- El envío se procesa en una transacción. Antes de leer disponibilidad, SQLite
  toma un bloqueo de escritura mediante una actualización no-op; así dos envíos
  concurrentes no pueden validar contra el mismo saldo disponible.

## Portfolio y rendimiento

- Efectivo y posiciones ejecutadas se reconstruyen desde órdenes `FILLED`.
  `CASH_IN` suma efectivo y `CASH_OUT` lo resta; las compras descuentan y las
  ventas acreditan `size * price`.
- El valor de mercado usa la última cotización disponible. Una posición abierta
  sin cotización devuelve `409 Conflict`.
- El rendimiento por posición es
  `(valor de mercado + ventas históricas - compras históricas) / compras
  históricas * 100`. El rendimiento global aplica la misma idea sobre los
  totales de la cuenta. Es una aproximación acumulada, no una TIR ni un cálculo
  por lotes o ponderado por tiempo.
- El retorno diario usa `(close - previousClose) / previousClose * 100`. Si el
  cierre anterior falta o es cero, se informa `0.00`.
- Los porcentajes y montos expuestos se redondean a dos decimales. Las sumas de
  importes de órdenes se hacen con `BigDecimal` en Java, no con agregaciones
  monetarias de punto flotante en SQLite.

## Alcance

No se implementan autenticación, ejecución posterior de órdenes LIMIT ni
simulación de mercado; el challenge los excluye o no los requiere. La API usa
`userId` para identificar la cuenta solicitada.
