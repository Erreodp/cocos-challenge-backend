# Contexto para continuar el proyecto

Este documento permite retomar el trabajo en otra sesión o equipo. Confirmar
siempre el estado actual del repositorio antes de editar: puede haber cambios
locales del usuario que no deben descartarse ni sobrescribirse.

## Proyecto y requerimiento

API REST de portfolio e inversiones para el challenge Cocos. El requerimiento
original está en [`cocos-challenge`](cocos-challenge), el script de esquema y
datos provistos en [`cocos-db`](cocos-db), y las decisiones del contrato API en
[`API.md`](API.md).

Este repositorio implementa Java 21, Spring Boot 3.5 y SQLite, una elección de
stack aceptada para el challenge. No cambiar de stack sin confirmación del usuario.

## Arquitectura actual

Paquete base: `com.cocos.challenge`.

- `domain`: modelos y política de órdenes, sin dependencias de Spring.
- `application.port.in` y `application.port.out`: casos de uso y puertos.
- `application.service`: implementación/orquestación de casos de uso.
- `application.exception`: errores de aplicación.
- `adapter.in.rest`: controladores REST, DTOs y traducción de errores HTTP.
- `adapter.in.spring`: configuración de composición de dependencias.
- `adapter.out.sqlite`: adaptadores JDBC y creación inicial de la base.

Los cuatro endpoints implementados son búsqueda de instrumentos, lectura de
portfolio, envío de órdenes y cancelación de órdenes. No hay autenticación ni
simulación posterior de órdenes LIMIT.

## Base de datos

`SqliteDatabaseInitializer` carga `docs/cocos-db` al arrancar una base SQLite
nueva. El script provisto usa `SERIAL`, que se adapta a `INTEGER PRIMARY KEY
AUTOINCREMENT` para SQLite. La carga se hace en transacción. Si las cuatro tablas
del challenge ya existen, se conserva la base sin volver a sembrar; si el esquema
está incompleto, el arranque falla con un error explícito.

La ruta predeterminada es `./challenge-cocos.db`; `CHALLENGE_DB_PATH` la puede
reemplazar. El archivo de base local está excluido por `.gitignore`.

## Pruebas y estado de validación

- `SqliteDatabaseInitializerTest`: inicialización, carga única y rechazo de
  esquema parcial.
- `OrderPolicyTest`: políticas unitarias de órdenes.
- `OrderRestControllerIntegrationTest`: ocho flujos HTTP con Spring y SQLite
  temporal: compra MARKET y reflejo en portfolio, cálculo entero desde `amount`,
  compra seguida de venta, compras concurrentes con saldo para una sola,
  rechazos por fondos/tenencia, orden LIMIT cancelada y disponibilidad de Swagger.
- La suite completa (`mvn test`) pasó con Temurin JDK 21.0.13.1 y Maven 3.9.11:
  13 pruebas, sin fallos ni errores. Ambos se descargaron en el directorio
  temporal de la sesión y no quedaron instalados globalmente.
- Para repetir la validación en otro entorno, comprobar que Java 21 y Maven
  estén disponibles y ejecutar `mvn test`.

El portfolio ahora expone `dailyReturnPercent` por posición, calculado como
`(close - previousClose) / previousClose * 100`; `API.md` documenta el caso de
`previousClose` faltante o cero. Swagger UI está en `/swagger-ui/index.html` y
OpenAPI JSON en `/v3/api-docs`. La colección Postman se encuentra en
`docs/postman/Cocos-Challenge.postman_collection.json`.
`docs/API.md` es la referencia funcional completa, y Swagger incluye ejemplos de
requests, esquemas descriptivos y códigos de respuesta/error por endpoint.

El envío de órdenes corre dentro de una transacción y SQLite adquiere un bloqueo
de escritura antes de leer fondos/tenencias para serializar validación y guardado.
El bloqueo se toma con una actualización no-op a la tabla `orders`, sin agregar
tablas técnicas al esquema. La concurrencia está cubierta con dos compras
simultáneas donde solo una dispone de saldo suficiente.

Las pruebas de integración usan una base con nombre aleatorio en el directorio
temporal y la eliminan al terminar.

## Próximos pasos sugeridos

1. Añadir cobertura de integración para búsqueda y validaciones de entrada.
2. Contrastar las reglas restantes del portfolio con el requerimiento y
   documentar cualquier supuesto adicional en `API.md`.

## Cuidado con los cambios locales

En la última revisión, `docs/cocos-db` aparecía como `AM` y
`docs/cocos-challenge` como archivo no rastreado. Son documentos provistos en el
workspace; preservar su contenido y no limpiarlos ni reemplazarlos al continuar.
No asumir que el resto del árbol está limpio: revisar `git status` antes de
empezar.
