package ch.orellbuehler.jackdaw.store

import ch.orellbuehler.jackdaw.db.JackdawDatabase

internal class LibraryStore(
    private val db: JackdawDatabase,
    private val env: Env,
) {
    private val library get() = db.libraryQueries

    fun setArchived(
        packId: String,
        archived: Boolean,
    ) = change(packId) { library.setArchived(archived.toLong(), packId) }

    fun setUrl(
        packId: String,
        url: String?,
    ) = change(packId) { library.setUrl(url, packId) }

    fun setEdited(
        packId: String,
        edited: Boolean,
    ) = change(packId) { library.setEdited(edited.toLong(), packId) }

    fun isEdited(packId: String): Boolean = library.selectLocal(packId).executeAsOneOrNull()?.edited == 1L

    fun groups(): List<PackGroup> {
        val members = library.selectMembers().executeAsList().groupBy({ it.group_id }, { it.pack_id })
        return library.selectGroups().executeAsList().map { PackGroup(it.id, it.name, members[it.id].orEmpty()) }
    }

    fun createGroup(name: String): String? {
        val clean = name.trim().take(MAX_NAME)
        if (clean.isEmpty()) return null
        val id = env.newId()
        db.transaction { library.insertGroup(id, clean, library.nextGroupPosition().executeAsOne()) }
        env.changed()
        return id
    }

    fun renameGroup(
        groupId: String,
        name: String,
    ) {
        val clean = name.trim().take(MAX_NAME)
        if (clean.isEmpty()) return
        library.renameGroup(clean, groupId)
        env.changed()
    }

    fun deleteGroup(groupId: String) {
        db.transaction {
            library.deleteGroupMembers(groupId)
            library.deleteGroup(groupId)
        }
        env.changed()
    }

    fun setMember(
        groupId: String,
        packId: String,
        member: Boolean,
    ) {
        if (member) library.insertMember(packId, groupId) else library.deleteMember(groupId, packId)
        env.changed()
    }

    private fun change(
        packId: String,
        update: () -> Unit,
    ) {
        db.transaction {
            if (db.packQueries.selectPack(packId).executeAsOneOrNull() == null) return@transaction
            library.insertLocalIfMissing(packId)
            update()
        }
        env.changed()
    }

    private companion object {
        const val MAX_NAME = 60
    }
}
