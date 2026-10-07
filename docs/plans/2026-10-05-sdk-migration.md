# Plan de migración de los SDK — java-sdk

**Objetivo:** reorganizar los 10 SDK (separar, renombrar, retirar), unificar prefijos de propiedades
bajo `codehunters.*`, corregir los hallazgos de la revisión del 2026-10-05 y publicar todo alineado
por un `sdk-bom`.

**Enfoque:** primero los cimientos del build, que desbloquean el resto. Después, una versión mayor
por módulo que agrupa en un solo release su separación, su renombre, sus correcciones y sus nuevos
prefijos, para que cada consumidor migre una sola vez por módulo. Al final, el BOM fija el conjunto.

**Stack:** Java 21, Spring Boot 4.0.2, Gradle Kotlin DSL, JUnit 6 (vía BOM de Boot), Testcontainers
2.x, GitHub Packages.

**Tipo de documento:** hoja de ruta. Cada fase genera su propio plan detallado con TDD (test que
falla, implementación mínima, test en verde, commit) en `docs/plans/<fecha>-<fase>.md` **al
empezar esa fase**. El contenido de los tests se escribe entonces, contra el código de ese momento.

---

## Decisiones aprobadas (2026-10-05)

| # | Decisión | ADR |
|---|---|---|
| D1 | Partir `sdk-event-messaging` en `-core`, `-jdbc`, `-rabbit` y `-kafka` | `docs/adr/0001-event-messaging-split.md` |
| D2 | Sacar el PEP de autorización a `sdk-user-security-authz` | `docs/adr/0002-user-security-authz-split.md` |
| D3 | Retirar `sdk-geo-validation` del SDK y moverlo al servicio de rifas | `docs/adr/0003-geo-validation-out.md` |
| D4 | Un solo prefijo, `codehunters.<sdk>.*`, con alias deprecados por una versión mayor | `docs/adr/0004-property-prefix.md` |
| D5 | Renombrar `sdk-utils` a `sdk-totp` | `docs/adr/0005-utils-to-totp.md` |
| D6 | Crear `sdk-bom` (`java-platform`) y dejar de exportar el BOM de Boot a los consumidores | `docs/adr/0006-sdk-bom.md` |
| D7 | Solo Spring Boot 4. Los `afterName` apuntan solo a paquetes de Boot 4 y el README lo declara (2026-10-05) | `docs/adr/0007-boot4-only.md` |
| D9 | `user-security` 3.0 verifica el JWT localmente (JWKS); los headers del gateway solo se aceptan si coinciden (2026-10-05) | (en el CHANGELOG del módulo) |
| D8 | Mantener `@Measured` y su aspecto propio; no se reemplaza por `@Timed`/`@Counted` de Micrometer (2026-10-05) | `docs/adr/0008-keep-measured.md` |
| D10 (A4) | `file-share` se autentica con client credentials por defecto; un bean `FileShareRequestInterceptor` lo reemplaza (2026-10-06) | `docs/adr/0009-file-share-outbound-client-credentials.md` |

## Decisiones abiertas (bloquean la fase indicada)

| # | Pregunta | Recomendación | Bloquea |
|---|---|---|---|
| A5 | Modelo mutable de `event-messaging` (`@Setter` en `OutboxMessage` y `EventMessage`) | Fuera de este plan; se aborda en la 7.0 | — |

---

## Restricciones globales

- Cada versión mayor agrupa **todos** los cambios incompatibles de su módulo. No hay dos mayores
  seguidas del mismo módulo dentro de este plan.
- Los paquetes Java **no cambian** al partir un módulo (D1, D2). Solo cambia el artefacto. Excepción:
  `io.codehunters.sdk.security` pasa a `io.codehunters.security` en la 3.0.0, porque esa versión ya
  rompe la API.
- Cuando un artefacto se reemplaza por otro, el nuevo declara la *capability* Gradle del viejo. Así
  Gradle rechaza tener ambos jars en el classpath (ver Fase 1, tarea 1.6).
- Alias de propiedades viejas: un `EnvironmentPostProcessor` por módulo copia la clave vieja a la
  nueva si la nueva no está definida, y emite un WARN una sola vez. Cada alias se declara deprecado en
  `META-INF/additional-spring-configuration-metadata.json`, con su `replacement`. Se eliminan en la
  siguiente versión mayor.
- Cobertura JaCoCo: LINE ≥ 0.90 y BRANCH ≥ 0.80 (desde la Fase 1). Un módulo por debajo baja su umbral
  con `coverage.branch.minimum` en su `gradle.properties` hasta su siguiente mayor.
- Commits Conventional, uno por asunto, con el test antes de la implementación. Cada fase en su rama
  `feature/sdk-<módulo>-vN` con PR hacia `develop`.

## Definición de terminado por módulo (aplica a las Fases 2 a 10)

Cada fase de módulo debe cumplir todo esto además de sus tareas propias:

- [ ] La autoconfiguración usa clases `@Configuration(proxyBeanMethods = false)` anidadas, con
      `@ConditionalOnClass` **a nivel de clase**, para cada dependencia opcional.
- [ ] Los beans de tipos de infraestructura (`JsonMapper`, `Executor`, `JdbcClient`, `RestClient`,
      `MessageConverter`) son `@Bean(defaultCandidate = false)` y se inyectan con `@Qualifier`.
- [ ] `@ConditionalOnMissingBean` en cada bean público del SDK.
- [ ] Toggle `codehunters.<sdk>.enabled` (`@ConditionalOnBooleanProperty`, `matchIfMissing = true`).
- [ ] Ningún `@EnableAsync`, `@EnableScheduling` ni `@EnableAspectJAutoProxy` sin condición. Si es
      necesario, va detrás de una propiedad y queda documentado.
- [ ] Propiedades con prefijo `codehunters.<sdk>`, record con validación en el constructor compacto,
      y el `EnvironmentPostProcessor` de alias registrado en `META-INF/spring.factories`.
- [ ] Tests de autoconfiguración: `FilteredClassLoader` sin cada dependencia opcional; arranque
      junto con la autoconfiguración real de Boot que corresponda (Jackson, TaskExecution, JDBC,
      Metrics); el bean del consumidor desplaza al nuestro; `enabled=false`; una clave vieja produce
      el mismo binding que la nueva.
- [ ] Las dependencias usadas en firmas públicas van en `api`; las opcionales, en `compileOnly`.
- [ ] `README.md` del módulo con propiedades y valores por defecto, dependencias que el consumidor
      debe traer, la tabla de migración desde la versión anterior y su semántica de fallos.
- [ ] `CHANGELOG.md` **del módulo** (el raíz solo enlaza) y bump de `version` en su `build.gradle.kts`.
- [ ] Test ArchUnit del módulo si tiene capas `domain`/`application`.
- [ ] `./gradlew :<módulo>:check` en verde.

---

## Fase 0 — Cerrar lo que está en curso ✅

- [x] `e0ad9c2 feat(event-messaging): add publisher, claim dialect and purge properties`. Es el
      paso GREEN de `4fcd891`. El cableado de Kafka, el dialecto y el purge se hace en la Fase 3,
      sobre los módulos ya separados, para no hacerlo dos veces.

## Fase 1 — Cimientos del build ✅ (rama `feature/build-foundations`)

Ningún módulo cambió de versión. Las clases de los 10 jars son idénticas byte a byte a la línea base
(comparación de CRC tras `publishToMavenLocal`).

| Tarea | Commit | Resultado |
|---|---|---|
| 1.0 (nueva) | `9189cab` | **Bug encontrado al tomar la línea base:** todos los artefactos se publicaban con versión `unspecified`, porque `publications` leía `project.version` antes de que cada módulo la asignara. Se usan los valores por defecto perezosos de la publicación |
| 1.1 + 1.2 | `3c54664` | `java-library`; BOM de Boot en la configuración no publicable `bootPlatform`; `versionMapping` publica versiones concretas. Los POM ya no importan `spring-boot-dependencies` |
| 1.3 + 1.7 | `653eb9a` | Fuera el BOM de JUnit 5 (Boot resuelve JUnit 6.0.2) y las propiedades sin uso. El IT de SQL Server pasa a Testcontainers 2.x, con `azure-sql-edge:1.0.7` fijada (se mantiene azure-sql-edge por ARM). **Cambio respecto al plan:** sin source set `integrationTest` ni `disabledWithoutDocker`. Las ITs aportan cobertura, así que sin Docker el gate de JaCoCo fallaría igual; Docker queda como requisito del build, documentado en el README |
| 1.4 | `f2754c9` | **Cambio respecto al plan:** `BRANCH ≥ 0.80` global desde ya, no 0.60. Solo `sdk-measure-micrometer` (0.63) baja a 0.60 con `coverage.branch.minimum` en su `gradle.properties`. Sonar excluye lo mismo que JaCoCo (`*Properties`) |
| 1.5 | `a232bac` | `pom {}` con name, description (una por módulo), url, licencia MIT y scm. La URL vive en la propiedad `repository.url` |
| 1.8 | `0428b8c` | `onlyIf` en cada tarea `PublishToMavenRepository`: `HEAD` autenticado al POM; 200 → omite, 404 → publica, otro código → falla. Omite SNAPSHOT y falla si la versión es `unspecified`. Probado contra un registro falso local. El workflow no cambia (`build publish`) |
| 1.9 | `a0af28f` | `sdk-bom` (`java-platform`, `1.0.0-SNAPSHOT`, no se publica todavía). La publicación compartida pasa a un bloque común para librerías y BOM |
| 1.10 | `4236a6f` | ADR 0001–0008 en `docs/adr/` con índice; README con el requisito de Boot 4 y de Docker |
| 1.11 | — | `codehuntersio-java-commons-support/` agregado a `.git/info/exclude`. **Moverlo fuera del árbol queda pendiente de ti**: es un repo git propio |
| 1.6 | — | **Movida a la Fase 3** (tarea 3.0). El helper de capability se escribe junto a su primer uso real, con su test |

**Pendiente de confirmar:** el repo `codehunters/java-sdk` no existe en GitHub, o el token local no
tiene acceso. `github_packages_url` y `repository.url` apuntan ahí. Si la org es `Codehunters-IO`,
hay que cambiar las dos propiedades en `gradle.properties`.

## Fase 1b — Reestructuración del repo ✅ (rama `feature/build-logic`)

Decidida el 2026-10-05 tras el análisis de estructura: hacerla **antes** de partir módulos para no
moverlos dos veces.

| Commit | Qué |
|---|---|
| `13645d7` (en `feature/build-foundations`) | **Bug:** `gradle-wrapper.jar` nunca estuvo versionado (un `*.jar` posterior anulaba la excepción); `./gradlew` fallaba en un clon limpio y en CI |
| `5224ae9` | `build-logic/` (included build) con `codehunters.java-library` y `codehunters.publishing`; versiones en `gradle/libs.versions.toml`. El build raíz queda solo con Sonar. Artefactos idénticos salvo `spring-configuration-metadata.json` de event-messaging, que ahora trae las descripciones completas |
| `33c57a5` | Familias agrupadas: `messaging/sdk-event-messaging`, `security/sdk-user-security`, `platform/sdk-bom`. Nombres de proyecto y artefacto sin cambios |
| `681cada` | CI sin `--scan`, triggers `chore/**` y `docs/**`; eliminado el workflow del agente |

Ubicación de los módulos nuevos: `security/sdk-user-security-authz` (Fase 2),
`messaging/sdk-event-messaging-{core,jdbc,rabbit,kafka}` (Fase 3). `sdk-totp` queda en la raíz.

Decidido y no hecho: un plugin `codehunters.spring-autoconfig` (no aporta mientras todos los módulos
son autoconfiguraciones; lo cubre `codehunters.java-library`); un módulo público de soporte de tests
(se usa `java-test-fixtures` en `-core` en la Fase 3).

## Fase 2 — `sdk-user-security` 3.0.0 + `sdk-user-security-authz` 3.0.0 ✅ (rama `feature/sdk-user-security-v3`)

Hecha según D9. Resumen (detalle en `security/*/CHANGELOG.md` y `README.md`):

- `147a0bf` paquete `io.codehunters.security`; `ca6567f` PEP extraído a `security/sdk-user-security-authz`.
- `95c9b9b` → `0f2dc89` verificación local del JWT (RS256, issuer, sub, audiencia, timestamps), introspección
  que falla cerrada con timeouts y caché acotada por hash, `GatewayHeadersFilter` que exige que
  `x-user-id`/`x-user-roles` coincidan con el token, propiedades `codehunters.security` validadas.
- `74e493c` → `431f0af` alias de claves 2.x (`EnvironmentPostProcessor` + `LegacyPropertyAliases`).
- `9eb8a68` → `c700eb5` PEP: cliente perezoso, deniega métodos fuera del catálogo, patrón más específico,
  403/503, modos `ENFORCING`/`PERMISSIVE`/`DISABLED`, keycloak-authz-client 26.0.12.
- `4b4c43e` (build) la metadata adicional ahora se fusiona; `4094892` README, CHANGELOG, metadata de deprecación.
- Revisión independiente → `a2ba265` tests, `733f739` y `f5aee60` correcciones: alias con variables de entorno
  (crasheaba el arranque), `audiences` obligatorio, `issuer-uri`/`jwk-set-uri`, JWKS cacheado y con límite
  de frecuencia (un `kid` desconocido forzaba una descarga por petición), timeouts y back-off del PEP,
  orden determinista de alias, `method-security.enabled`.

Verificación: 99 tests en la base (matriz de 18 ataques de punta a punta), 22 en el PEP; pruebas de mutación
sobre cada defensa; `./gradlew clean build` en verde.

**Pendiente fuera del repo:** el gateway debe seguir eliminando los `x-user-*` entrantes (ahora es
defensa en profundidad, no la única barrera); cada servicio debe configurar `codehunters.security.audiences`
con su client id y revisar que el realm le otorga los roles que antes llegaban solo por header.

## Fase 3 — `sdk-event-messaging-*` 6.0.0 ✅ (rama `feature/sdk-event-messaging-v6`, sobre `feature/sdk-tracing-v1`)

Detalle en `messaging/*/CHANGELOG.md`, `messaging/*/README.md` y `messaging/MIGRATION-6.0.md`.
Cada unidad es un par test (RED) → implementación (GREEN):

| Commits | Qué |
|---|---|
| `7f18b2d` → `90c234a` | Tarea 3.0: `codehuntersPublishing { replaces(...) }` declara la capability del artefacto viejo |
| `a4e5848` | `LegacyPropertyAliases` pasa al módulo interno `platform/sdk-support` |
| `6e9a19b` | Split en `-core`, `-jdbc`, `-rabbit`, `-kafka` con `git mv`, sin cambios de comportamiento |
| `5bded85` → `e49e96f` | Prefijo `codehunters.outbox.*`; `OutboxLegacyProperties` mantiene `outbox.*` hasta la 7.0 |
| `6580f3e` → `febfe3c` | Validación de propiedades al arrancar y chequeo del presupuesto del claim |
| `b625c7f` → `43fed65` | Reclamo agotado → `FAILED`; el lote corta a `claimTtl/2` y libera el resto a `PENDING` |
| `155f9c8` → `48e7320` | Timer `outbox.publish` vía `MessagePublisherDecorator` |
| `297790a` → `7ae4c0d` | `traceId` por defecto desde `TraceContextPort` (`TraceIdSource`) |
| `3f6d5b3` → `a52a886` | `outboxJsonMapper` con `defaultCandidate = false` |
| `6726153` → `af44512` | El reclamo de una fila IN_FLIGHT vencida suma un intento (3 dialectos) |
| `c8606a8`, `79c31c8`, `96c0865` | ArchUnit en `-core`; javadoc; formato |
| `b225bf0` → `dd4c35e` | `OUTBOX_ID_HEADER` pasa a `MessagePublisherPort` |
| `289d8cb` → `6c6068e` | `publisher.rabbit.confirm-timeout` (5 s) y presupuesto del claim también para RabbitMQ |
| `5350b66` → `e49b70b` | `@ConditionalOnOutboxTransport`: el artefacto presente elige el transporte |
| `af4e695` → `d8e4170` | RabbitMQ con confirms, `mandatory` y `outboxRabbitTemplate` dedicado |
| `74b4755` → `cab4328` | Kafka con productor idempotente dedicado (`outboxKafkaTemplate`) |
| (esta rama) | README, CHANGELOG y guía de migración por módulo; CI corre `./gradlew -p build-logic test` |

**Cambios respecto al plan:**

- Módulo interno nuevo `platform/sdk-support` (no publicado) para compartir `LegacyPropertyAliases`
  con security sin depender de ese módulo.
- `publisher.kafka.send-timeout` baja de 10 s a 5 s, para que la configuración por defecto
  (50 × 5 s < 300 s) pase el chequeo del presupuesto. RabbitMQ suma `confirm-timeout` y el mismo
  chequeo.
- Punto de extensión público `MessagePublisherDecorator` (el timer es un decorator más).
- La selección del transporte vive en `@ConditionalOnOutboxTransport`; cada autoconfiguración de
  transporte valida su propio presupuesto con `requireClaimBudget`.
- El scheduler de dispatch va en su propia autoconfiguración (`OutboxDispatchSchedulerAutoConfiguration`),
  porque necesita correr después de `-core` y el resto de `-jdbc`, antes.
- Kafka: además del template, `outboxKafkaProducerFactory` derivado del `ProducerFactory` de la app;
  se exige un `ProducerFactory`, no un `KafkaTemplate`.
- Tarea 6 recortada: solo el timer `outbox.publish` (tags `outcome`, `exception`); no hay
  `outbox_published_total` ni `outbox_publish_failures_total{final}`.
- Tarea 7 recortada: sin respaldo por la clave MDC cuando `sdk-tracing` no está; el outbox guarda el
  `traceId` en el header del sobre, no el `traceparent` completo.
- Tarea 2: `OutboxFailedGauge` no está cacheado; consulta la tabla en cada scrape.
- No se usó `java-test-fixtures` en `-core`: los tests de `-rabbit` y `-kafka` dependen de los otros
  módulos en `testImplementation`.
- `-jdbc` activa `@EnableScheduling` y `@EnableSchedulerLock` siempre que `codehunters.outbox.enabled`
  sea `true`, sin propiedad propia (queda documentado en su README).

Verificación: 253 métodos `@Test` en los cuatro módulos; `./gradlew build` en verde.

### Plan original

**Estructura** (los paquetes `io.codehunters.messaging.*` se mantienen):

| Artefacto | Contenido | Dependencias |
|---|---|---|
| `sdk-event-messaging-core` | `domain/**`, `application/**`, `MessagePublishException` y propiedades comunes (`origin`) | `api` slf4j; `compileOnly` Jackson, `sdk-tracing` |
| `sdk-event-messaging-jdbc` | `drivenadapters/jdbc/**`, `entrypoint/scheduler/**`, `metrics/OutboxFailedGauge`, SQL de `db/migration/outbox/**` y su autoconfiguración | `api` core; `api` ShedLock; `compileOnly` spring-jdbc, spring-tx, micrometer |
| `sdk-event-messaging-rabbit` | `drivenadapters/rabbitmq/**` y su autoconfiguración | `api` core; `compileOnly` spring-rabbit |
| `sdk-event-messaging-kafka` | `drivenadapters/kafka/**` y su autoconfiguración | `api` core; `compileOnly` spring-kafka |

**Tarea 3.0:** extensión `replaces("<artefacto viejo>")` en el plugin `codehunters.publishing`, que agrega
`outgoing.capability("io.codehunters:<viejo>:<version>")` a `apiElements` y `runtimeElements` además de
la propia. Test: un proyecto de prueba que pide `sdk-event-messaging:5.1.5` y `-core:6.0.0` falla la
resolución. Los contenedores compartidos por `-jdbc` y `-kafka` van como `java-test-fixtures` de `-core`.

`-core` declara la capability `io.codehunters:sdk-event-messaging`. El transporte lo decide el
artefacto presente. `codehunters.outbox.publisher.type` (`rabbit` | `kafka`, validado en el
constructor compacto) solo hace falta para desempatar cuando están los dos artefactos. Si están los
dos y la propiedad no está definida, el arranque falla con un mensaje explícito.

**Prefijo:** `outbox.*` → `codehunters.outbox.*` (incluye `outbox.task.*` y los placeholders de
`@Scheduled` del purge).

**Tareas propias:**
1. Mover las clases a los 4 módulos (commit de solo movimiento, sin cambios). Los tests se mueven
   con su clase.
2. `-jdbc`: `OutboxDialectResolver.resolve(ds, props.claim().dialect())`; `OutboxPurgeScheduler`
   con `OutboxPurgeSettings`; `OutboxFailedGauge` cacheado (el valor se refresca cada N segundos,
   no en cada scrape).
3. `-jdbc`: un reclamo de fila IN_FLIGHT vencida incrementa `attempts` (3 dialectos). Al arrancar,
   se valida que `claim.ttl > batchSize × sendTimeout`. El caso de uso corta el lote cuando pasa
   `claimTtl/2`.
4. `-rabbit`: publisher confirms (`waitForConfirmsOrDie`), `mandatory=true`, override de
   `publish(OutboxMessage)` con `messageId` y header `outbox-message-id`. Se eliminan los beans
   globales `JacksonJsonMessageConverter` y `RabbitTemplate`, y el MPP de traza (ahora es de
   `sdk-tracing`).
5. `-kafka`: `KafkaTemplate` dedicado `outboxKafkaTemplate` (`StringSerializer`, `acks=all`,
   `enable.idempotence=true`), con `defaultCandidate = false`.
6. `-core`: decorator `MeteredMessagePublisher` (`outbox_published_total`,
   `outbox_publish_failures_total{final}`, timer `outbox_publish`).
7. `-core`: `traceId` desde `TraceContextPort` si `sdk-tracing` está presente; si no, de la clave
   MDC pública de `sdk-tracing`, copiada como constante.
8. Test ArchUnit en `-core`: `domain` y `application` sin Spring, JDBC, AMQP ni Kafka.
9. README: dialectos, DDL a copiar (y que el `seq` de SQL Server debe aplicarse **antes** del
   deploy), semántica at-least-once, cómo deduplicar con `outbox-message-id`, y bloqueo head-of-line
   por clave.

**Migración del consumidor:** `sdk-event-messaging:5.x` → `sdk-event-messaging-jdbc` +
`sdk-event-messaging-rabbit` (o `-kafka`). Prefijo `outbox.*` → `codehunters.outbox.*`. DDL `seq` en
SQL Server.

**Revisión independiente** (`26630df` → `4f40ab4`): publish de Kafka acotado por `send-timeout`
(`max.block.ms`, `delivery.timeout.ms`, `request.timeout.ms`, `linger.ms=0`); config idempotente
corregida y validada al arrancar; factory del outbox con los customizers de Boot y `client.id` propio;
`task.lock-at-most` por defecto 300 s y validado contra la duración de un despacho; MySQL/MariaDB fallan
al arrancar; presupuesto del claim con aritmética exacta; Rabbit acepta confirms `CORRELATED`, usa
conexión de publicación y conserva la causa original; gauge de FAILED cacheado 30 s;
`codehunters.outbox.scheduling.enabled`. Límites conocidos documentados (intentos sumados a todo el lote
tras un crash, hasta 7.0; consumidores Maven sin protección por capability → usar `sdk-bom`).

Verificación final: core 116, jdbc 110, rabbit 35, kafka 41 tests; líneas 100% en los cuatro;
`./gradlew clean build` y `./gradlew -p build-logic test` en verde.

## Fase 4 — `sdk-tracing` 1.0.0 ✅ (rama `feature/sdk-tracing-v1`, sobre `feature/sdk-user-security-v3`)

Detalle en `sdk-tracing/CHANGELOG.md` y `README.md`.

- `88337e2` → `55c658f`: `TraceParent` (W3C), `TraceHeaders`, `traceparent` primero con `x-traceparent` /
  `x-trace-id` como respaldo, span hijo por servicio, flag de muestreo en el MDC (`traceFlags`),
  `TraceContextPort` con `Optional` y `currentTraceParent()`, Rabbit sin bean `JsonMapper`, BPP estático
  y perezoso sin duplicar el post-processor, `codehunters.tracing.enabled`.
- `3ecfc96`, `21d0513`: README, CHANGELOG, 1.0.0 y metadata de configuración.
- Revisión independiente → `9b9b0b6`: MDC con solo `traceId` mantiene el trace; dispatch async y de error
  con el mismo trace; listeners batch; headers en otra capitalización o `byte[]`. **Cambio respecto al
  plan:** el filtro no se aparta por la clase de Micrometer (Boot registra un `Tracer` no-op sin puente);
  se ordena justo después del filtro de observación de Micrometer y respeta un MDC ya poblado.

Verificación: 64 tests, mutaciones detectadas, `./gradlew clean build` en verde.

Para la Fase 3: `event-messaging` debe usar `TraceContextPort.currentTraceParent()` para guardar el
`traceparent` en el outbox y quitar su propio post-processor de traza (este módulo es el dueño).

## Fase 5 — `sdk-file-share` 2.0.0 ✅ (rama `feature/sdk-file-share-v2`, sobre `feature/sdk-event-messaging-v6`)

Desbloqueada por D10 (A4). Detalle en `sdk-file-share/CHANGELOG.md`, `README.md` y `MIGRATION-2.0.md`.

- `d44e634` → `535a598`: `FileConstraints`; `FileRef` y `UploadRequest` validados con `Map.copyOf`;
  `FileStatus.UNKNOWN`; tickets con `Instant` y `toString()` sin query string.
- `2c86d6d` → `38618e8`: mapeo dentro de `execute`, `PresignedUrlPolicy` (https + hosts permitidos con
  comodín `*.`), body de error a 512 caracteres, `FileShareException.status()`, `download` acotado por
  `max-download-size` y sobrecargas en streaming.
- `8bba2a7` → `a9561bf`: `codehunters.file-share.*` con alias `vitxo.file-share.*` hasta 3.0, connect
  timeout real, `RestClient` desde el builder de la app con `defaultCandidate = false`, client credentials
  y hook `FileShareRequestInterceptor`; fuera Lombok.
- `3631070`: ADR 0009, README, CHANGELOG, MIGRATION-2.0.
- Revisión → `3b1e1f4`, `d9f7a47`, `9f5a2ad`: base URL que no es URI nombra la propiedad; límites
  documentados; javadoc.

**Cambios respecto al plan:**
- El SDK construye su propio `AuthorizedClientServiceOAuth2AuthorizedClientManager` (no usa el
  `OAuth2AuthorizedClientManager` de la app, que en servlet depende del request) a partir del
  `ClientRegistrationRepository` y, si existe, del `OAuth2AuthorizedClientService` de la app.
- `allowed-hosts` vacío acepta cualquier host https (no rompe a quien sube de versión); se recomienda
  configurarlo en producción.
- `download(String, UUID, OutputStream)` además de `download(FileRef, OutputStream)`.
- El paso de client credentials corre **después** de los `FileShareRequestInterceptor` de la app y
  ya no se puede reemplazar declarando uno: así ningún interceptor de la app ve el token ni lo quita
  en silencio. Para usar otra auth se deja `client-registration-id` sin valor.
- Las descargas de contenido usan su propio `RestClient` (`fileShareContentRestClient`, sobre
  `HttpURLConnection`) con `download-idle-timeout` por lectura en vez de plazo total, y sin seguir
  redirecciones.

**Revisión independiente** (3 HIGH, 4 MEDIUM, varios LOW), corregida con TDD:
- `9323951` → `6671d97`: puerto 443 obligatorio con `allowed-hosts`; `toString()` de tickets sin user
  info ni fragmento; caracteres de control fuera del body de error.
- `144f121` → `13d43a2`: `/content` solo acepta 2xx; fallo al escribir en el `OutputStream` del
  llamador → `UncheckedIOException` (no `Unavailable`); límites de `max-download-size` en el adapter.
- `65798d9` → `94dc7d0`: `download-idle-timeout` y cliente de contenido sin redirecciones;
  `max-download-size` validado al arrancar; esquema del base URL sin distinguir mayúsculas; aviso al
  arrancar si `allowed-hosts` está vacío.
- `1139923` → `3de03b7`: interceptores compuestos (auth al final); 401/403 desalojan el token;
  timeouts en el endpoint de token y caídas de ese endpoint → `FileShareUnavailableException`; https
  obligatorio con auth salvo `auth.allow-insecure-http=true`; `client-registration-id` vacío = sin auth.
- `a370e1c`, `c3e9c34`: README, ADR 0009, MIGRATION, CHANGELOG y javadoc.
- Quedan como límites documentados: validación de nombre/tipo solo ASCII (controles Unicode,
  bidi y barras parecidas pasan) y alias `vitxo.*` que no cubren claves de `@PropertySource`.

Verificación: 163 tests, cobertura 96,2 % líneas / 93,1 % ramas, `./gradlew clean build` y
`./gradlew -p build-logic test` en verde.

1. `connectTimeout` real (`JdkClientHttpRequestFactory` + `HttpClient.connectTimeout`); `RestClient`
   construido desde `ObjectProvider<RestClient.Builder>`; `defaultCandidate = false`.
2. Mapeo de respuesta dentro de `execute`: un `presignedUrl` faltante, una URI inválida o un UUID
   inválido lanzan `FileShareException`. El body de error se trunca a 512 caracteres en UTF-8, y el
   status queda como campo de la excepción.
3. `UploadRequest` y `FileRef`: validación en el constructor compacto (`referenceId`
   `^[A-Za-z0-9._-]+$`, `filename` sin separadores ni controles, `size ≥ 0`, `contentType` parseable),
   y `Map.copyOf`.
4. Tickets: `expiresAt` como `Instant`; `toString()` oculta la query string; se exige `https` y un
   host de `allowedPresignedHosts`.
5. `download(FileRef, OutputStream)` en streaming, y `maxDownloadSize` para el `byte[]`.
6. `FileStatus.UNKNOWN`; se elimina el código muerto y Lombok.
7. Auth saliente según D10: client credentials + hook `FileShareRequestInterceptor` (se suma, no
   reemplaza).
8. Prefijo: `vitxo.file-share.*` → `codehunters.file-share.*`.

## Fase 6 — `sdk-audit-log` 3.0.0 ✅ (rama `feature/sdk-audit-log-v3`, sobre `feature/sdk-file-share-v2`)

Detalle en `sdk-audit-log/CHANGELOG.md`, `README.md` y `MIGRATION-3.0.md`.

- `5cb5813` → `2bedaf5`: el aspecto guarda la clase de la excepción; `correlationId` que no es UUID
  queda en `null`; `Clock` inyectado (el bean único de la app o UTC) y duración con `nanoTime`; una
  `AuditPersistenceException` se propaga (suprimida en la excepción original si el método falló).
- `55e3f42` → `1d22699`: `MethodBasedEvaluationContext`, caché de expresiones acotada a 1024, errores
  de sintaxis SpEL → campo `null` en vez de perder el evento.
- `38203dd` → `38fe497`: `codehunters.audit.*` con alias `vitxo.audit.*` hasta 4.0 y metadatos de
  deprecación; `codehunters.audit.async.*` validado al arrancar.
- `91137c4` → `8617444`: `AuditEventJdbcAdapter` síncrono; `AsyncAuditEventPublisher` +
  `AuditEventRejectionListener` (contador `audit.events.rejected`); executor acotado, con MDC,
  drenado al cerrar y destruido antes del `DataSource`; `auditTaskExecutor`/`auditJsonMapper` con
  `defaultCandidate = false`; fuera `@EnableAsync`.
- `fecbfe8` → `1f93b9f` → `cb0fd96`: DDL de referencia Postgres y SQL Server en
  `db/migration/audit/<db>/audit_event_create.sql`, probados con Testcontainers; `event_created_at`
  se enlaza como `OffsetDateTime`.
- `9cb6fe5`: README, CHANGELOG, MIGRATION-3.0.

**Cambios respecto al plan:**
- El script de SQL Server se publicaba como `db/migration/V1__create_audit_event_table.sql`, así que
  Flyway lo ejecutaba en cualquier servicio con la ubicación por defecto. Se mueve fuera del patrón
  versionado; `MIGRATION-3.0.md` explica qué hacer si ya está en el historial.
- El IT de SQL Server destapó un bug no listado: `Timestamp` sobre `DATETIMEOFFSET` desplazaba la
  hora según la zona de la JVM. Corregido.
- `fail-on-error=true` nunca fallaba la llamada (hilo aparte y el aspecto tragaba la excepción).
  Ahora sí: síncrono y dentro de la transacción del llamador si la hay.
- El bean publicador se llama `auditEventPublisher`.

**Límites documentados:** eventos en cola se pierden si el proceso muere o vence
`await-termination`; un evento diferido hasta el fin de la transacción se pierde si el proceso muere
antes; los tipos de retorno asíncronos se registran al arrancar el trabajo, no al terminarlo
(resuelto después, ver abajo).

**Revisión independiente** (3 HIGH/MEDIUM de transacción, 3 MEDIUM, 6 LOW). Arreglos, cada uno con
su commit de test en rojo:
- `3debccd` → `14b4e49`: una expresión o un `AuditContextProvider` que lanzan dejan nulo solo su
  campo; `#result` reservado; en el camino de error todo lo que lanza el publicador (incluido un
  `Error`) queda como suprimido; un evento que no se puede construir falla la llamada con
  `fail-on-error`. `AuditAspect.Options(clock, failOnError, order)`, el aspecto es `Ordered`.
- `55dbde3` → `4d0abea`: `JsonSnapshotAuditEventPublisher` copia los mapas a JSON en el hilo del
  llamador; un mapa no serializable queda `null` (nunca `toString()`); un insert fallido se loguea
  con tipo, acción, SQLState y clase, sin el mensaje de la base.
- `20897ba` → `9d90e42`: `TransactionAwareAuditEventPublisher`. El evento de error se escribe al
  momento en transacción propia (`REQUIRES_NEW` con el único `PlatformTransactionManager`, o
  conexión auto-commit `AutoCommitDataSource` si no hay uno) y sobrevive al rollback, también tras
  un error SQL que aborta la transacción en Postgres. Con `fail-on-error` el éxito se une a la
  transacción del llamador; sin él se difiere a `afterCompletion` y se marca `ROLLED_BACK` o
  `UNKNOWN`. `FailSafeAuditEventPublisher` + `AuditEventFailureListener`. IT en Postgres con
  `@Transactional` real que prueba que, con el orden por defecto, el aspecto queda dentro de la
  transacción.
- `ec52376` → `f0db51b`: `@ConditionalOnSingleCandidate(DataSource)`; contadores
  `audit.events.rejected` y `audit.events.failed` registrados al arrancar;
  `codehunters.audit.order`; `fail-on-error` llega al aspecto. Test de executor saturado a través
  de la auto-config.
- `82eb0ae`: test con proxy JDK de interfaz.
- `d1e7d6f`: README (sección Transacciones), CHANGELOG, MIGRATION-3.0; fila de `sdk-file-share` en
  el README raíz.

Desvíos: un commit fallido se registra `UNKNOWN`, no `ROLLED_BACK`, porque Spring informa
`STATUS_UNKNOWN` cuando falla el commit. La tabla no tiene columna de estado: `ROLLED_BACK` y
`UNKNOWN` se guardan en `error_message` con `success=false`/`null`.

Verificación: 119 tests (incluye ITs Postgres 17 y azure-sql-edge), cobertura 98,4 % líneas /
95,8 % ramas, `./gradlew clean build` y `./gradlew -p build-logic test` en verde.

**Pendientes cerrados tras la revisión**, cada uno con su commit de test en rojo:
- `795b68b` → `0b8b928`: `AuditPersistenceException.redacted(...)`. En modo `fail-on-error` la
  excepción y su cadena de causas ya no llevan el mensaje de la base (Postgres incluye
  `Failing row contains (...)`); quedan clase, SQLState, código de proveedor y stack trace
  (`failureClass()`, `sqlState()`, `vendorCode()`). IT con violación de NOT NULL en Postgres;
  mutación detectada.
- `7c5c527` → `12cbb81`: los métodos que devuelven `CompletionStage`, `Mono` o `Flux` se auditan al
  terminar el trabajo, con su resultado y duración. El stage se devuelve sin cambios; `Mono`/`Flux`
  (Reactor opcional, `compileOnly`) se decoran y auditan cada suscripción; una cancelación no se
  audita. El evento se publica en el hilo que completa el trabajo y un fallo se loguea aunque haya
  `fail-on-error`.
- `24a5b34`: test de `AutoCommitDataSource.getConnection(username, password)`.
- `3f28dea` → `476b1e8`: WARN al arrancar si hay varios transaction managers sin primario y uno es
  `JtaTransactionManager` (los eventos de error irían por una conexión auto-commit que JTA enlista).
- `36872fc`: README, CHANGELOG y MIGRATION-3.0.

Verificación: 143 tests, cobertura 98,6 % líneas / 93,8 % ramas, `./gradlew clean build` y
`./gradlew -p build-logic test` en verde.

**Segunda revisión (pendientes asíncronos)**, `6a11724` (tests en rojo) → `0556a46` (fix) → `docs`:

- Los eventos de completion se publican con `AuditEventPublisherPort#publishDetached` (método
  `default` nuevo): el publisher transaccional los manda siempre "fuera", así que un future o un
  `Mono` que termina dentro de otra transacción ya no se une a ella ni toma su resultado.
- `CapturedCall`: los campos del `AuditContextProvider`, el MDC y las expresiones que no nombran
  `#result` se toman en el hilo del caller cuando el método retorna; solo `#result` espera al
  resultado.
- Cualquier fallo del publisher o al construir el evento: con `fail-on-error` falla la llamada con
  una excepción redactada; sin él, se loguea solo la clase. `AsyncAuditEventPublisher` también.
- Cancelación (future o suscripción Reactor) → estado `CANCELLED`; cada suscripción reporta una sola
  vez. Reintentos = un evento por intento (documentado).
- Un completion en un hilo no bloqueante se escribe en `Schedulers.boundedElastic()`.
- Solo se decora si el tipo declarado admite `Mono`/`Flux` (un `ConnectableFlux` se audita al
  retornar). `Throwable` contenido en el callback, salvo `VirtualMachineError`.
- WARN si el único transaction manager es JTA sin `TransactionManager`. `vendorCode` 0 → vacío.
- Test con un classloader que oculta Reactor.

Verificación: 169 tests, cobertura 98,4 % líneas / 91,5 % ramas.

**Proxying**, `d4d9f79` (test en rojo) → `8ebc5fe` (fix): la auto-configuración ya no aplica
`@EnableAspectJAutoProxy` (anulaba `spring.aop.auto=false` y rompía el arranque sin
`aspectjweaver`). El aspecto lo aplica la auto-configuración AOP de Boot, igual que en `measure`, y
el bean del aspecto exige `org.aspectj.weaver.Advice`. 172 tests.

**Review del proxying**, `ce571bb` → `3b63158`: sin un auto-proxy creator de AspectJ
(`spring.aop.auto=false`, `AopAutoConfiguration` excluida o contexto Spring sin Boot) el audit no
hacía nada y no avisaba. Un `SmartInitializingSingleton` lo comprueba al arrancar: WARN, o fallo
del arranque con `fail-on-error=true`. 175 tests, 98,4 % líneas / 91,3 % ramas. `measure-v3` y
`parameter-v2` se rebasaron encima, así que sus hashes anteriores cambiaron.

1. `auditTaskExecutor` de tipo `ThreadPoolTaskExecutor` con `defaultCandidate = false` y
   `TaskDecorator` MDC. `auditJsonMapper` con `defaultCandidate = false` y `@Qualifier` aplicado de
   verdad.
2. `AuditEventJdbcAdapter` síncrono, sin `@Async` ni `@Transactional`; nuevo decorator
   `AsyncAuditEventPublisher`. Con `failOnError=true`, se publica de forma síncrona.
3. `AuditAspect`: guarda la clase de la excepción, no el mensaje. Un `correlationId` inválido queda
   en `null`. Se inyecta `Clock` y la duración se mide con `nanoTime`.
4. `AuditSpelEvaluator`: `MethodBasedEvaluationContext` y caché de expresiones.
5. SQL de referencia para Postgres, además del de SQL Server.
6. Prefijo: `vitxo.audit.*` → `codehunters.audit.*`.

## Fase 7 — `sdk-measure-micrometer` 3.0.0 ✅ (rama `feature/sdk-measure-v3`, sobre `feature/sdk-audit-log-v3`)

Detalle en `sdk-measure-micrometer/CHANGELOG.md`, `README.md` y `MIGRATION-3.0.md`.

- `e9838db` → `9edfdd4`: un timer por nombre con tags `class`, `method`, `exception` y `outcome`
  (fuera `<name>.errors`); `<name>.active` para long-task y `<name>.counted` para el contador;
  nombre por defecto `method.measured`. Claves de tag estables (`none` para nulos/blancos,
  `unknown` si la expresión falla). `SimpleEvaluationContext` (lectura de propiedades y métodos de
  instancia, sin `T()` ni beans); cada anotación se parsea una vez. `@Measured` de clase
  (`@within`, la anotación de método gana). `CompletionStage` medido hasta completar y devuelto sin
  cambios. Tope de valores por tag de expresión: los nuevos pasan a `other`.
- `f25832e` → `229c02f`: `@AutoConfiguration(afterName = MetricsAutoConfiguration,
  CompositeMeterRegistryAutoConfiguration)`, sin `@EnableAspectJAutoProxy`;
  `codehunters.measure.enabled` y `codehunters.measure.max-tag-values`; `MeasuredBeanValidator`
  falla el arranque con tags impares, expresiones sin `=` o claves vacías, repetidas o reservadas.
  Se borra `gradle.properties`: el módulo vuelve al 0.80 de ramas.
- `bf63e49`: tests de parseo y del tope.
- `178ddd6` → `346fe1c`: un meter rechazado por el registro o una anotación inválida se loguean una
  vez, no en cada llamada.
- `a93aa02`: README (tabla de meters, límites), CHANGELOG, MIGRATION-3.0 con la tabla de nombres
  viejos y nuevos; fila en el README raíz.

**Cambios respecto al plan:**
- El tope de cardinalidad no es un `MeterFilter` sino un límite en el aspecto que sustituye los
  valores nuevos por `other`: un filtro `deny` de Micrometer descartaría la medición entera, y el
  aspecto es el único que sabe qué tags vienen de expresiones.
- El contador pasa a `<name>.counted` y el long-task timer a `<name>.active`: así ningún nombre
  lleva dos tipos de meter, y `.counted` no se lee como el `_count` del timer. (Un contador
  `x.count` no choca con `x_seconds_count` en Prometheus, pero el sufijo confunde.)
- Las expresiones que no parsean no fallan el arranque (se loguean y quedan `unknown`), como pide
  el punto 2; solo los errores de estructura de la anotación lo hacen.
- No había propiedades `vitxo.*` en este módulo, así que no hay alias.

Verificación: 50 tests, cobertura 100 % líneas / 93,2 % ramas, mutaciones detectadas (contexto
SpEL, `afterName`), `./gradlew clean build` y `./gradlew -p build-logic test` en verde.

**Revisión independiente**, `150ea9c` (tests en rojo) → `e27f790` (fix) → `docs`:

- El validador junta `nombre → claves de tag` entre todos los beans y falla el arranque si dos
  anotaciones comparten nombre con claves distintas (Prometheus descarta en silencio la segunda
  serie; con el nombre por defecto `method.measured` era el caso normal). Revisa las anotaciones de
  clase en las superclases; avisa de `@Measured` que nunca mide (método privado, final o estático, o
  solo en una interfaz); salta clases que no se pueden introspectar (`isCandidateClass` + `catch
  Throwable`).
- Claves de tag con `[A-Za-z_][A-Za-z0-9_.-]*`; `countValue` positivo y finito.
- Plan por `Method` en caché (definición, tags base, nombres de parámetros); una anotación inválida
  queda en caché como inválida y no se construye contexto SpEL si no hace falta.
- Tope de cardinalidad por nombre + método, con alta atómica. Avisos con clave y texto por nombre +
  método.
- Expresiones solo de lectura de propiedades: sin llamadas a métodos (incompatible, documentado).
- Tests nuevos: identidad de la excepción, variantes de fallo asíncrono, contador sin incrementar en
  fallos, SpEL con asignación y `@bean`, validador con superclases, limitador concurrente.

Verificación: 77 tests, cobertura 98,0 % líneas / 91,7 % ramas.

1. `@AutoConfiguration(afterName = ...MetricsAutoConfiguration / CompositeMeterRegistryAutoConfiguration)`.
   Se elimina `@EnableAspectJAutoProxy`.
2. Los errores de SpEL (en tags y en `countCondition`) se registran en el log y usan `"unknown"`;
   nunca afectan la llamada. Las claves de tags son estables (`"none"` para nulos). Un número impar
   de `tags` falla al arrancar.
3. `@Measured` a nivel de clase (`@within`). Soporte de `CompletionStage`.
4. `SimpleEvaluationContext` y caché de expresiones.
5. Nombres según la convención de Micrometer: un timer con tags `class`, `method`, `exception` y
   `outcome`. Es un cambio incompatible y va documentado en el README con la tabla de nombres viejos
   y nuevos.
6. `MeterFilter` con `codehunters.measure.max-tag-values` para contener la cardinalidad.
7. Borrar `sdk-measure-micrometer/gradle.properties` (`coverage.branch.minimum=0.60`): el módulo vuelve
   al 0.80 por defecto.

## Fase 8 — `sdk-parameter` 2.0.0 ✅ (rama `feature/sdk-parameter-v2`, sobre `feature/sdk-measure-v3`)

Commits (cada test en rojo seguido de su cambio):

- `2b135a4` → `e69a964`: `ParameterModule` pasa a `record` con constantes y `of(code)`; un módulo
  desconocido ya no se mapea a `null`. Accesores null-safe, `asBoolean` estricto, overflow como
  `ParameterTypeMismatchException`; el mensaje nombra el parámetro, sin valor y sin causa.
- `3641f27` → `ad0759e`: `asMap`/`asMapByName` conservan el orden y los `null`, y un nombre
  repetido entre submódulos lanza `ParameterException`. Submódulo en blanco = vacío. Tabla
  configurable, validada como identificador (opcionalmente `schema.tabla`). Fuera Lombok.
- `ccf8e3f` → `ab5d8f1`: `ParameterProperties` (`enabled`, `table-name`, `cache-ttl`),
  `CachingParameterClient` (TTL, acotado a 1024 respuestas, no cachea fallos),
  `parameterJdbcClient` con `defaultCandidate = false`, `@ConditionalOnSingleCandidate(DataSource)`.
  Fuera el `compileOnly` de `spring-boot-starter-jdbc`. Versión 2.0.0.
- `cfaeeba` → `479bdfd`: listas inmodificables (una respuesta cacheada no se puede alterar) y test
  ArchUnit de capas.
- `14b89f0`: README, CHANGELOG, MIGRATION-2.0 y la fila del README raíz.

Verificación: 88 tests, cobertura 98,4 % líneas / 86,1 % ramas. (Hashes anteriores al rebase
sobre el fix de proxying de `audit-log`; los actuales son `679ac41` … `88a0e23`.)

**Fixes de la review** (cada test en rojo seguido de su cambio):

- `b0161ce` → `860365a`: `ParameterModule.of` devuelve la constante para un código conocido y
  recorta espacios, así que `==` sigue funcionando con las filas que mapea el SDK. Tabla de hasta
  tres partes (`db.schema.tabla`), probada contra un esquema real en H2. Un elemento `null` en las
  colecciones lanza `IllegalArgumentException`. Un UUID mal formado lanza
  `ParameterAccessException` con la columna y sin el valor.
- `050a183` → `bccebbc`: la caché expulsa la respuesta menos usada en vez de dejar de guardar;
  los fallos concurrentes de una misma clave comparten una sola consulta (single-flight) y el error
  llega a todos sin cachearse; claves normalizadas (submódulo en blanco, colecciones ordenadas y
  sin repetidos); listas copiadas e inmodificables; `asMap`/`asMapByName` salen del
  `findByModule` cacheado.
- `0b60853`: README, CHANGELOG y MIGRATION-2.0: identidad de `ParameterModule`, forma JSON
  `{"code":...}`, `EnumMap`/`EnumSet`, listas inmodificables, reglas del nombre de tabla y la
  caché.

Verificación: 119 tests, cobertura 97,4 % líneas / 86,9 % ramas. Un `cache-ttl` vacío ya
arrancaba sin caché; el test nuevo lo fija.

**Desvíos del plan:**
- No hay alias de propiedades: la 1.x no tenía ninguna propiedad `vitxo.*`.
- El tope de 1024 respuestas de la caché es una constante, no una propiedad.
- Se añadieron, fuera del plan: `@ConditionalOnSingleCandidate(DataSource)`, tabla validada contra
  inyección SQL y resultados inmodificables.

Tareas planificadas:

1. `JdbcClient` interno con `defaultCandidate = false`.
2. `asMap` acepta valores nulos y conserva el orden; un nombre duplicado lanza
   `ParameterException`. Un `submodule` en blanco se normaliza.
3. `Parameter`: accesores null-safe; `asBoolean` estricto; overflow envuelto; el mensaje de error no
   incluye el valor.
4. `ParameterModule` pasa de enum a `record ParameterModule(String code)`, con constantes para los
   valores actuales.
5. Nuevo `ParameterProperties` (`codehunters.parameter.*`: `enabled`, `table-name`, `cache-ttl`) y
   decorator de caché opcional.
6. Se elimina Lombok y el `compileOnly` de `spring-boot-starter-jdbc`.

## Fase 9 — `sdk-i18n` 1.0.0 ✅ (rama `feature/sdk-i18n-v1`, sobre `feature/sdk-parameter-v2`)

Commits (cada test en rojo seguido de su cambio):

- `a85ead3` → `3c6aa46`: `XLocaleAwareLocaleResolver` acepta `en_US`, ignora valores de más de 35
  caracteres y cruza `X-Locale` y `Accept-Language` con `supportedLocales` igual que
  `AcceptHeaderLocaleResolver` (exacto, o mismo idioma sin país). `LocaleMdcFilter` corre también
  en dispatch async y de error, y registra los fallos del resolver solo con la clase de la
  excepción.
- `4c454ee` → `d90659c`: `I18nProperties` pasa a record validado bajo `codehunters.i18n`
  (`enabled`, varias `basename`, `supported-locales`, `encoding` como `Charset`); los `vitxo.i18n.*`
  siguen hasta 2.0 con metadata de deprecación. Tres configuraciones anidadas, antes de las de
  Boot: mensajes (cede solo ante un bean llamado `messageSource`, avisa si hay
  `spring.messages.basename`), servlet (solo apps servlet) y validación (solo con proveedor; cede
  ante cualquier `jakarta.validation.Validator`; resuelve mensajes a través del contexto).
  Versión 1.0.0.
- `1164ae4`: README, CHANGELOG, MIGRATION-1.0 y la fila del README raíz.

Verificación: 57 tests, cobertura 100 % líneas / 100 % ramas; `./gradlew clean build` y
`./gradlew -p build-logic test` en verde.

**Desvíos del plan:**
- `before` va por `beforeName`, para no exigir en compilación los módulos de validación y Web MVC.
- Se añadieron, fuera del plan: `codehunters.i18n.enabled`, varias base names, validación de
  propiedades al arrancar, el aviso por `spring.messages.*`, el tope de longitud de `X-Locale` y el
  filtro en dispatch async/error.
- El validador usa el `ApplicationContext` como fuente de mensajes, no el bean del SDK, para
  funcionar también con un `messageSource` propio de la aplicación. La review lo quitó (ver abajo).

**Correcciones de la review** (cada test en rojo seguido de su cambio):

- `1a7c7cb` → `30b7112` (`platform/sdk-support`, afecta a todos los módulos con alias): una
  variable de entorno que parte con `_` un nombre con guion (`VITXO_I18N_DEFAULT_LOCALE`) se
  copiaba a un nombre que nunca enlaza. Cada `*LegacyProperties` declara ahora sus claves viejas, y
  el helper las busca como lo hace Boot (las dos formas de env), recorre por índice las listas y
  sigue recorriendo las fuentes para los campos anidados (`paths[0].methods[0]`). Las claves viejas
  no declaradas ya no se copian, y un segundo aviso lista las ignoradas porque la nueva está
  puesta. El arreglo entra con esta rama; los CHANGELOG de user-security, authz, event-messaging,
  file-share y audit-log lo mencionan en su versión.
- `97a6177` → `89dc1ff`: el SDK ya no registra validador. El `defaultValidator` de Boot resuelve
  `{key}` con el `messageSource` del SDK, aplica los `ValidationConfigurationCustomizer`, cae a un
  interpolador sin EL y mantiene `ValidationMessages.properties`.
- `1aea76a` → `41efd84`: sin `supported-locales`, un `Accept-Language` `*`, con todos los rangos a
  peso cero o ilegible resuelve al `default-locale` (antes, al locale raíz o al de la JVM).
  `X-Locale` pierde las extensiones Unicode; `setLocale` lanza con un mensaje que lo explica.
- `c2d9d8b` → `6491349`: aviso al arrancar por `spring.web.locale` y `spring.web.locale-resolver`;
  el filtro se registra explícitamente para REQUEST, ASYNC y ERROR.
- `3ecbddc`: README, CHANGELOG y MIGRATION-1.0.

Verificación: i18n 66 tests (100 % líneas / 100 % ramas), sdk-support 13 tests; `./gradlew clean
build` y `./gradlew -p build-logic test` en verde.

Tareas planificadas:

1. Tres configuraciones anidadas: MessageSource, Servlet web (`@ConditionalOnWebApplication(SERVLET)`)
   y Validation (`@ConditionalOnClass(jakarta.validation.Validator)` + `@ConditionalOnResource` del
   proveedor).
2. `before = {MessageSourceAutoConfiguration, ValidationAutoConfiguration, WebMvcAutoConfiguration}`,
   con la precedencia sobre `spring.messages.*` documentada.
3. `supportedLocales`; `_` se normaliza a `-`. `LocaleMdcFilter` registra los errores en el log.
4. `I18nProperties` pasa a record. Prefijo: `vitxo.i18n.*` → `codehunters.i18n.*`.

## Fase 10 — `sdk-totp` 2.0.0 (antes `sdk-utils`) y retiro de `sdk-geo-validation`

Rama: `feature/sdk-totp-v2`.

**TOTP:**
1. Renombrar el directorio y el artefacto `sdk-utils` → `sdk-totp`, con la capability
   `io.codehunters:sdk-utils`. El paquete `io.codehunters.totp` no cambia.
2. `Locale.ROOT` en el padding y en Base32; comparación de tiempo constante; `floorDiv`; variance
   ≤ 10; el paso debe ser de segundos enteros y ≥ 1 s; `SecureRandom` estático; `fromHex`.
3. Tests con los vectores del RFC 6238, apéndice B (SHA1, SHA256, SHA512).
4. Prefijo: `vitxo.totp.*` → `codehunters.totp.*`.

**geo-validation** (D3), rama `chore/remove-geo-validation`:
1. Entregar el código al equipo del servicio de rifas, que es quien lo adopta. **Es trabajo de otro
   repo y necesita un dueño.**
2. Una vez adoptado, eliminar `sdk-geo-validation` de `settings.gradle.kts` y su directorio. La 0.2.0
   sigue disponible en GitHub Packages.
3. Antes de entregarlo, se corrigen en el SDK los NaN de `Latitude`/`Longitude` y `SET NOCOUNT ON`,
   para no entregar los bugs. Ese arreglo no se publica.

**Estado: hecha, salvo los puntos 1 y 2 de geo-validation** (bloqueados: falta el dueño en el
servicio de rifas). Rama `feature/sdk-totp-v2`, creada desde `feature/sdk-parameter-v2` en un
worktree aparte mientras la Fase 9 avanzaba en paralelo; se rebasea sobre `feature/sdk-i18n-v1`
antes de integrar. Cada cambio lleva antes su commit de test en rojo.

- `a7e7316`: `sdk-utils` → `sdk-totp` (directorio, `settings.gradle.kts`, `sdk-bom`) con
  `replaces("sdk-utils")`.
- `07bd888` → `e4a443a`: vectores RFC 6238 (apéndice B, 18 casos) con `TotpSecret.fromHex`;
  `Locale.ROOT` en el padding y en Base32 (probado con `ar-SA-u-nu-arab` y `tr-TR`); `floorDiv`;
  comparación en tiempo constante sin cortar en la primera coincidencia; variance `0..10`
  (`MAX_VARIANCE`); código `null` → `false`; paso de segundos enteros ≥ 1 s; `SecureRandom`
  estático; los errores de Base32 y hex no repiten la entrada. Se quitó el test con bucle.
- `62526be` → `9a8b511`: `codehunters.totp.*` con alias `vitxo.totp.*` hasta 3.0
  (`TotpLegacyProperties` sobre `sdk-support`) y metadata de deprecación; un paso inválido hace
  fallar el arranque; versión 2.0.0.
- `7478f57` → `ba1d454` (geo): `NaN` e infinitos rechazados, el mensaje ya no lleva la coordenada,
  y `SET NOCOUNT ON` al inicio. La versión sigue en 0.2.0 y no se vuelve a publicar.
- `5f87169`: README, CHANGELOG y `MIGRATION-2.0.md` de `sdk-totp`, `sdk-geo-validation/HANDOFF.md`
  y las dos filas del README raíz.

Verificación: `sdk-totp` 105 tests (98,4 % líneas, 100 % ramas); `sdk-geo-validation` 21 tests
(100 % líneas, 93,8 % ramas); `./gradlew clean build` y `./gradlew -p build-logic test` en verde.

**Desvíos:**
- `sdk-utils` no tenía Lombok, así que no hubo nada que quitar.
- Una variance negativa ahora falla (antes se trataba como 0); el plan solo pedía el tope de 10.
- `TotpGenerator` sigue siendo candidato normal de inyección: es la API pública del módulo, no un
  bean interno.
- `HANDOFF.md` suma, para el dueño nuevo, los pendientes vistos en geo y no corregidos aquí:
  `@ConditionalOnSingleCandidate`, `MatchedZoneType.valueOf` con un tipo desconocido, prefijo
  propio y un test de integración contra SQL Server.

**Abierto:** `verify` no impide reusar un código dentro de su ventana (documentado en el README:
quien necesite un solo uso guarda el último paso aceptado). Sin review independiente todavía.

## Fase 11 — Cierre

1. `sdk-bom` 1.0.0 con las versiones finales de las Fases 2 a 10.
2. Comprobar que ningún módulo sobrescribe `coverage.branch.minimum`.
3. `README.md` raíz: tabla de módulos con su estado real, el BOM y una guía de migración por
   consumidor (artefactos, imports, prefijos, DDL).
4. `CHANGELOG.md` raíz como índice de los CHANGELOG por módulo; tags `sdk-<módulo>/vX.Y.Z` y
   `bom/vX.Y.Z` creados por `publish.yml` después de publicar.
5. `CONTRIBUTING.md` con las convenciones reales (ramas, commits, TDD, definición de terminado, ADR).

---

## Orden y dependencias

```
Fase 1 ──┬─> Fase 2 (security, bloqueada por A1)
         ├─> Fase 4 (tracing) ──> Fase 3 (event-messaging)
         ├─> Fase 5 (file-share)
         ├─> Fase 6 (audit)
         ├─> Fase 7 (measure)
         ├─> Fase 8 (parameter)
         ├─> Fase 9 (i18n)
         └─> Fase 10 (totp + geo)
                                   todas ──> Fase 11 (BOM)
```

Las ramas hechas forman una pila lineal, cada una sobre la anterior:
`feature/build-foundations` → `feature/build-logic` → `feature/sdk-user-security-v3` →
`feature/sdk-tracing-v1` → `feature/sdk-event-messaging-v6` → `feature/sdk-file-share-v2` →
`feature/sdk-audit-log-v3` → `feature/sdk-measure-v3` → `feature/sdk-parameter-v2` →
`feature/sdk-i18n-v1`. Se integran en ese orden.

Las Fases 2 a 10 son independientes entre sí, excepto 4 → 3. El orden sugerido es por riesgo:
2, 4, 3, 6, 5, 8, 7, 9, 10.

## Versiones resultantes

| Artefacto | Antes | Después |
|---|---|---|
| `sdk-user-security` | 2.6.0 | 3.0.0 |
| `sdk-user-security-authz` | — | 3.0.0 |
| `sdk-event-messaging` | 5.1.5 | (reemplazado) |
| `sdk-event-messaging-core` / `-jdbc` / `-rabbit` / `-kafka` | — | 6.0.0 |
| `sdk-tracing` | 0.2.3 | 1.0.0 |
| `sdk-file-share` | 1.0.3 | 2.0.0 |
| `sdk-audit-log` | 2.1.4 | 3.0.0 |
| `sdk-measure-micrometer` | 2.1.2 | 3.0.0 |
| `sdk-parameter` | 1.2.1 | 2.0.0 |
| `sdk-i18n` | 0.2.2 | 1.0.0 |
| `sdk-utils` → `sdk-totp` | 1.0.4 | 2.0.0 |
| `sdk-geo-validation` | 0.2.0 | (retirado) |
| `sdk-bom` | — | 1.0.0 |

## Prefijos de propiedades

| Antes | Después |
|---|---|
| `keycloak.security.*` | `codehunters.security.*` |
| `keycloak.policy-enforcer.*`, `keycloak.host`, `keycloak.realm` | `codehunters.security.authz.*` (URL y realm desde `codehunters.security`) |
| `outbox.*` | `codehunters.outbox.*` |
| `vitxo.audit.*` | `codehunters.audit.*` |
| `vitxo.file-share.*` | `codehunters.file-share.*` |
| `vitxo.i18n.*` | `codehunters.i18n.*` |
| `vitxo.totp.*` | `codehunters.totp.*` |
| (no había) | `codehunters.tracing.*`, `codehunters.measure.*`, `codehunters.parameter.*` |
| `geo.validation.*` | (se retira con el módulo) |

---

## Puntos críticos de revisión (fallos que ninguna tarea cubre por sí sola)

1. **Jars viejo y nuevo juntos en el classpath** (`sdk-event-messaging:5.x` con `-core:6.0`,
   `sdk-utils` con `sdk-totp`): clases duplicadas, y gana
   una al azar. El consumidor espera que el build falle. Se cubre con la capability (1.6) y con un
   test en `sdk-bom` que resuelve ambas coordenadas y espera el conflicto.
2. **Un consumidor que actualiza sin cambiar sus propiedades:** espera que todo siga funcionando, con
   un WARN. El test de alias en la Definición de terminado lo cubre en cada módulo.
3. **Una app con solo uno de los transportes, o un worker sin web:** espera que arranque. Lo cubren
   los tests de `FilteredClassLoader` de cada módulo, y en la Fase 3 un test de contexto por
   artefacto con solo ese artefacto.
4. **Una app que define su propio `JsonMapper`, `Executor`, `JdbcClient`, `RestClient` o
   `SecurityFilterChain`:** espera que el suyo gane y que el nuestro no rompa la inyección por tipo.
   Lo cubre el test "arranque junto a la autoconfiguración real de Boot" de cada módulo.
5. **Un pod alcanzable sin pasar por el gateway:** espera que un header forjado no dé privilegios.
   Lo cubre el test de la Fase 2 (`x-user-roles` forjado → 401).
