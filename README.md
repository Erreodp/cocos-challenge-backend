# Challenge Cocos

API REST de portfolios y órdenes de inversión, desarrollada con Java 21,
Spring Boot 3.5 y SQLite, y organizada con arquitectura hexagonal.

## Para revisar la solución

- [Requerimiento original](docs/cocos-challenge)
- [Decisiones y reglas de negocio](docs/decisiones.md)
- [Contrato detallado de la API](docs/API.md)
- [Colección Postman](docs/postman/Cocos-Challenge.postman_collection.json)

La API expone:

| Operación | Endpoint |
|---|---|
| Buscar instrumentos | `GET /api/instruments?query=GGAL` |
| Consultar portfolio | `GET /api/users/{userId}/portfolio` |
| Enviar orden | `POST /api/users/{userId}/orders` |
| Cancelar orden pendiente | `POST /api/users/{userId}/orders/{orderId}/cancel` |

## Ejecutar

Requiere JDK 21 y Maven.

```bash
mvn spring-boot:run
```

La API queda disponible en `http://localhost:8080`. En el primer inicio crea
`./challenge-cocos.db` y carga el esquema y los datos de `docs/cocos-db`.
`CHALLENGE_DB_PATH` permite elegir otra ruta para el archivo. Una base con el
esquema completo se conserva sin volver a sembrar; un esquema parcial detiene el
arranque con un error explícito.

Para correr la suite:

```bash
mvn test
```

La documentación interactiva está en
[`/swagger-ui/index.html`](http://localhost:8080/swagger-ui/index.html) y el
contrato OpenAPI JSON en [`/v3/api-docs`](http://localhost:8080/v3/api-docs).

## Organización del código

- `domain`: modelos y política de órdenes, sin dependencias de Spring.
- `application`: casos de uso, puertos y orquestación.
- `adapter.in.rest`: endpoints, DTOs y traducción de errores HTTP.
- `adapter.out.sqlite`: persistencia JDBC e inicialización de la base.

Los supuestos de cálculo, estados de órdenes, manejo de reservas y concurrencia
están explicados en [Decisiones y reglas de negocio](docs/decisiones.md).
