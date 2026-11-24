package ch.orellbuehler.jackdaw.ui.addpack

import androidx.annotation.StringRes
import ch.orellbuehler.jackdaw.R
import ch.orellbuehler.jackdaw.format.ImportErrorCode
import ch.orellbuehler.jackdaw.format.ImportWarningCode
import ch.orellbuehler.jackdaw.format.ItemType

@StringRes
fun ImportErrorCode.messageRes(): Int =
    when (this) {
        ImportErrorCode.NOT_JSON -> R.string.error_not_json
        ImportErrorCode.NOT_AN_OBJECT, ImportErrorCode.NOT_A_PACK -> R.string.error_not_a_pack
        ImportErrorCode.VERSION_INVALID -> R.string.error_version_invalid
        ImportErrorCode.VERSION_TOO_NEW -> R.string.error_version_too_new
        ImportErrorCode.ID_INVALID -> R.string.error_id_invalid
        ImportErrorCode.TITLE_INVALID -> R.string.error_title_invalid
        ImportErrorCode.ITEMS_MISSING -> R.string.error_items_missing
        ImportErrorCode.NO_VALID_ITEMS -> R.string.error_no_valid_items
        ImportErrorCode.TOO_LARGE -> R.string.error_too_large
        ImportErrorCode.TOO_MANY_ITEMS -> R.string.error_too_many_items
        ImportErrorCode.REVISION_DOWNGRADE -> R.string.error_downgrade
        ImportErrorCode.UNKNOWN_SAMPLE -> R.string.error_unknown_sample
        ImportErrorCode.STORAGE_ERROR -> R.string.error_storage
        ImportErrorCode.ITEM_INVALID -> R.string.error_item_invalid
        ImportErrorCode.PACK_MISSING -> R.string.error_pack_missing
    }

@StringRes
fun ImportWarningCode.titleRes(): Int =
    when (this) {
        ImportWarningCode.ITEM_SKIPPED -> R.string.warning_item_skipped
        ImportWarningCode.ITEM_DUPLICATE_ID -> R.string.warning_duplicate_id
        ImportWarningCode.ITEM_UNKNOWN_TYPE -> R.string.warning_unknown_type
        ImportWarningCode.ITEM_UNKNOWN_SECTION -> R.string.warning_unknown_section
        ImportWarningCode.PROPERTY_IGNORED -> R.string.warning_property_ignored
        ImportWarningCode.ENTRIES_DROPPED -> R.string.warning_entries_dropped
        ImportWarningCode.SECTION_DROPPED -> R.string.warning_section_dropped
        ImportWarningCode.MORE_WARNINGS -> R.string.warning_more
    }

@StringRes
fun ImportProblem.messageRes(): Int =
    when (this) {
        is ImportProblem.Sdk -> error.code.messageRes()
        ImportProblem.ReadFailed -> R.string.error_read_failed
        ImportProblem.NotUtf8 -> R.string.error_not_utf8
        ImportProblem.Empty -> R.string.error_empty
        ImportProblem.TooLarge -> R.string.error_too_large
        ImportProblem.DownloadFailed -> R.string.error_download_failed
        ImportProblem.InvalidUrl -> R.string.error_invalid_url
    }

fun ImportProblem.technicalDetail(): String? = (this as? ImportProblem.Sdk)?.error?.message

@StringRes
fun ItemType.labelRes(): Int =
    when (this) {
        ItemType.CARD -> R.string.type_card
        ItemType.CHOICE -> R.string.type_choice
        ItemType.TEXT -> R.string.type_text
        ItemType.CLOZE -> R.string.type_cloze
        ItemType.TRUE_FALSE -> R.string.type_truefalse
        ItemType.ORDER -> R.string.type_order
        ItemType.OPEN -> R.string.type_open
    }
