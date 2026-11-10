# Atlassian MCP Light Autotests

Внешний black-box AQA-проект для проверки [Atlassian MCP Light](https://github.com/Solomentcev/atlassian-mcp-light).

AQA взаимодействует с сервером через его штатный MCP/JSON-RPC интерфейс поверх STDIO. Тесты не импортируют внутренние классы основного проекта и не обращаются напрямую к Jira/Confluence REST API. Фактический контракт классического и stateless MCP-режимов, JSON-RPC ошибок и 13 инструментов описан в [mcp-contract.md](docs/mcp-contract.md).

## Технологии

- Java 17
- Maven
- JUnit 5
- AssertJ
- Jackson
- Allure Report
- Jenkins
- Docker (для Docker-режима запуска сервера и Jenkins-инфраструктуры)

Для первой версии используется собственный минимальный MCP Client на Java/Jackson. MCP SDK и Mockito не добавляются без отдельной технической необходимости.

## Документация

- [Стратегия и план тестирования](docs/test-strategy.md)
- [Каталог тест-кейсов](docs/test-cases.md)
- [Структура Jira-бэклога](docs/jira-backlog.md)
- [Техническая спецификация AQA-фреймворка](docs/technical-specification.md)
- [Фактический MCP/JSON-RPC контракт сервера](docs/mcp-contract.md)
- [Реестр архитектурных рисков и мер контроля](docs/risk-register.md)

## Архитектура запуска

Jenkins получает два независимых репозитория:

1. atlassian-mcp-light-autotests — исходный код AQA.
2. atlassian-mcp-light — исходный код тестируемого сервера на выбранном commit SHA.

Jenkins собирает тестируемый сервер из исходников. Затем AQA запускает отдельный тестовый экземпляр сервера одним из двух способов:

- **Локальный JAR:** AQA запускает Java-процесс через ProcessBuilder.
- **Docker:** AQA запускает Docker CLI через ProcessBuilder; команда docker run -i запускает отдельный контейнер и передаёт его STDIN/STDOUT через потоки Docker CLI.

В обоих режимах MCP Client работает одинаково: отправляет JSON-RPC сообщения в STDIN процесса и читает ответы из STDOUT. Отличается только способ создания процесса. AQA управляет жизненным циклом запущенного тестового экземпляра: запуск до тестов, штатное завершение после тестов и принудительная очистка при сбое или таймауте.

Основной проект не является Maven-зависимостью AQA. Между проектами сохраняется black-box граница: AQA использует только собранный исполняемый артефакт и публичный STDIO/MCP контракт.

## Текущая реализация

В ветке `feature/mcp-client` начата реализация базового STDIO-слоя:

- `DefaultProcessLauncher` запускает команду через `ProcessBuilder`, не объединяя STDOUT и STDERR.
- `ManagedProcess` предоставляет потоки процесса и выполняет штатное/принудительное завершение.
- `StderrCollector` независимо читает STDERR и хранит ограниченный хвост диагностики.
- `McpClient` отправляет JSON-RPC сообщения по STDIN, читает ответы из STDOUT, проверяет request id и поддерживает классический `initialize → notifications/initialized`.
- `McpProcessSession` объединяет запуск процесса, сбор STDERR, MCP handshake и последующее завершение процесса.
- `DockerProcessLauncher` запускает отдельный контейнер через `docker run --rm -i`, без TTY и опубликованных портов. Для контейнера назначаются уникальные имя и label AQA run; cleanup проверяет label, выполняет stop и при необходимости rm -f.

Docker launcher использует тот же `ManagedProcess`, `McpProcessSession` и `McpClient`, что и локальный режим. Для Docker CLI нужны доступный Docker Engine, собранный image и env-файл; AQA не подключается к уже работающему контейнеру.

### Конфигурация запуска тестов

JUnit-аннотация `@McpServerTest` подключает `McpServerExtension`: перед тестами класса создаётся отдельный процесс и выполняется MCP handshake, после класса сессия закрывается. Методы класса выполняются последовательно. В тестовые методы можно внедрять `McpClient` или `McpProcessSession`.

Режим выбирается переменной `AQA_SERVER_MODE` (или одноимённым system property с префиксом `aqa.`). System properties имеют приоритет над environment.

| Переменная | Локальный режим | Docker-режим |
|---|---|---|
| `AQA_SERVER_MODE` | `local` | `docker` |
| `AQA_SERVER_JAR` | Путь к собранному исполняемому JAR | Не используется |
| `AQA_SERVER_JAVA` | Необязательно; по умолчанию Java из текущей JVM | Не используется |
| `AQA_SERVER_WORKING_DIRECTORY` | Необязательно; рабочий каталог процесса | Необязательно; рабочий каталог Docker CLI |
| `AQA_DOCKER_IMAGE` | Не используется | Имя собранного image |
| `AQA_DOCKER_ENV_FILE` | Не используется | Путь к env-файлу для контейнера |
| `AQA_DOCKER_EXECUTABLE` | Не используется | Необязательно; по умолчанию `docker` |
| `AQA_RUN_ID` | Не используется | Базовый ID Jenkins-прогона; к нему добавляется уникальный суффикс на класс |
| `AQA_SERVER_STARTUP_TIMEOUT_SECONDS` | Необязательно; по умолчанию 15 | Необязательно; по умолчанию 15 |
| `AQA_DOCKER_STOP_TIMEOUT_SECONDS` | Не используется | Необязательно; по умолчанию 10 |
| `AQA_MCP_PROTOCOL_VERSION` | Необязательно; по умолчанию `2025-11-25` | Необязательно; по умолчанию `2025-11-25` |

Пример локального запуска:

```bash
AQA_SERVER_MODE=local \
AQA_SERVER_JAR=../atlassian-mcp-light/atlassian-mcp-app/target/atlassian-mcp-light-1.0-SNAPSHOT.jar \
mvn test
```

Пример Docker-режима:

```bash
AQA_SERVER_MODE=docker \
AQA_DOCKER_IMAGE=atlassian-mcp-light:latest \
AQA_DOCKER_ENV_FILE=.runtime.env \
AQA_RUN_ID=${BUILD_TAG:-local} \
mvn test
```

В локальном режиме процесс наследует environment Jenkins agent. В Docker-режиме переменные тестируемого приложения передаются через указанный env-файл. Не передавайте секреты через Maven arguments и не выводите содержимое env-файла в лог.

JUnit-проверки покрывают запуск процесса, раздельный сбор STDERR, JSON-RPC request/response, notifications, классический handshake, stateless metadata и валидацию конфигурации. Добавлены Docker launcher, JUnit lifecycle extension и Allure JUnit 5.

Stateless API клиента:
- `discoverStateless(...)` — `server/discover`;
- `listToolsStateless(...)` — `tools/list`;
- `requestStateless(...)` — произвольный stateless request с обязательной metadata в `params._meta`.

Интеграционные проверки публичного контракта находятся в `McpContractIntegrationTest`: discovery, поддерживаемые версии, точные свойства/required/type/ограничения схем 13 инструментов, annotations, ошибки валидации аргументов и различение MCP tool error и JSON-RPC error. `McpStatelessLifecycleIntegrationTest` отдельно проверяет discovery и tools/list на новом процессе без предварительного classic initialize. Интеграционные тесты не запускаются обычной командой `mvn test`.

Для запуска интеграционных проверок требуется собранный JAR или Docker image тестируемого сервера и тестовая конфигурация Jira/Confluence (обязательные `JIRA_URL`, `JIRA_EMAIL`, `JIRA_TOKEN` передаются серверному процессу):

```bash
AQA_SERVER_MODE=local \
AQA_SERVER_JAR=../atlassian-mcp-light/atlassian-mcp-app/target/atlassian-mcp-light-1.0-SNAPSHOT.jar \
mvn -Pintegration test
```

Для Docker-режима дополнительно задаются `AQA_DOCKER_IMAGE` и `AQA_DOCKER_ENV_FILE`. Обычная команда `mvn test` выполняет только unit-тесты и не требует запуска Atlassian MCP Light.

Локальную сборку и выполнение тестов ещё необходимо подтвердить; до этого реализация не считается проверенной.

## Jenkins Pipeline

1. Checkout AQA-репозитория.
2. Checkout выбранной ревизии основного проекта в отдельный каталог workspace; commit SHA сохраняется в результатах прогона.
3. Сборка основного проекта из исходников в зависимости от выбранного режима запуска.
4. Сборка и запуск AQA-тестов.
5. Публикация JUnit XML и Allure Report.
6. Архивирование диагностических логов и реестра тестовых данных.
7. Cleanup в завершающем блоке Pipeline независимо от результата тестов.

Для локального режима агенту нужны JDK 17 и Maven. Для Docker-режима дополнительно нужны Docker CLI и доступ к Docker Engine. Сам факт запуска Jenkins controller в Docker не означает, что AQA обязана работать в отдельном контейнере.

## Конфигурация и безопасность

- Jira/Confluence credentials хранятся в Jenkins Credentials и передаются только тестируемому серверу через environment.
- Секреты не хранятся в репозитории, не передаются MCP Client без необходимости и не прикладываются к Allure.
- STDOUT процесса зарезервирован под MCP/JSON-RPC. Диагностика читается отдельно из STDERR.
- Не использовать production-проекты, пространства и данные.
- Автоматические повторы неидемпотентных write-операций запрещены. При потере ответа после отправки запроса фиксируется UNKNOWN_OUTCOME.
- В фактическом MCP-контракте нет инструментов удаления Jira-задач и Confluence-страниц. AQA сохраняет реестр созданных объектов для последующей административной очистки, но не удаляет их обходным вызовом Atlassian REST API.

## Статус документации

Документы описывают согласованную целевую архитектуру и требования. Описанная мера не считается реализованной до появления реализации и подтверждающего теста.
