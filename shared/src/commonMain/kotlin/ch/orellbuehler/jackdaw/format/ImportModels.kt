package ch.orellbuehler.jackdaw.format

enum class ItemType(
    val code: String,
) {
    CARD("card"),
    CHOICE("choice"),
    TEXT("text"),
    CLOZE("cloze"),
    TRUE_FALSE("truefalse"),
    ORDER("order"),
    OPEN("open"),
    ;

    companion object {
        internal fun fromCode(code: String): ItemType? = entries.firstOrNull { it.code == code }
    }
}

enum class ImportErrorCode {
    NOT_JSON,
    NOT_AN_OBJECT,
    NOT_A_PACK,
    VERSION_INVALID,
    VERSION_TOO_NEW,
    ID_INVALID,
    TITLE_INVALID,
    ITEMS_MISSING,
    NO_VALID_ITEMS,
    TOO_LARGE,
    TOO_MANY_ITEMS,
    REVISION_DOWNGRADE,
    UNKNOWN_SAMPLE,
    STORAGE_ERROR,
    ITEM_INVALID,
    PACK_MISSING,
}

data class ImportError(
    val code: ImportErrorCode,
    val message: String,
)

enum class ImportWarningCode {
    ITEM_SKIPPED,
    ITEM_DUPLICATE_ID,
    ITEM_UNKNOWN_TYPE,
    ITEM_UNKNOWN_SECTION,
    PROPERTY_IGNORED,
    ENTRIES_DROPPED,
    SECTION_DROPPED,
    MORE_WARNINGS,
}

data class ImportWarning(
    val code: ImportWarningCode,
    val message: String,
    val itemId: String?,
    val itemIndex: Int,
    val detail: String?,
    val count: Int,
)
