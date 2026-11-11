package ru.solomentsev.aqa.testdata;

/** Тестовые данные для сценариев Confluence. */
public final class ConfluenceTestData {

    private static final String PAGE_ID = "1";
    private static final String SEARCH_CQL = "type = page";
    private static final String LIST_SPACES_TOOL = "confluence_list_spaces";

    public static String getSearchCql() { return SEARCH_CQL; }

    public static String getPageId() {
        return PAGE_ID;
    }

    public static String getSearchCql() {
        return SEARCH_CQL;
    }

    public static String getListSpacesTool() {
        return LIST_SPACES_TOOL;
    }

    private ConfluenceTestData() {
    }
}
