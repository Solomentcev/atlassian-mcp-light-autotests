# Техническая спецификация AQA-фреймворка

Документ описывает фактическую архитектуру AQA и целевые компоненты, которые ещё предстоит реализовать. Статус каждой меры приведён отдельно; наличие требования в этой спецификации не является доказательством его реализации.

## 1. Назначение

AQA — внешний black-box проект для проверки Atlassian MCP Light.

AQA проверяет собранное приложение через публичный MCP/JSON-RPC интерфейс и штатный STDIO транспорт. Тесты не импортируют внутренние классы основного проекта, не используют его как Maven dependency и не обращаются напрямую к Jira/Confluence REST API для проверки функциональности MCP-инструментов.

Ключевые принципы:

- black-box testing;
- единый MCP Client для локального и Docker запуска;
- управление жизненным циклом тестового экземпляра со стороны AQA;
- независимые репозитории и этапы сборки;
- фиксируемая ревизия тестируемого сервера;
- безопасная конфигурация и сохранение диагностических артефактов;
- отсутствие промежуточного сетевого сервиса между AQA и STDIO.

Описанное целевое поведение не считается реализованным до появления кода и подтверждающих тестов.

### 1.1. Статус реализации на 12.11.2026

| Область | Состояние | Подтверждение / ограничение |
|---|---|---|
| Java/Maven/JUnit/AssertJ/Jackson | Реализовано | Maven компилирует test sources с `--release 17` |
| MCP Client и STDIO | Реализовано | Unit- и integration-тесты request/response, notifications, JSON-RPC и framing |
| Classic и stateless session | Реализовано | `McpProcessSession`, JUnit extension и отдельные stateless lifecycle tests |
| Local JAR launcher | Реализовано | Launcher и управление процессом входят в unit/integration инфраструктуру |
| Docker launcher | Реализовано для штатного lifecycle | Jenkins integration run подтвердил запуск отдельных контейнеров и их штатное удаление после тестов; аварийное прерывание Docker CLI отдельно не проверялось |
| Конфигурация AQA | Реализовано | Environment, system properties, `.env`, режимы local/docker, таймауты и уникальный run ID |
| Allure diagnostics | Частично | Есть маскирование и unit-тесты; end-to-end проверка canary-секретов во всех CI-артефактах не выполнена |
| MCP tools contract | Реализовано | Сравнение состава, описаний, JSON Schema и annotations с эталонными TestData |
| Jira/Confluence CRUD integration | Не реализовано | Реальные создание/чтение/изменение бизнес-объектов не покрыты отдельным набором AQA |
| Test data registry / write safety | Не реализовано | Нет устойчивого WRITE_INTENT/UNKNOWN_OUTCOME журнала и retry-policy adapter |
| Jenkins Pipeline | Реализовано | `Jenkinsfile`: checkout двух репозиториев, сборка Docker image, запуск integration profile, публикация JUnit/Allure, cleanup; успешный прогон 57/57 от 12.11.2026 |
| Реальный Docker runtime в CI | Проверено для штатного прогона | Контейнеры запускались и штатно удалялись; аварийный cleanup при потере агента или Docker CLI не подтверждён |

12.11.2026 полный integration-набор был выполнен в Jenkins Pipeline командой `mvn -B -Pintegration clean test`: 57 тестов, 0 failures, 0 errors, 0 skipped, `BUILD SUCCESS`. Pipeline собрал Docker image тестируемого сервера из отдельного checkout, запустил контейнеры для тестовых MCP-сессий, опубликовал JUnit results и сформировал Allure report. Логи подтверждают штатное завершение контейнеров; post-блок не обнаружил оставшихся контейнеров и удалил временный image. Это подтверждает успешный Docker runtime и cleanup при штатном завершении, но не аварийную очистку при потере Jenkins agent/host или Docker Engine.

## 2. Репозитории и стек

| Назначение | Значение |
|---|---|
| Основной проект | https://github.com/Solomentcev/atlassian-mcp-light |
| AQA-проект | https://github.com/Solomentcev/atlassian-mcp-light-autotests |
| Java | 17 |
| Сборка | Maven |
| Тесты | JUnit 5 |
| Assertions | AssertJ |
| JSON | Jackson |
| Отчётность | Allure |
| CI | Jenkins |
| Контейнеризация | Docker |

Основной проект — отдельный multi-module Maven repository. Сборка выполняется из корня его checkout. Конкретный путь и имя исполняемого JAR должны извлекаться из фактического результата Maven-сборки или конфигурации Pipeline, а не дублироваться в нескольких местах как неизменяемая константа.

В каждом прогоне фиксируются repository URL, branch/ref и полный commit SHA тестируемого сервера.

## 3. Компоненты и ответственность

| Компонент | Ответственность |
|---|---|
| Jenkins Pipeline | Checkout двух репозиториев, выбор ревизии сервера, сборка, запуск тестов, публикация результатов и cleanup |
| `McpServerLauncher` | Запуск тестового экземпляра в выбранной среде; реализации — `LocalMcpServerLauncher` и `DockerMcpServerLauncher` |
| `ProcessBuilderLauncher` | Запуск системной команды через Java `ProcessBuilder`, без MCP- или Docker-логики |
| `ManagedProcess` | Доступ к STDIO-потокам, подтверждение завершения дочернего процесса и связанный cleanup |
| MCP Client | Обмен JSON-RPC сообщениями через STDIN/STDOUT, сопоставление request/response и таймауты |
| STDERR collector | Независимое чтение и сохранение диагностического потока |
| JUnit 5 | Управление тестами и lifecycle extensions |
| Allure | Отчёты, вложения и диагностический контекст |
| Test data registry | Целевой компонент; устойчивый журнал write-намерений и созданных объектов пока не реализован |

Bridge, TCP endpoint, control channel и отдельный контейнер-посредник в архитектуру не входят.

## 4. Общая схема

Jenkins получает два репозитория в разные каталоги одного workspace (или в отдельные workspace):

1. AQA repository — тестовый код, например в корень workspace.
2. Server repository — исходный код тестируемого приложения на выбранной ревизии, например в подкаталог `server/`.

Checkout основного проекта не должен перезаписывать `.git` и файлы AQA-репозитория. Все пути к JAR, Docker build context и Maven-командам задаются относительно каталога соответствующего checkout.

Pipeline собирает сервер из исходников. Затем AQA запускает тестовый экземпляр в выбранном режиме. Весь обмен MCP выполняется через STDIO запущенного процесса.

Логическая схема:

AQA tests → MCP Client → Process STDIN/STDOUT → Atlassian MCP Light → Jira/Confluence API

STDERR процесса читается отдельным collector и не смешивается с MCP-ответами.

Сборка основного проекта и сборка AQA — разные этапы одного Pipeline. Основной проект не подключается как Maven dependency AQA. Для каждого прогона используется один выбранный commit SHA сервера. Pipeline собирает артефакт, необходимый выбранному режиму; обязательной сборки одновременно JAR и Docker image нет.

## 5. Способы запуска

### 5.1. Локальный JAR

`LocalMcpServerLauncher` формирует команду Java и передаёт её `ProcessBuilderLauncher`. Концептуальная команда:

    java -jar path/to/atlassian-mcp-app.jar

ProcessBuilder получает executable и аргументы отдельными элементами, без shell-командной строки. AQA получает Process handle и использует его потоки:

- process.getOutputStream() — STDIN тестируемого сервера;
- process.getInputStream() — STDOUT тестируемого сервера;
- process.getErrorStream() — STDERR тестируемого сервера.

MCP Client пишет в STDIN JSONL-сообщения и читает JSONL-ответы из STDOUT. STDERR collector работает независимо от MCP Client.

### 5.2. Docker

`DockerMcpServerLauncher` формирует команду Docker CLI и запускает её через `ProcessBuilderLauncher`. Концептуальная команда:

    docker run --rm --name "aml-${AQA_RUN_ID}" -i --env-file .runtime.env atlassian-mcp-light:latest

Опции:

- -i оставляет STDIN контейнерного процесса открытым и подключённым к STDIN Docker CLI;
- -t не используется, поскольку псевдотерминал может изменить поведение потоков и framing;
- --rm удаляет контейнер после завершения;
- env-file передаёт конфигурацию тестируемому серверу; в Jenkins временный env-файл создаётся из Jenkins Credentials с ограниченными правами доступа и удаляется после прогона. Файл не хранится в репозитории, не включается в Docker image и не архивируется как build artifact.

STDIN/STDOUT Docker CLI используются так же, как потоки локального Java-процесса. MCP Client не знает, что за Process находится Docker CLI, а не java.

Docker CLI не является MCP Bridge и не разбирает протокол. Он предоставляет стандартное подключение потоков запущенного контейнерного процесса.

Контейнер должен быть отдельным тестовым экземпляром, созданным текущим AQA-прогоном. AQA не подключается задним числом к STDIN/STDOUT произвольного уже работающего контейнера или процесса.

### 5.3. Единый интерфейс launcher

`McpServerLauncher` скрывает выбор среды запуска от `McpProcessSession`. Оба режима возвращают одинаковый `ManagedProcess`; низкоуровневый `ProcessBuilderLauncher` не знает о MCP или Docker lifecycle.

Оба режима предоставляют MCP Client единый объект управления процессом с:

- input stream для отправки запросов;
- output stream для чтения ответов;
- error stream/collector для диагностики;
- методом проверки состояния процесса;
- штатным завершением;
- принудительным завершением по timeout;
- cleanup, вызываемым независимо от результата теста.

Различие локального и Docker режимов должно находиться в реализации launcher/configuration, а не в MCP Client и протокольных тестах.

## 6. Жизненный цикл тестового экземпляра

Базовая последовательность:

1. Прочитать и проверить конфигурацию запуска.
2. Создать локальный Java-процесс или новый Docker container.
3. Запустить STDERR collector до активного обмена сообщениями.
4. Проверить, что процесс не завершился сразу после запуска.
5. Выполнить протокольный запрос готовности: classic initialize или stateless server/discover.
6. Выполнить тесты.
7. Закрыть STDIN и предоставить серверу возможность завершиться штатно.
8. Дождаться завершения в пределах configured shutdown timeout.
9. Если процесс не завершился после штатной остановки, выполнить `destroyForcibly()` и обязательно подтвердить завершение повторным ожиданием; если процесс остался жив, cleanup завершается явной ошибкой.
10. Завершить collector, сохранить STDOUT/STDERR диагностику и освободить ресурсы.

ProcessBuilder.start() подтверждает только создание OS-процесса, но не готовность MCP-сервера. Готовность подтверждается корректным протокольным ответом.

Lifecycle реализован через JUnit extension. Cleanup обязателен в finally/after callbacks и должен выполняться при assertion failure, exception и timeout.

### Docker cleanup

В Docker-режиме завершение локального процесса Docker CLI само по себе не должно считаться достаточным доказательством удаления контейнера. Launcher должен назначать контейнеру уникальное имя/идентификатор и сохранять его. При teardown он проверяет состояние контейнера и при необходимости вызывает docker stop; если контейнер не завершился в отведённое время — docker rm -f. --rm выполняет автоматическое удаление после штатного завершения, но не заменяет защитный cleanup на стороне AQA.

Cleanup ограничивается ресурсами, созданными конкретным AQA run. Нельзя останавливать контейнеры по общему имени или затрагивать чужие Docker resources.

## 7. MCP Client и STDIO framing

MCP Client реализует минимальный транспортный слой поверх потоков Process:

- UTF-8;
- одно JSON-RPC сообщение на строку;
- запись сообщения с завершающим newline и flush;
- чтение строк из STDOUT;
- десериализация JSON;
- сопоставление response с request id;
- обработка notification без ожидания response;
- response timeout;
- корректная обработка EOF и завершения процесса.

MCP Client не читает STDERR как часть протокола. STDERR collector сохраняет диагностические строки независимо, ограничивая объём буфера/вложений, чтобы не допустить неограниченного потребления памяти.

На первом этапе не требуется поддерживать несколько одновременных MCP-клиентов на одном STDIO-процессе. Один процесс обслуживает одну клиентскую сессию. Запросы одного клиента сериализуются или управляются одним reader loop с сопоставлением request id; конкурентное чтение одного InputStream разными тестами запрещено.

Точные правила JSON-RPC, classic lifecycle, stateless metadata, tools и error codes определены в [mcp-contract.md](mcp-contract.md).

## 8. Параллельность и изоляция

Один тестовый процесс принадлежит одной MCP-сессии. Независимые сессии требуют независимых процессов/контейнеров.

Каждый класс с `@McpServerTest` получает отдельный процесс и одну MCP-сессию. Методы класса используют эту сессию последовательно. Тесты, которым требуется начальное состояние NEW или отдельный lifecycle, запускают собственный процесс на каждый тестовый метод.

Правила параллельности:

- методы одного тестового класса, использующие одну MCP-сессию, выполняются последовательно;
- разные тестовые классы выполняются параллельно с ограничением в четыре потока; методы внутри класса выполняются последовательно;
- параллельный запуск включён через `src/test/resources/junit-platform.properties`;
- каждый процесс имеет отдельные STDIN/STDOUT/STDERR collectors;
- каждый прогон имеет уникальный AQA_RUN_ID;
- Docker container name получает уникальный суффикс для каждого экземпляра сервера;
- общие тестовые данные не изменяются параллельно без явной синхронизации.

Параллельный запуск не должен приводить к совместному чтению одного потока, смешению ответов, взаимному завершению процессов или пересечению cleanup.

## 9. Конфигурация и секреты

Конфигурация разделяется на:

1. параметры запуска AQA (режим, путь к JAR или Docker image, таймауты, теги тестов);
2. параметры тестируемого сервера (Jira/Confluence URL и credentials, лимиты приложения);
3. параметры Jenkins (repository refs, credentials IDs, workspace, report paths).

Jira/Confluence credentials хранятся в Jenkins Credentials. Они передаются только тестируемому процессу через environment. Не хранить секреты в git, Maven arguments, Allure attachments или диагностических логах. Не выводить полный environment.

Локальная разработка может использовать локальный env-файл, исключённый из git. В Jenkins env-файл формируется из credentials или переменные environment передаются напрямую.

### Реализация конфигурации и JUnit lifecycle

Реализация первой версии использует `config.McpServerConfiguration`, которая читает system properties `aqa.*` (приоритет) и environment variables `AQA_*`.

- `AQA_SERVER_MODE=local`: обязателен `AQA_SERVER_JAR`; запускается `<java> -jar <jar>`.
- `AQA_SERVER_MODE=docker`: обязательны `AQA_DOCKER_IMAGE` и `AQA_DOCKER_ENV_FILE`; запускается отдельный контейнер через Docker launcher.
- `AQA_SERVER_STARTUP_TIMEOUT_SECONDS` задаёт таймаут MCP readiness handshake (по умолчанию 15 секунд).
- `AQA_DOCKER_STOP_TIMEOUT_SECONDS` задаёт время штатной остановки контейнера (по умолчанию 10 секунд).
- `AQA_RUN_ID` задаёт базовый ID Jenkins-прогона; каждый создаваемый экземпляр сервера получает отдельный уникальный суффикс.
- `AQA_MCP_PROTOCOL_VERSION` задаёт версию классического MCP lifecycle (по умолчанию `2025-11-25`).

Аннотация `@McpServerTest` из пакета `ru.solomentsev.aqa.support.junit` подключает `McpServerExtension`. Extension создаёт одну `McpProcessSession` в `BeforeAllCallback`, выполняет classic initialize handshake, предоставляет `McpClient` или `McpProcessSession` через parameter resolver и закрывает сессию в `AfterAllCallback`. Методы класса выполняются последовательно за счёт `junit.jupiter.execution.parallel.mode.default=same_thread`; разные классы выполняются параллельно за счёт `junit.jupiter.execution.parallel.mode.classes.default=concurrent`. Ограничение пула — четыре потока. Для stateless lifecycle `McpServerConfiguration.startStatelessSession()` создаёт отдельный процесс без classic initialize; готовность подтверждается первым stateless-запросом.
### Stateless client и интеграционные проверки

`McpClient.requestStateless` добавляет в `params._meta` обязательные поля `io.modelcontextprotocol/protocolVersion` и `io.modelcontextprotocol/clientCapabilities`, а также необязательное `io.modelcontextprotocol/clientInfo`. Исходный объект params не изменяется; повторное поле `_meta` отклоняется до отправки запроса.

Для типовых запросов доступны `discoverStateless` и `listToolsStateless`. Версия stateless-протокола задаётся вызывающим тестом отдельно от версии классического initialize lifecycle.

Интеграционные классы помечены условием `AQA_RUN_INTEGRATION=true` и запускаются Maven-профилем `integration`.

`McpContractIntegrationTest` проверяет:
- discovery и опубликованные сервером stateless protocol versions;
- все 13 имён инструментов, точный набор свойств, required, типы, строковые minLength и числовые границы inputSchema;
- annotations readOnlyHint, destructiveHint, idempotentHint и openWorldHint;
- отклонение неподдерживаемой версии с JSON-RPC кодом `-32022`;
- ошибки отсутствующих обязательных, лишних и пустых аргументов, границы limit и условие title/content для Confluence update;
- отличие MCP tool error от JSON-RPC error.

`McpStatelessLifecycleIntegrationTest` запускает отдельный процесс без classic initialize и проверяет, что classic `tools/list` до READY возвращает `-32001`, `ping` доступен до READY, а stateless `tools/list` и `tools/call` работают ещё до `server/discover`.

`McpTransportContractIntegrationTest` проверяет STDIO framing и JSON-RPC поведение реального процесса: пропуск пустых строк, parse error с продолжением обработки следующих сообщений, некорректную форму запроса, неизвестный метод, notification без request id с последующим ping, повторный initialize и сохранение UTF-8 в сообщении об ошибке (MCP-038). Отсутствие отдельного response на notification напрямую этим набором не проверяется.

Интеграционный прогон запускает реальный сервер и поэтому требует тестовой конфигурации Jira/Confluence. Проверки discovery/list и неизвестного инструмента не создают и не изменяют Atlassian-объекты. Обычный `mvn test` пропускает интеграционный класс и не требует серверного JAR или Docker image.

## 10. Jenkins Pipeline

**Статус: реализован; успешный прогон подтверждён 12.11.2026.** Реализация находится в корневом `Jenkinsfile`.

Pipeline выполняет следующие этапы:

1. **Checkout AQA** — получает репозиторий через `checkout scm`.
2. **Checkout server** — получает `Solomentsev/atlassian-mcp-light` в каталог `server` из ветки `master`, фиксирует полный commit SHA и сохраняет URL, branch и SHA в `server-revision.txt`.
3. **Build Docker image** — выполняет `docker build --pull` из корня server checkout. Dockerfile основного проекта самостоятельно собирает приложение на builder stage.
4. **Integration tests** — получает Secret file credential `atlassian-mcp-env`, передаёт путь к нему через `AQA_DOCKER_ENV_FILE` и запускает `mvn -B -Pintegration clean test` с `AQA_SERVER_MODE=docker` и уникальными image/run ID.
5. **Publish** — Jenkins JUnit plugin записывает результаты из `target/surefire-reports`; Jenkins Allure plugin формирует и архивирует HTML report из `target/allure-results`; `server-revision.txt` архивируется как build artifact.
6. **Cleanup** — в `post { always { ... } }` удаляются оставшиеся контейнеры с префиксом текущего run ID и временный Docker image.

Подтверждённый прогон: 57 тестов, 0 failures, 0 errors, 0 skipped; JUnit и Allure публикация успешны. Docker launcher штатно завершил тестовые контейнеры; дополнительный cleanup не обнаружил оставшихся контейнеров. `disableConcurrentBuilds()` запрещает параллельные сборки одной Jenkins job.

Jenkins agent должен иметь Git, Java 17, Maven, Docker CLI и доступ к Docker Engine. В текущей конфигурации Jenkins controller работает в Docker и использует подключённый Docker socket. Credentials хранятся в Jenkins Credentials как Secret file; содержимое env-файла не выводится в лог.

Границы подтверждения: аварийное завершение при потере Jenkins agent/host или Docker Engine не проверялось; `post { always { ... } }` может выполнить cleanup только пока доступны agent и Docker Engine. Отдельная end-to-end проверка отсутствия canary-секретов во всех CI-артефактах также не выполнялась.

## 11. Тестовые данные и write safety

AQA взаимодействует с Jira/Confluence только через MCP-инструменты. Все созданные объекты маркируются уникальным run/test marker и регистрируются.

До отправки write-запроса AQA синхронно сохраняет WRITE_INTENT. После однозначного успешного ответа сохраняется WRITE_CONFIRMED; при однозначном отказе без побочного эффекта — WRITE_REJECTED. Если запрос мог быть отправлен, но ответ потерян, сохраняется UNKNOWN_OUTCOME.

Автоматический retry для write-инструментов запрещён. Создание можно повторить только после сверки, подтверждающей отсутствие объекта. Для комментариев Jira и истории переходов Jira MCP-контракт не предоставляет необходимых read-инструментов, поэтому неоднозначный исход может требовать ручной сверки.

В текущем MCP-контракте отсутствуют инструменты удаления Jira issues и Confluence pages. AQA не вызывает Atlassian REST API напрямую для очистки; реестр используется для последующей административной очистки.

### 11.1. Формат и восстановление реестра

**Целевой дизайн; TestDataManager в текущем коде отсутствует.** После реализации он должен вести append-only JSONL event log. Каждая запись содержит как минимум `event`, `timestamp`, `operationId`, `AQA_RUN_ID`, `testId` и `toolName`. Для write-операций дополнительно сохраняются тип/ключ целевого объекта и уникальный marker. Полный payload запроса, credentials, Authorization headers и иные секреты в реестр не записываются.

До отправки write-запроса синхронно сохраняется newline-terminated `WRITE_INTENT`; запись сбрасывается на диск до отправки запроса. После однозначного успешного ответа записывается `WRITE_CONFIRMED`, после однозначного отказа без побочного эффекта — `WRITE_REJECTED`. Если запрос мог быть отправлен, но подтверждение не получено, записывается `UNKNOWN_OUTCOME`. Незавершённый intent при восстановлении также считается `UNKNOWN_OUTCOME`, а не подтверждённой операцией.

Если последняя строка журнала не завершена newline, её байты сохраняются в отдельный файл повреждённого хвоста, а основной журнал восстанавливается до последней полной строки. Неполная строка не считается отправленным intent. Полный `WRITE_INTENT` без терминального события сохраняется и требует сверки; повторная отправка запрещена.

В реестр объектов для последующей административной очистки включаются созданные Jira issues и Confluence pages. Для комментария Jira и перехода статуса фиксируется операция и родительский объект, но они не считаются новыми объектами с отдельным ID, если MCP-инструмент его не возвращает.

Потеря ответа для проверки retry-политики моделируется на стороне тестового MCP Client adapter: он может намеренно не передать тесту один уже полученный ответ. Это проверяет поведение AQA при неопределённом для вызывающего кода результате и не требует изменения тестируемого сервера или введения промежуточного процесса. Такой сценарий проверяет retry policy AQA, а не отказоустойчивость самого STDIO транспорта.

## 12. Логи и отчётность

Сохраняются:

- JUnit XML;
- Allure results/report с labels/tags Protocol, Process, Configuration, Jira, Confluence, Negative, Integration и Test Data;
- commit SHA основного проекта;
- выбранный launcher mode;
- stdout/stderr diagnostics тестируемого процесса;
- логи Jenkins stage;
- реестр WRITE_INTENT/WRITE_CONFIRMED/WRITE_REJECTED/UNKNOWN_OUTCOME.

Allure labels и структура сценария:

- `@Epic` — крупная область ответственности;
- `@Feature` — функциональная область внутри эпика;
- `@Story` — конкретный сценарий; для кейсов из `docs/test-cases.md` сохраняется ID кейса;
- `@Severity` — P0 → BLOCKER, P1 → CRITICAL, P2 → NORMAL;
- JUnit `@Tag` — тип/область теста и тег `case-<ID>` для связи с каталогом;
- `@DisplayName` и `@Description` — читаемое название, предусловия, шаги и ожидаемый результат;
- `Allure.step` — только фактические действия: отправка/получение MCP-сообщений и управление lifecycle процесса. Проверки выполняются вне шагов и снабжаются сообщениями, объясняющими ожидаемое поведение.
- Сценарные и интеграционные тестовые классы находятся в пакетах `tests.*`. `DockerMcpServerLauncherTest` расположен рядом с `DockerMcpServerLauncher` в пакете `process`, поскольку проверяет package-private построитель команды без расширения публичного API. MCP client/session, process launchers, config, JUnit support и diagnostics вынесены в отдельные инфраструктурные пакеты. Общие параметры и тестовые данные находятся в `testdata`.
- `AllureDiagnosticsTest` проверяет собственную функцию маскирования секретов; отдельные тесты стороннего Allure framework не создаются.

Результаты Allure сохраняются в `target/allure-results`, JUnit XML — в `target/surefire-reports`. HTML-отчёт локально формируется через Allure Maven Plugin 3.1.0 с явно закреплённым runtime Allure Report 2.39.0 (классический интерфейс Allure 2) командами `mvn allure:serve` (просмотр в браузере) или `mvn allure:report` (статический HTML в `target/site/allure-maven/index.html`); в Jenkins используется Jenkins Allure plugin.

Для JSON-RPC обмена к Allure прикладываются только безопасные summaries: method/request id, поля верхнего уровня, error code, resultType и количество элементов; params и текстовый content не сохраняются. При падении интеграционного теста STDERR тестируемого процесса может прикладываться к Allure после маскирования Authorization headers и значений TOKEN/PASSWORD/SECRET. Сырые JSON-RPC payload и текстовые данные ответов Atlassian не прикладываются. Credentials, Authorization headers и другие секреты запрещено записывать в реестр, логи или attachments.

STDOUT тестируемого процесса является MCP data stream и не должен загрязняться диагностикой. STDERR собирается отдельно; маскирование выполняется до публикации диагностических вложений.

## 13. Таймауты и отказоустойчивость

Таймауты задаются конфигурацией, а не разбросанными константами:

- process startup timeout;
- MCP response timeout;
- graceful shutdown timeout;
- forced termination timeout;
- Docker stop timeout.

Часть таймаутов уже задаётся конфигурацией AQA; остальные параметры и сценарии отказоустойчивости должны быть закреплены при дальнейшей реализации. Process startup timeout не заменяет MCP readiness check. При EOF, process exit или timeout MCP Client возвращает контролируемую ошибку с диагностическим контекстом.

STDERR collector должен продолжать чтение независимо от того, читает ли тест ответы MCP. Если collector завершился с ошибкой, это должно быть видно в диагностике, но не должно незаметно блокировать процесс.

## 14. Критерии приёмки архитектуры

- AQA получает основной проект из отдельного Git checkout и фиксирует commit SHA.
- Основной проект собирается из исходников Jenkins Pipeline.
- Локальный JAR и Docker режимы используют один MCP Client.
- AQA самостоятельно создаёт и завершает тестовый экземпляр.
- STDOUT используется только для MCP/JSON-RPC, STDERR читается отдельно.
- В AQA нет зависимости от внутренних классов тестируемого приложения.
- Нет Bridge, TCP proxy, control channel или обязательного промежуточного контейнера.
- Cleanup проверен при успехе, assertion failure, timeout и аварийном завершении.
- Credentials не попадают в репозиторий, логи и отчёты.
- Архитектурные меры отмечаются реализованными только после тестового подтверждения.
