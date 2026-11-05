# Спецификация корреляции и надёжности Bridge

Документ дополняет техническую спецификацию AQA и является обязательным контрактом для реализации Bridge и AQA Client. Описанные решения целевые; наличие требований в документации не означает, что компонент уже реализован.

## 1. Цепочка идентификаторов и границы доверия

Для каждого прогона создаётся уникальный `AQA_RUN_ID`. Для каждого тест-кейса используется стабильный `testId`. Для каждой MCP-сессии AQA создаёт UUID `clientSessionId`. Bridge назначает `connectionId`, а после успешного запуска дочернего процесса фиксирует PID.

Обязательная цепочка: `AQA_RUN_ID → testId → clientSessionId → connectionId → PID`.

### Сетевые предпосылки

- AQA и MCP Server подключены к одной выделенной user-defined Docker network.
- К этой сети подключены только AQA и MCP Server; посторонние контейнеры не должны иметь к ней доступ.
- Между AQA и Bridge нет NAT, TCP proxy, service mesh или другого посредника, меняющего source IP/source port. Bridge должен видеть исходный IP:port AQA data socket.
- Data port и control port слушают только внутренний интерфейс контейнерной сети и не публикуются на host.
- Для первой версии процессы внутри AQA-контейнера считаются доверенными. Проверка IP и endpoint не защищает от другого скомпрометированного процесса внутри того же контейнера. Защита от такого противника не входит в модель угроз первой версии.
- Если сетевую топологию с сохранением endpoint гарантировать нельзя, описанный механизм применять нельзя: требуется отдельное изменение архитектуры корреляции до реализации, а не fallback по времени/PID.

## 2. Регистрация соединения без изменения MCP-потока

AQA использует Java TCP socket, который привязывается к локальному адресу и свободному порту до регистрации. Вызов `Socket.bind(localAddress:0)` выполняется до `connect()`; socket остаётся открытым, поэтому порт зарезервирован этим процессом до подключения к Bridge. После регистрации именно этот экземпляр socket подключается к data port. Другой локальный процесс не может занять уже зарезервированный source port.

Последовательность:

1. AQA создаёт `clientSessionId`, привязывает socket к IP интерфейса Docker network и ephemeral source port, не вызывая `connect()`.
2. По control channel отправляет JSONL-команду `REGISTER_SESSION` с `AQA_RUN_ID`, `testId`, `clientSessionId`, заявленным data local IP:port и `registrationTtlMs`.
3. Bridge получает реальный peer IP control connection и проверяет, что заявленный data IP совпадает с ним. Проверяет формат/длину полей, TTL, UUID и допустимость endpoint. Эффективный TTL равен `min(registrationTtlMs, REGISTRATION_TTL_MS)`; `expiresAt` рассчитывается по эффективному TTL и возвращается клиенту.
4. Bridge атомарно резервирует endpoint tuple (address family, normalized IP, source port) и `clientSessionId`. Точный повтор того же `REGISTER_SESSION` с тем же requestId и неизменным payload обрабатывается идемпотентно по правилам раздела 3; другой requestId для уже занятого clientSessionId или endpoint приводит к `SESSION_CONFLICT` или `ENDPOINT_CONFLICT` соответственно.
5. Bridge возвращает `registrationId`, случайный `sessionToken` и `expiresAt`. Token применяется только для последующих control-команд этой сессии; он не передаётся в MCP data stream.
6. AQA подключает тот же заранее привязанный socket к data port. Bridge принимает соединение и сопоставляет фактический remote IP:port с одной незанятой и неистёкшей регистрацией.
7. Сопоставление и перевод регистрации в BOUND выполняются атомарно. Bridge назначает `connectionId`, запускает дочерний процесс и фиксирует PID после успешного старта.
8. AQA запрашивает `GET_SESSION_STATUS` по `clientSessionId` и `sessionToken`, получает `connectionId` и состояние. PID берётся из точной диагностической записи Bridge.
9. TTL действует только до успешного перехода `REGISTERED` → `BOUND`. При binding TTL регистрации отменяется; активная сессия не истекает по registration TTL и завершается по закрытию data connection, `IDLE_SESSION_TIMEOUT_MS` или остановке Bridge. Endpoint освобождается после терминального состояния; использованная регистрация не может быть повторно активирована. После terminal event Bridge немедленно освобождает endpoint, но сохраняет session record, token и diagnostics в течение `SESSION_RECORD_RETENTION_MS`; в этот период status/diagnostics доступны, а clientSessionId нельзя повторно зарегистрировать. По истечении retention record удаляется; AQA всегда создаёт новый UUID для новой сессии.

Bridge не запускает дочерний процесс при одной только регистрации: запуск происходит только после успешного сопоставления data connection. Незарегистрированное, просроченное, повторное или неоднозначное соединение закрывается до запуска процесса и фиксируется как `SESSION_REJECTED`.

Data stream остаётся полностью прозрачным: никаких registration token, preamble или служебных полей в MCP TCP-поток не добавляется.

## 3. Контракт control channel

Control channel — отдельный TCP listener и отдельный протокол UTF-8 JSON Lines. Один TCP connection обслуживает ровно один newline-terminated request и один newline-terminated response; Bridge выполняет flush и закрывает control connection. Pipelining не используется. Максимальный размер request и response — 16 KiB без завершающего newline. Некорректный JSON, отсутствие newline до лимита, превышение размера или времени обработки приводят к безопасному закрытию control connection.

### Формат запроса

Общие поля:
- `requestId` — UUID запроса;
- `command` — команда из allowlist;
- дополнительные поля определяются командой.

Ограничения полей `REGISTER_SESSION`: `runId` — строка 1–128 символов; `testId` — строка 1–256 символов; `clientSessionId` — UUID; `dataLocalAddress` — IP-адрес без hostname; `dataSourcePort` — целое 1–65535; `registrationTtlMs` — целое 1 000–30 000 мс. Bridge не принимает адрес/порт, отличный от реального IP control peer и валидного source port. TTL ограничивается серверной конфигурацией, даже если клиент запросил больше.

Команды:

| Команда | Обязательные поля | Назначение |
|---|---|---|
| `REGISTER_SESSION` | `runId`, `testId`, `clientSessionId`, `dataLocalAddress`, `dataSourcePort`, `registrationTtlMs` | Зарегистрировать заранее привязанный data endpoint |
| `GET_SESSION_STATUS` | `clientSessionId`, `sessionToken` | Получить состояние и connectionId/PID, если уже назначены |
| `GET_SESSION_DIAGNOSTICS` | `clientSessionId`, `sessionToken`; необязательно `afterSequence`, `limit` | Получить события указанной сессии начиная после sequence; `limit` 1–8, default 8 |
| `CANCEL_SESSION` | `clientSessionId`, `sessionToken` | Отменить только регистрацию в состоянии REGISTERED до data binding |

Успешный ответ `REGISTER_SESSION` содержит `requestId`, `ok: true` и `result.registrationId`, `result.sessionToken`, `result.expiresAt`. `registrationId` — созданный Bridge UUID; `sessionToken` — случайное значение не менее 256 бит. Успешный `GET_SESSION_STATUS` возвращает `clientSessionId`, `state`, `connectionId` и `pid`, если они уже назначены. Для `GET_SESSION_DIAGNOSTICS` `afterSequence` — необязательное целое число не меньше 0 (default 0), `limit` — целое число 1–8 (default 8). Ответ содержит только события указанной сессии, массив `events`, `hasMore` и `nextAfterSequence`; возвращается не более `limit` событий после `afterSequence`. `nextAfterSequence` равен sequence последнего возвращённого события; если событий нет — входному `afterSequence`. При `hasMore=true` клиент передаёт `nextAfterSequence` в `afterSequence` следующего запроса. Каждый диагностический `reason` ограничен 256 символами; один response не превышает 16 KiB. `CANCEL_SESSION` успешен только для состояния REGISTERED; после BOUND команда возвращает `SESSION_ALREADY_BOUND` и не завершает активную сессию. Активная сессия закрывается только закрытием data connection, idle timeout или остановкой Bridge. Ошибочный ответ содержит `requestId`, `ok: false`, `error.code` и безопасное `error.message`.

Пример регистрации:

```json
{"requestId":"4b9b57f4-12a2-4ad8-97b7-c5c96f1e9d10","command":"REGISTER_SESSION","runId":"run-2026-11-05-001","testId":"PROC-000","clientSessionId":"3e2dfae2-4e25-4e95-b62c-f28f72e55b17","dataLocalAddress":"172.20.0.5","dataSourcePort":41237,"registrationTtlMs":10000}
```

Пример успешного ответа:

```json
{"requestId":"4b9b57f4-12a2-4ad8-97b7-c5c96f1e9d10","ok":true,"result":{"registrationId":"8b6b86ec-5a2c-4d8c-8d31-9c9e2fcb43a7","sessionToken":"<случайный секретный токен>","expiresAt":"2026-11-05T10:00:10Z"}}
```

Пример ошибки:

```json
{"requestId":"4b9b57f4-12a2-4ad8-97b7-c5c96f1e9d10","ok":false,"error":{"code":"ENDPOINT_CONFLICT","message":"Endpoint is already reserved"}}
```

Обязательные коды ошибок control channel: `INVALID_REQUEST`, `UNKNOWN_COMMAND`, `UNAUTHORIZED`, `INVALID_ENDPOINT`, `ENDPOINT_CONFLICT`, `SESSION_CONFLICT`, `REGISTRATION_EXPIRED`, `SESSION_ALREADY_BOUND`, `REGISTRATION_LIMIT_REACHED`, `RATE_LIMITED`, `MESSAGE_TOO_LARGE`, `INTERNAL_ERROR`.

Ошибки валидного control request возвращаются JSON-ответом с `ok:false`: `INVALID_REQUEST`, `UNKNOWN_COMMAND`, `UNAUTHORIZED`, `INVALID_ENDPOINT`, `ENDPOINT_CONFLICT`, `SESSION_CONFLICT`, `REGISTRATION_EXPIRED`, `SESSION_ALREADY_BOUND`, `REGISTRATION_LIMIT_REACHED`, `RATE_LIMITED`, `MESSAGE_TOO_LARGE`, `INTERNAL_ERROR`. `SESSION_LIMIT_REACHED` является diagnostic reason для terminal state `REJECTED` и возвращается через `GET_SESSION_STATUS`/diagnostics, а не как ошибка control request. При превышении `CONTROL_MAX_CONNECTIONS` Bridge закрывает новое соединение без обработки; при malformed JSON, отсутствии newline до лимита или control timeout соединение закрывается без ответа. Для oversized line возвращается короткий `MESSAGE_TOO_LARGE`, если ответ можно отправить в пределах лимита.

### Состояния регистрации и сессии

Допустимые состояния: `REGISTERED`, `BOUND`, `STARTING`, `RUNNING`, `STOPPING` и конечные `CLOSED`, `REJECTED`, `START_FAILED`, `STOP_FAILED`, `EXPIRED`, `CANCELLED`. Допустимые переходы:

| Исходное состояние | Условие | Новое состояние |
|---|---|---|
| `REGISTERED` | Endpoint совпал, активный слот доступен | `BOUND` |
| `REGISTERED` | Истёк registration TTL | `EXPIRED` |
| `REGISTERED` | Успешный `CANCEL_SESSION` | `CANCELLED` |
| `REGISTERED` | Endpoint совпал, но достигнут `MAX_ACTIVE_SESSIONS` | `REJECTED` |
| `BOUND` | Начат запуск child process | `STARTING` |
| `STARTING` | `ProcessBuilder.start()` завершился успешно | `RUNNING` |
| `STARTING` | IOException или startup deadline | `START_FAILED` |
| `RUNNING` | EOF/reset клиента, idle timeout, завершение процесса или shutdown Bridge | `STOPPING` |
| `STOPPING` | Процесс завершён штатно или force kill успешен | `CLOSED` |
| `STOPPING` | Не удалось завершить процесс после force kill | `STOP_FAILED` |

Незарегистрированный data connection или connection с несовпадающим endpoint получает событие `SESSION_REJECTED` с `connectionAttemptId`, но не меняет состояние другой регистрации; она остаётся `REGISTERED` до корректного binding, CANCEL или TTL expiry.

Переходы состояния и резервирование endpoint должны быть атомарными. `REGISTER_SESSION` считается точным повтором только при полном совпадении `requestId`, `clientSessionId`, control peer IP и всех полей исходного payload. Пока регистрация находится в `REGISTERED` и TTL не истёк, Bridge возвращает исходные `registrationId`/`sessionToken`/`expiresAt`, не создавая новую регистрацию и не продлевая TTL. Для BOUND/STARTING/RUNNING/STOPPING/CLOSED/START_FAILED/STOP_FAILED возвращается `SESSION_ALREADY_BOUND`; для CANCELLED/REJECTED — `SESSION_CONFLICT`; для EXPIRED — `REGISTRATION_EXPIRED`. Изменённый payload/peer IP с тем же requestId или другой requestId для занятого clientSessionId возвращает `SESSION_CONFLICT`; занятый другим clientSessionId endpoint возвращает `ENDPOINT_CONFLICT`. Поэтому при таймауте/потере ответа AQA может выполнить ровно один recovery retry на новом control TCP connection с теми же `requestId`, `clientSessionId`, endpoint и байт-в-байт тем же payload. Новый requestId для recovery запрещён. Если ответ повторной попытки также потерян, AQA не открывает data connection и ждёт истечения TTL; новая регистрация создаётся только с новым clientSessionId, новым requestId и новым socket. После TTL повтор старой команды не создаёт новую регистрацию и возвращает `REGISTRATION_EXPIRED`. `CANCEL_SESSION` идемпотентен: повтор с теми же `clientSessionId`/`sessionToken` для состояния `CANCELLED` возвращает успешный ответ без повторного действия. Запись об отмене и terminal diagnostics сохраняются в течение `SESSION_RECORD_RETENTION_MS`, чтобы исключить повторное использование регистрации и позволить получить status/diagnostics. GET_SESSION_STATUS и GET_SESSION_DIAGNOSTICS не меняют состояние. Повторная команда не должна повторно создавать процесс или переиспользовать регистрацию. Запрос статуса является read-only и не меняет состояние.

### Защитные ограничения

- Control listener доступен только внутри выделенной Docker network; порт не публикуется на host.
- В сети находятся только AQA и MCP Server. Docker network сама по себе не является ACL для отдельных процессов внутри контейнера.
- Разрешены только перечисленные команды; shell, произвольный запуск процессов, чтение environment и файловые операции отсутствуют.
- Проверяются peer IP, совпадение data IP, UUID, длина/тип полей, допустимый диапазон TTL, уникальность endpoint и одноразовость регистрации.
- `sessionToken` генерируется криптографически стойким генератором, не менее 256 бит, не логируется и не включается в Allure. Он защищает status/diagnostics/cancel команды, но не заменяет проверку endpoint. Сравнение token выполняется constant-time. Для неизвестного `clientSessionId` и неверного token возвращается одинаковый `UNAUTHORIZED`, чтобы не раскрывать существование сессии.
- Ограничиваются размер сообщения, число control connections, число незавершённых регистраций, частота запросов и время обработки. `GET_SESSION_DIAGNOSTICS` выдаёт события страницами не более 8 записей, чтобы ответ укладывался в лимит control message.
- Control channel не принимает и не возвращает Jira/Confluence credentials или другие секреты.
- При недоступности control channel MCP data protocol не меняется; lifecycle-тест завершается ошибкой корреляции.
- Ошибки не должны раскрывать environment, секреты, локальные пути или stack trace клиенту.

## 4. Правила сопоставления endpoint и обработка гонок

Ключ сопоставления — фактическая пара remote IP:source port, полученная Bridge из принятого data socket, и заранее зарегистрированная пара. Сравнение адресов выполняется в нормализованном бинарном представлении с учётом address family; текстовые варианты одного IPv6/IPv4 адреса не должны давать разные ключи.

- Регистрация создаётся до data connect и имеет конечный TTL. TTL применяется только к состоянию REGISTERED и атомарно прекращается при переходе в BOUND; активная сессия управляется idle/stop timeout.
- Endpoint и clientSessionId резервируются атомарно; две регистрации не могут владеть одним endpoint.
- Data connection может потребить регистрацию только один раз.
- Если data connect пришёл до регистрации, с неверного endpoint, после TTL или с уже использованным endpoint, Bridge закрывает его и не запускает процесс.
- Если data connect не состоялся, AQA отправляет CANCEL_SESSION; при потере control connection регистрация освобождается по TTL.
- Если несколько data connections одновременно претендуют на один endpoint, ровно одна может перевести регистрацию в BOUND; остальные отклоняются.
- При переполнении очереди/лимита сессий Bridge возвращает контролируемую ошибку и не создаёт процесс.
- Нельзя восстанавливать связь по timestamp, порядку подключений, имени потока или PID без connectionId.

## 5. Формат диагностических событий

Формат — JSON Lines, одна JSON-запись на строку. Обязательные поля каждой записи: `timestamp` (UTC ISO-8601), `sequence` (монотонный целый номер в пределах `clientSessionId`, начиная с 1) и `event`. Поля контекста добавляются по мере появления:
- `runId`, `testId`, `clientSessionId`, `registrationId` — после успешной регистрации;
- `controlPeerAddress`, `dataLocalAddress`, `dataSourcePort` — при регистрации;
- `connectionId`, `remoteAddress`, `remotePort` — после binding data connection;
- `pid` — после успешного запуска процесса;
- `reason`, `exitCode` — если применимо.

Для отклонённого незарегистрированного data connection runId/testId/clientSessionId могут отсутствовать; такая запись содержит уникальный `connectionAttemptId`, фактический remote endpoint и причину отказа. Секреты и sessionToken никогда не записываются.

Минимальные события: `SESSION_REGISTERED`, `SESSION_BOUND`, `PROCESS_START_REQUESTED`, `PROCESS_STARTED`, `PROCESS_START_FAILED`, `CLIENT_DISCONNECTED`, `PROCESS_EXITED`, `PROCESS_KILLED`, `SESSION_CLOSED`, `SESSION_REJECTED`, `SESSION_EXPIRED`, `SESSION_CANCELLED`, `SESSION_STOP_FAILED`, `FIXTURE_PARTIAL_RESPONSE_WRITTEN`, `TEST_RESPONSE_TRUNCATED` и `TEST_CONTROL_RESPONSE_DROPPED` (только в test profile).

`sequence` назначается Bridge атомарно в момент добавления события в общий append-only журнал. Для события отклонённого незарегистрированного соединения, у которого нет `clientSessionId`, используется отдельная последовательность по `connectionAttemptId`; поле `sequence` монотонно только внутри соответствующего потока идентификатора.

Для каждой регистрации допускается ровно одно терминальное событие из `PROCESS_START_FAILED`, `SESSION_REJECTED`, `SESSION_EXPIRED`, `SESSION_CANCELLED`, `SESSION_CLOSED` или `SESSION_STOP_FAILED`. `PROCESS_EXITED` и `PROCESS_KILLED` являются событиями процесса и не заменяют терминальное событие сессии. События имеют монотонный порядковый номер в пределах сессии. Диагностические сообщения Bridge не попадают в stdout дочернего процесса и MCP data stream. Jenkins архивирует полный журнал; Allure получает события только по точному `runId`/`testId`/`clientSessionId`; `connectionId` добавляется как дополнительный фильтр после binding. Для незарегистрированных connection attempts без test context события не прикрепляются к Allure-тесту. sessionToken не записывается.

## 6. Параллельность

- Один тестовый класс использует одну MCP-сессию и один дочерний процесс.
- Методы внутри класса выполняются последовательно.
- Параллелятся только классы; каждому классу выделяются отдельные socket, clientSessionId, connectionId и PID.
- `AQA_PARALLELISM` не может превышать `MAX_ACTIVE_SESSIONS` Bridge; проверка выполняется до запуска набора.
- При превышении лимита Bridge отклоняет новую регистрацию/data session контролируемо; клиент не должен зависать.

## 7. Таймауты и завершение

Обязательные конфигурационные параметры и начальные значения первой версии:

| Параметр | Начальное значение | Ограничение/назначение |
|---|---:|---|
| `BRIDGE_DATA_BIND_ADDRESS` | `0.0.0.0` | Bind address data listener внутри контейнера |
| `BRIDGE_DATA_PORT` | 8 090 | TCP data port, не публикуется на host |
| `BRIDGE_CONTROL_BIND_ADDRESS` | `0.0.0.0` | Bind address control listener внутри контейнера |
| `BRIDGE_CONTROL_PORT` | 8 091 | TCP control port, не публикуется на host |
| `BRIDGE_CHILD_JAVA` | `java` | Исполняемый файл Java для дочернего процесса; передаётся отдельным аргументом, без shell |
| `MCP_SERVER_JAR_PATH` | `/artifacts/atlassian-mcp-light.jar` | Путь к дочернему JAR; для изолированных process-тестов заменяется fixture JAR |
| `BRIDGE_TEST_DROP_RESPONSE_AFTER_BYTES` | disabled | В test profile: целое N 1–4096; для каждой отдельной data-сессии однократно пересылает первые N байт stdout дочернего процесса, затем закрывает data connection; MCP/JSON-RPC не разбирает |
| `BRIDGE_TEST_DROP_CONTROL_RESPONSE_ONCE` | false | В test profile один раз закрывает control connection без ответа после успешного резервирования первой REGISTER_SESSION; используется только для проверки recovery retry |
| `REGISTRATION_TTL_MS` | 10 000 | Допустимый диапазон 1 000–30 000 |
| `SESSION_RECORD_RETENTION_MS` | 3 600 000 | Хранение terminal status/diagnostics и tombstone после terminal event; 60 000–86 400 000 мс |
| `MAX_ACTIVE_SESSIONS` | 2 | Верхняя граница параллельных дочерних процессов |
| `MAX_PENDING_REGISTRATIONS` | 4 | Не более двух ожидающих регистраций на один активный слот |
| `CONTROL_MAX_MESSAGE_BYTES` | 16 384 | Максимальный размер одной control JSONL-записи |
| `CONTROL_MAX_CONNECTIONS` | 8 | Максимум одновременно открытых control connections |
| `CONTROL_RATE_LIMIT_PER_MINUTE` | 60 | Лимит команд на один source IP |
| `CONTROL_CONNECT_TIMEOUT_MS` | 1 000 | Таймаут AQA подключения к control listener |
| `CONTROL_RESPONSE_TIMEOUT_MS` | 1 000 | Таймаут ответа на одну control-команду |
| `DATA_CONNECT_TIMEOUT_MS` | 2 000 | Таймаут AQA подключения к data listener |
| `PROCESS_START_TIMEOUT_MS` | 10 000 | Верхняя граница создания OS-процесса; не означает готовность MCP-протокола |
| `PROCESS_STOP_TIMEOUT_MS` | 5 000 | Ожидание штатного завершения до force kill |
| `IDLE_SESSION_TIMEOUT_MS` | 90 000 | Таймаут бездействия MCP-сессии; больше MCP response timeout AQA |

AQA `AQA_PARALLELISM` по умолчанию равен 2 и не может превышать `MAX_ACTIVE_SESSIONS`. Значения конфигурации валидируются на старте; production-подобные значения могут быть изменены только явной конфигурацией и повторным прогоном reliability-набора. При закрытии data connection Bridge закрывает stdin процесса, ожидает штатного завершения в пределах `PROCESS_STOP_TIMEOUT_MS`, затем принудительно завершает процесс. При остановке Bridge сначала прекращается приём новых control/data connections, затем stdin закрывается у всех активных дочерних процессов параллельно; каждый процесс ожидается до `PROCESS_STOP_TIMEOUT_MS`, после чего оставшиеся завершаются принудительно. Это укладывается в Compose `stop_grace_period=15s` при `MAX_ACTIVE_SESSIONS=2`. Потоки, сокеты и регистрации освобождаются при всех путях завершения.

`BRIDGE_TEST_DROP_CONTROL_RESPONSE_ONCE` разрешён только в test profile: для первой успешно обработанной `REGISTER_SESSION` Bridge сначала атомарно сохраняет регистрацию, затем закрывает control connection без response и пишет `TEST_CONTROL_RESPONSE_DROPPED`. Следующий точный повтор с тем же requestId возвращает исходный результат. В обычном профиле параметр отсутствует.

`BRIDGE_TEST_DROP_RESPONSE_AFTER_BYTES` разрешён только в test profile и применяется к отдельной выделенной сессии с одним write-запросом: Bridge пересылает ровно N байт из stdout дочернего процесса, записывает `TEST_RESPONSE_TRUNCATED` с `bytesForwarded=N` и закрывает data connection. Fault injection выполняется независимо для каждой сессии, один раз на сессию. Bridge не разбирает JSON-RPC и не меняет payload; fault injection работает только на уровне количества байтов. В обычном профиле параметр отсутствует и механизм выключен.

`PROCESS_START_TIMEOUT_MS` ограничивает время от принятия data connection до успешного создания OS-процесса. `PROCESS_STARTED` означает только успешный возврат `ProcessBuilder.start()` и не подтверждает готовность MCP-протокола; MCP readiness проверяется первым `initialize`/`server/discover` запросом и отдельным AQA response timeout. `PROCESS_START_FAILED` записывается, если создание процесса завершилось IOException или истёк startup deadline. Если Java executable запустился, но JAR некорректен и дочерний процесс завершился, Bridge записывает `PROCESS_STARTED`, затем `PROCESS_EXITED` с ненулевым exit code и терминальное `SESSION_CLOSED`.

## 8. Политика повторов и неопределённый результат

Автоматические повторы допустимы только для read-only вызовов, если доказано, что запрос не был отправлен. Запрещён общий retry для `jira_create_issue`, `jira_add_comment`, `jira_transition`, `confluence_create_page` и `confluence_update_page`.

Если write-запрос мог быть отправлен, но ответ потерян, результат фиксируется как `UNKNOWN_OUTCOME`. До разрешённого повтора выполняется сверка по уникальному маркеру или ID. Создание повторяется только после доказательства отсутствия объекта. Комментарии, переходы и обновления не повторяются общим retry-механизмом. Для `jira_add_comment` нет MCP-инструмента чтения комментариев: при потере ответа UNKNOWN_OUTCOME остаётся неопределённым и требует ручной сверки без повтора. Конфликт версии Confluence update приводит к перечитыванию для диагностики и падению сценария, а не к автоматической перезаписи.

## 9. Устойчивый реестр тестовых данных

Реестр — append-only JSONL event log, одна запись на каждое действие записи. До отправки write-запроса AQA синхронно сохраняет `WRITE_INTENT` с `operationId`, runId, testId, `toolName`, типом/ключом целевого объекта, уникальным marker и временем; полный payload и секреты не сохраняются. После успешного MCP-ответа сохраняется `WRITE_CONFIRMED` с `outcome=SUCCESS`. Поля `objectId`/`objectKey` добавляются, если инструмент возвращает идентификатор. Для `jira_add_comment` MCP не возвращает ID комментария: запись содержит `objectType=JIRA_COMMENT`, `parentObjectKey` задачи и marker, а `objectId` остаётся null. Для `jira_transition` фиксируются ключ задачи и целевой статус; это запись об операции, а не о создании нового объекта. Только создание Jira issue/Confluence page пополняет реестр объектов для последующей административной очистки.

`WRITE_REJECTED` записывается только при однозначном ответе, подтверждающем, что побочный эффект не был применён. Если результат ошибки не доказывает отсутствие побочного эффекта, используется `UNKNOWN_OUTCOME`. При потере ответа или аварии до подтверждения незавершённый intent восстанавливается как `UNKNOWN_OUTCOME`. Каждая строка дописывается атомарно под общей блокировкой и сбрасывается на диск через flush/force до продолжения операции. На старте восстановления незавершённые WRITE_INTENT без терминального события переводятся в UNKNOWN_OUTCOME и требуют сверки. Реестр не является транзакцией с Atlassian API и не исключает неопределённость; его задача — не потерять намерение и обеспечить безопасную последующую сверку.

Jenkins архивирует event log и диагностику при любом исходе Pipeline, если Jenkins agent и workspace остаются доступными. Автоматическое удаление Atlassian-объектов через MCP не выполняется, поскольку соответствующих инструментов в контракте нет.

## 10. Обязательные негативные проверки

- Истёкшая, повторно использованная и неизвестная регистрация.
- Повтор REGISTER_SESSION после потери ответа с тем же requestId и конфликтный повтор clientSessionId с другим requestId/endpoint.
- Несовпадение IP control клиента с заявленным data IP и несовпадение заявленного source port с фактическим remote port.
- Неизвестная команда, malformed JSON, превышение размера сообщения, лимита одновременных control connections и rate limit control channel.
- Конфликт двух регистраций на один endpoint и гонка нескольких data connections.
- Data connection без регистрации и registration TTL expiry.
- Неверный sessionToken для status/diagnostics/cancel и отмена неиспользованной регистрации.
- Параллельные сессии и отсутствие смешения ID, PID и логов.
- Недоступность Bridge, TCP reset, EOF и частичный ответ.
- Ошибка запуска JAR и зависание при завершении процесса.
- Достижение MAX_ACTIVE_SESSIONS.
- SIGTERM Bridge при активных соединениях.
- Потеря ответа после отправки write-запроса и отсутствие автоматического повтора.
- Аварийное завершение AQA/Jenkins и восстановление незавершённого WRITE_INTENT.
- Проверка отсутствия секретов в Jenkins logs, Bridge diagnostics и Allure.
