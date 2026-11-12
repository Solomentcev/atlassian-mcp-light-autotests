# Стратегия и план тестирования

Документ разделяет текущую автоматизацию и целевой объём. Наличие сценария в стратегии или каталоге не означает, что он уже реализован.

## 1. Объект тестирования

Atlassian MCP Light — Java 17 MCP-сервер для работы с Jira и Confluence через REST API. Основной транспорт — STDIO: сервер читает JSON-RPC сообщения из STDIN и записывает ответы в STDOUT. Диагностические сообщения направляются в STDERR.

Репозиторий: https://github.com/Solomentcev/atlassian-mcp-light

Сервер состоит из Maven-модулей:

- atlassian-core — MCP-протокол, конфигурация и общая инфраструктура;
- atlassian-jira — инструменты Jira;
- atlassian-confluence — инструменты Confluence;
- atlassian-mcp-app — точка входа и сборка приложения.

Исполняемый JAR собирается из корня основного репозитория Maven-командой ./mvnw clean package. Фактический путь и имя артефакта определяются актуальной конфигурацией сборки; AQA не должно полагаться на неподтверждённое имя файла.

## 2. Цель тестирования

Проверить внешний контракт сервера через MCP Client и штатный STDIO транспорт:

- запуск приложения и готовность к обмену сообщениями;
- корректность передачи JSON-RPC через STDIN/STDOUT;
- классический и stateless MCP lifecycle;
- состав и схемы инструментов;
- корректность вызовов Jira/Confluence;
- обработку ошибок и завершение процесса;
- воспроизводимый запуск в Jenkins.

## 3. Границы тестирования

### Входит в объём

1. Запуск тестового экземпляра сервера из собранного JAR.
2. STDIO framing: UTF-8, JSONL, последовательность сообщений, EOF.
3. Разделение потоков: MCP-ответы в STDOUT, диагностика в STDERR.
4. JSON-RPC: запросы, уведомления, идентификаторы, ошибки и некорректные сообщения; сохранение UTF-8 в имени метода и тексте error.message.
5. Классический initialize/notifications/initialized lifecycle.
6. Stateless server/discover, tools/list и tools/call.
7. Контракт 13 MCP-инструментов: имена, схемы аргументов, результат и ошибки.
8. Интеграция с Jira и Confluence через MCP-инструменты и реальные тестовые API.
9. Запуск и остановка процесса в локальном и Docker режимах.
10. Отчётность JUnit/Allure и выполнение в Jenkins.

### Не входит в первую версию

- нагрузочное и стресс-тестирование;
- полноценное измерение производительности;
- тестирование UI Jira/Confluence;
- unit-тестирование внутренних Java-классов основного приложения;
- прямые вызовы Jira/Confluence REST API из AQA для проверки функциональности MCP-инструментов;
- тестирование сторонних MCP-клиентов как самостоятельных продуктов;
- подключение к произвольному уже работающему процессу сервера.

Проверки времени ответа допускаются как sanity-check с техническими таймаутами, но не как performance benchmark.

## 4. Архитектура взаимодействия

Jenkins получает исходный код обоих репозиториев в разные каталоги одного workspace (или отдельные workspace), чтобы checkout сервера не перезаписывал AQA-репозиторий, и собирает тестируемый сервер из выбранной ревизии. AQA запускает отдельный тестовый экземпляр сервера и владеет его жизненным циклом.

Поддерживаются два способа запуска:

- **Локальный JAR:** ProcessBuilder запускает Java с аргументами -jar и путём к JAR.
- **Docker:** ProcessBuilder запускает Docker CLI с командой docker run -i. Docker CLI передаёт STDIN контейнерного процесса и возвращает его STDOUT в собственные потоки.

MCP Client не зависит от способа запуска. Он получает Process, пишет запросы в process.getOutputStream() и читает ответы из process.getInputStream(). STDERR читается отдельным потоком, чтобы не допустить заполнения pipe и блокировки процесса.

AQA не подключается к STDIN/STDOUT процесса, который был запущен независимо от неё. Для каждого прогона AQA сама создаёт тестовый экземпляр. Для Docker-режима запускается новый контейнер, а не выполняется подключение к PID 1 уже работающего контейнера.

AQA не импортирует внутренние классы основного приложения, не подменяет его сервисы и не использует его исходники как Maven dependency. Проверяется собранное приложение через публичный протокол.

## 5. Уровни проверок

### 5.1. Process и STDIO tests

Проверяется:

- успешный запуск приложения и получение протокольного ответа;
- корректная передача JSONL-сообщений через STDIN и STDOUT;
- отсутствие логов и постороннего текста в STDOUT;
- независимое чтение STDERR;
- обработка нескольких последовательных запросов;
- корректная обработка EOF;
- завершение процесса после тестов;
- принудительное завершение при превышении timeout;
- очистка процесса/контейнера после ошибки теста;
- одинаковое поведение MCP Client в локальном и Docker режимах.

Сам факт успешного ProcessBuilder.start() не означает готовность MCP-сервера. Готовность подтверждается протокольным запросом initialize или server/discover.

### 5.2. MCP/JSON-RPC protocol tests

Проверяются публичные сообщения и состояния MCP. Эти тесты по возможности не зависят от доступности Jira/Confluence. Фактические поля, схемы и коды ошибок приведены в [mcp-contract.md](mcp-contract.md).

Обязательные сценарии:

- classic: initialize → notifications/initialized → tools/list/tools/call;
- stateless: server/discover, затем tools/list/tools/call с per-request metadata;
- корректное различение JSON-RPC error и MCP tool result с isError=true;
- сохранение request id;
- обработка некорректных сообщений без необоснованного завершения процесса.

### 5.3. Integration tests

В текущей реализации integration-классами запускается реальный Atlassian MCP Light и проверяются его публичные MCP/JSON-RPC контракт, lifecycle и STDIO transport. Это black-box integration с процессом сервера, но не полный набор бизнес-интеграций с Jira/Confluence.

Реальные Jira/Confluence CRUD-сценарии через MCP-инструменты, выделенные project/space, permission profiles и заранее подготовленные данные остаются целевым следующим этапом.

### 5.4. End-to-end tests

Целевой критичный путь: тест → MCP Client → STDIO процесса Atlassian MCP Light → MCP tool → Atlassian API → результат. Полноценные end-to-end проверки реальных Jira/Confluence операций в текущей версии ещё не реализованы; существующие integration-классы проверяют MCP contract, lifecycle и transport.

## 6. Инструменты под тестированием

### Jira

| Инструмент | Назначение |
|---|---|
| jira_get_issue | Получение задачи по ключу |
| jira_search | Поиск задач по JQL |
| jira_create_issue | Создание задачи |
| jira_transition | Перевод задачи в другой статус |
| jira_add_comment | Добавление комментария |
| jira_get_project | Получение проекта |
| jira_get_user | Получение пользователя |

### Confluence

| Инструмент | Назначение |
|---|---|
| confluence_get_page | Получение страницы по ID |
| confluence_list_spaces | Получение списка пространств |
| confluence_get_space | Получение пространства |
| confluence_search | Поиск страниц по CQL |
| confluence_create_page | Создание страницы |
| confluence_update_page | Обновление страницы |

## 7. Приоритеты и теги

В коде используются JUnit tags; Maven profiles выбирают subset:

| Tag | Назначение |
|---|---|
| `unit` | Unit-проверки AQA-инфраструктуры |
| `integration` | Тесты с запущенным экземпляром Atlassian MCP Light |
| `protocol`, `contract`, `transport`, `lifecycle` | Тематическая классификация MCP-проверок |
| `negative` | Негативные сценарии |
| `smoke` | Ограниченный набор интеграционных smoke-проверок |
| `regression` | Набор unit/integration классов, помеченных для регрессии |
| `case-<ID>` | Связь автоматизированного метода с ID из `test-cases.md` |

Команды:

```bash
mvn clean test
mvn -Pintegration,smoke clean test
mvn -Pintegration,regression clean test
mvn -Pintegration clean test
```

Профиль `integration` включает интеграционные классы через system property `AQA_RUN_INTEGRATION=true`. Профили `smoke` и `regression` фильтруют JUnit tags через Surefire. В текущем smoke-наборе есть безопасный read-вызов Confluence (MCP-021), поэтому нужен доступный тестовый Atlassian tenant. Текущий набор не содержит Jira/Confluence CRUD regression suite.


## 8. Тестовое окружение и данные

Для запуска интеграционных классов требуется:

- собранный исполняемый JAR или Docker image тестируемого сервера;
- валидные `JIRA_URL`, `JIRA_EMAIL`, `JIRA_TOKEN`, поскольку приложение проверяет обязательную конфигурацию при старте;
- для MCP-021 — доступный Confluence read-инструмент и тестовая учётная запись с соответствующими правами;
- доступ к Java 17/Maven; для Docker mode дополнительно Docker CLI и Docker Engine.

AQA configuration загружается из system properties, environment и локального `.env`. В local mode серверный процесс наследует подготовленное окружение; в Docker mode конфигурация приложения передаётся через `AQA_DOCKER_ENV_FILE`. Credentials нельзя записывать в Git, Maven arguments, логи или Allure attachments.

**Пока не реализованы:** environment preflight, отдельные Jira/Confluence CRUD fixtures, устойчивый registry созданных объектов и автоматизированная очистка. MCP-контракт не содержит delete tools; прямые REST-вызовы из AQA для удаления/проверки бизнес-функций запрещены.


## 9. Управление процессом и параллельность

AQA отвечает за запуск, ожидание готовности и остановку тестового экземпляра. Shutdown выполняется через JUnit lifecycle и защитный cleanup, который срабатывает также при ошибке теста. Сначала используется штатное завершение; по истечении timeout процесс завершается принудительно.

Docker launcher запускает отдельный контейнер с уникальным именем для каждой MCP-сессии и использует `docker run -i --rm`. Успешный Jenkins integration run подтвердил реальный запуск контейнеров и их штатное удаление после завершения тестов. Принудительное прерывание Docker CLI и cleanup после потери Jenkins agent/host отдельно не проверялись.

Одна MCP-сессия соответствует одному STDIO-процессу. JUnit 5 параллельно выполняет независимые тестовые классы с фиксированным пределом в четыре потока; методы внутри класса выполняются последовательно. Каждый класс с `@McpServerTest` получает отдельный процесс и MCP-сессию. Сценарии, которым требуется состояние NEW, например проверки lifecycle, запускают отдельный процесс на каждый тестовый метод. Один процесс не разделяется между независимыми MCP-клиентами. Docker-контейнер получает уникальное имя для каждого экземпляра сервера. Изоляция процессов не изолирует общие Jira/Confluence данные: интеграционные сценарии должны использовать уникальные маркеры и реестр созданных объектов.

## 10. Jenkins Pipeline

**Статус: реализован; успешный прогон подтверждён 12.11.2026.**

Корневой `Jenkinsfile` выполняет checkout AQA и основного проекта в разные каталоги, фиксирует commit SHA сервера, собирает Docker image, получает env-файл из Jenkins Secret file credential `atlassian-mcp-env`, запускает `mvn -B -Pintegration clean test`, публикует JUnit XML и Allure report, архивирует `server-revision.txt` и удаляет ресурсы текущего запуска в `post { always { ... } }`.

Подтверждённый прогон: 57 тестов, 0 failures, 0 errors, 0 skipped. Jenkins agent должен иметь Git, Java 17, Maven, Docker CLI и доступ к Docker Engine. `disableConcurrentBuilds()` предотвращает параллельные запуски одной job. Cleanup после потери agent/host или Docker Engine не гарантируется; принудительное прерывание Docker CLI отдельно не проверялось.

## 11. Обработка write-операций

**Статус: политика согласована, но write-safety инфраструктура не реализована.**

Целевые правила:

- автоматический повтор write-запросов запрещён;
- перед отправкой write-запроса сохраняется `WRITE_INTENT`;
- подтверждённый ответ фиксируется как `WRITE_CONFIRMED`;
- однозначный отказ без побочного эффекта — `WRITE_REJECTED`;
- возможная отправка без подтверждённого ответа — `UNKNOWN_OUTCOME`;
- повторная отправка допускается только после отдельной сверки, исключающей уже выполненную операцию.

Для Jira comments и переходов статуса может потребоваться ручная сверка: MCP-контракт не предоставляет чтение комментариев или историю переходов. Устойчивый JSONL registry, recovery и adapter для имитации потери ответа ещё предстоит реализовать.


## 12. Критерии готовности AQA

### Реализовано в текущей версии

- единый MCP Client и process session для local/Docker launcher abstraction;
- STDIO/JSON-RPC обмен, classic и stateless lifecycle;
- AQA configuration из system properties, environment и `.env`;
- JUnit lifecycle extension и параллельное выполнение независимых классов;
- unit-покрытие MCP Client, конфигурации, process/session, Docker command builder и Allure diagnostics;
- интеграционные проверки MCP contract, stateless lifecycle и STDIO/JSON-RPC поведения реального процесса.

### Осталось реализовать или подтвердить

- принудительное прерывание Docker CLI и cleanup после аварийного завершения;
- полноценные Jira/Confluence CRUD integration tests и fixtures;
- environment preflight;
- устойчивый test data registry, write-safety и recovery;
- запуск smoke/regression профилей из Jenkins по параметрам job;
- end-to-end проверку отсутствия секретов в CI logs и Allure attachments.

Архитектурная мера считается закрытой только после реализации и подтверждающего теста; проектирование и наличие описания в документации не являются подтверждением.

