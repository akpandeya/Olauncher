package app.olauncher.helper

import app.olauncher.data.AppModel

object DrawerOrdering {

    fun order(apps: List<AppModel>, newFirst: Boolean): List<AppModel> {
        if (apps.isEmpty()) return emptyList()

        val privateSpaceIndex = apps.indexOfFirst { it is AppModel.PrivateSpaceHeader }
        val mainApps = if (privateSpaceIndex != -1) apps.subList(0, privateSpaceIndex) else apps
        val privateSpaceItems = if (privateSpaceIndex != -1) apps.subList(privateSpaceIndex, apps.size) else emptyList()

        if (!newFirst) {
            return apps
        }

        val (newApps, restApps) = mainApps.partition { it.isNew && !it.isArchived }
        if (newApps.isEmpty()) {
            return apps
        }

        val sortedNewApps = newApps.sortedByDescending { it.firstInstallTime }
        return sortedNewApps + restApps + privateSpaceItems
    }
}
