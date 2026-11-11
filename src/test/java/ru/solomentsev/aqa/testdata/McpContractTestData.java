package ru.solomentsev.aqa.testdata;

import org.junit.jupiter.params.provider.Arguments;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Наборы данных и эталонный публичный контракт MCP-инструментов.
 *
 * <p>Данные отделены от тестовых сценариев. Контракт описывает только
 * наблюдаемую через MCP JSON-RPC схему, не ссылаясь на реализацию сервера.</p>
 */
public final class McpContractTestData {

    private static final Map<String, ToolContract> EXPECTED_TOOLS = createExpectedTools();

    private McpContractTestData() {
    }

    public static Stream<Arguments> getMissingStatelessMetadataCases() {
        return Stream.of(
                Arguments.of("_meta", McpTestData.getServerDiscoverMethod()),
                Arguments.of("clientCapabilities", McpTestData.getToolsListMethod()),
                Arguments.of("protocolVersion", McpTestData.getToolsListMethod())
        );
    }

    public static Stream<Arguments> getRequiredArgumentCases() {
        return Stream.of(
                Arguments.of("null", (String) null),
                Arguments.of("пустая строка", ""),
                Arguments.of("строка из трёх пробелов", "   ")
        );
    }

    public static Stream<Arguments> getLimitBoundaryCases() {
        return Stream.of(
                Arguments.of("jira_search", "jql", JiraTestData.getSearchJql()),
                Arguments.of("confluence_search", "cql", ConfluenceTestData.getSearchCql()),
                Arguments.of("confluence_list_spaces", null, null)
        );
    }

    public static Map<String, ToolContract> getExpectedTools() {
        return EXPECTED_TOOLS;
    }

    private static Map<String, ToolContract> createExpectedTools() {
        return Map.ofEntries(
                Map.entry("jira_get_issue", tool(
                        Map.of("issue_key", "string"), Set.of("issue_key"), true, false, true, true)),
                Map.entry("jira_search", tool(
                        Map.of("jql", "string", "limit", "integer"), Set.of("jql"), true, false, true, true)),
                Map.entry("jira_create_issue", tool(
                        Map.of("project_key", "string", "summary", "string", "description", "string", "issue_type", "string"),
                        Set.of("project_key", "summary", "description"), false, false, false, true)),
                Map.entry("jira_transition", tool(
                        Map.of("issue_key", "string", "transition_name", "string"),
                        Set.of("issue_key", "transition_name"), false, true, false, true)),
                Map.entry("jira_add_comment", tool(
                        Map.of("issue_key", "string", "comment", "string"),
                        Set.of("issue_key", "comment"), false, false, false, true)),
                Map.entry("jira_get_project", tool(
                        Map.of("project_key", "string"), Set.of("project_key"), true, false, true, true)),
                Map.entry("jira_get_user", tool(
                        Map.of("user_identifier", "string"), Set.of("user_identifier"), true, false, true, true)),
                Map.entry("confluence_get_page", tool(
                        Map.of("page_id", "string"), Set.of("page_id"), true, false, true, true)),
                Map.entry("confluence_list_spaces", tool(
                        Map.of("limit", "integer"), Set.of(), true, false, true, true)),
                Map.entry("confluence_get_space", tool(
                        Map.of("space_key", "string"), Set.of("space_key"), true, false, true, true)),
                Map.entry("confluence_search", tool(
                        Map.of("cql", "string", "limit", "integer"), Set.of("cql"), true, false, true, true)),
                Map.entry("confluence_create_page", tool(
                        Map.of("space_key", "string", "title", "string", "content", "string", "parent_id", "string"),
                        Set.of("space_key", "title", "content"), false, false, false, true)),
                Map.entry("confluence_update_page", tool(
                        Map.of("page_id", "string", "version", "integer", "title", "string", "content", "string"),
                        Set.of("page_id"), false, true, false, true))
        );
    }

    private static ToolContract tool(Map<String, String> propertyTypes,
                                     Set<String> required,
                                     boolean readOnly,
                                     boolean destructive,
                                     boolean idempotent,
                             boolean openWorld) {
        return new ToolContract(propertyTypes, required, readOnly, destructive, idempotent, openWorld);
    }

    /**
     * Неизменяемое описание схемы и annotations одного публичного MCP-инструмента.
     */
    public static final class ToolContract {

        private final Map<String, String> propertyTypes;
        private final Set<String> required;
        private final boolean readOnly;
        private final boolean destructive;
        private final boolean idempotent;
        private final boolean openWorld;

        private ToolContract(Map<String, String> propertyTypes,
                             Set<String> required,
                             boolean readOnly,
                             boolean destructive,
                             boolean idempotent,
                                     boolean openWorld) {
            this.propertyTypes = Map.copyOf(propertyTypes);
            this.required = Set.copyOf(required);
            this.readOnly = readOnly;
            this.destructive = destructive;
            this.idempotent = idempotent;
            this.openWorld = openWorld;
        }

        public Map<String, String> getPropertyTypes() {
            return propertyTypes;
        }

        public Set<String> getRequired() {
            return required;
        }

        public boolean isReadOnly() {
            return readOnly;
        }

        public boolean isDestructive() {
            return destructive;
        }

        public boolean isIdempotent() {
            return idempotent;
        }

        public boolean isOpenWorld() {
            return openWorld;
        }
    }
}
