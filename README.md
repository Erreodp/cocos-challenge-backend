# Challenge Cocos

API REST en Java 21, Spring Boot 3.5 y SQLite, organizada con arquitectura hexagonal.

El requerimiento original está en [`docs/cocos-challenge`](docs/cocos-challenge).
La solución implementa el challenge con Java 21, Spring Boot y SQLite.
El estado y contexto para continuar el trabajo están en [`docs/agent.md`](docs/agent.md).

## Ejecutar

Requiere JDK 21 y Maven.

```bash
mvn spring-boot:run
```

La API escucha en `http://localhost:8080`. Por defecto, SQLite usa
`./challenge-cocos.db`; `CHALLENGE_DB_PATH` permite indicar otra ruta de archivo.
Al iniciar una base nueva, la aplicación crea las tablas y carga los datos de
`docs/cocos-db`. El script original usa `SERIAL`, por lo que la aplicación adapta
esa declaración a la sintaxis SQLite al ejecutarlo. La carga se ejecuta una sola
vez: si ya existe el esquema del challenge, los datos no se vuelven a insertar.
Un esquema parcial detiene el arranque con un error explícito.

## Endpoints

- `GET /api/instruments?query=...`
- `GET /api/users/{userId}/portfolio`
- `POST /api/users/{userId}/orders`
- `POST /api/users/{userId}/orders/{orderId}/cancel`

Las órdenes reciben exactamente uno de `quantity` o `amount`; las órdenes
`MARKET` se ejecutan al último cierre y las `LIMIT` quedan `NEW`. El contrato y
los supuestos de cálculo están en `docs/API.md`.

Swagger UI: `http://localhost:8080/swagger-ui/index.html`. La colección Postman
está en `docs/postman/Cocos-Challenge.postman_collection.json`.
La [referencia detallada de la API](docs/API.md) documenta los campos, fórmulas,
ejemplos de respuestas, errores y estados de órdenes.

## Estructura hexagonal

- `domain`: modelos y políticas de negocio sin dependencias de framework.
- `application.port.in` / `application.port.out`: casos de uso y contratos de persistencia.
- `application.service`: orquestación de casos de uso.
- `application.exception`: errores de aplicación compartidos entre casos de uso y REST.
- `adapter.in.rest`: endpoints y traducción HTTP.
- `adapter.in.spring`: composición de dependencias.
- `adapter.out.sqlite`: persistencia JDBC e inicialización de la base.

Los DTOs HTTP permanecen junto a sus controladores porque solo pertenecen a esa
interfaz; no se agregan clases de mapeo o configuración sin una responsabilidad
independiente.
