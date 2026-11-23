# Challenge Cocos

API REST en Java 21, Spring Boot 3.5 y SQLite, organizada con arquitectura hexagonal.

## Ejecutar

Requiere JDK 21 y Maven.

```bash
CHALLENGE_DB_PATH=/Users/apple1234/Documents/challenge-cocos mvn spring-boot:run
```

La API escucha en `http://localhost:8080`. Si no se define `CHALLENGE_DB_PATH`,
se usa la ruta local conocida del archivo SQLite. La aplicación no crea ni
modifica tablas al iniciar.

## Endpoints

- `GET /api/instruments?query=...`
- `GET /api/users/{userId}/portfolio`
- `POST /api/users/{userId}/orders`
- `POST /api/users/{userId}/orders/{orderId}/cancel`

Las órdenes reciben exactamente uno de `quantity` o `amount`; las órdenes
`MARKET` se ejecutan al último cierre y las `LIMIT` quedan `NEW`. El contrato y
los supuestos de cálculo están en `docs/API.md`.

## Estructura hexagonal

- `domain`: modelos y políticas sin dependencias de framework.
- `application.port.in`: casos de uso.
- `application.port.out`: puertos requeridos por los casos de uso.
- `application.service`: implementación de casos de uso.
- `adapter.in`: entrada REST e integración Spring.
- `adapter.out.sqlite`: persistencia JDBC.
