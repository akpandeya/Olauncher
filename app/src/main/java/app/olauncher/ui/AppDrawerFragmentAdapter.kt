package app.olauncher.ui

import android.util.Log
import android.content.ContentUris
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.view.View
import android.view.animation.AnimationUtils
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.helper.AppActionHandlers
import app.olauncher.helper.HiddenChange
import app.olauncher.helper.NotesStore
import app.olauncher.helper.HomeAppHelper
import app.olauncher.helper.UndoMessage
import app.olauncher.wellbeing.UsageSnapshotWorker
import app.olauncher.helper.DrawerScrollHelper
import app.olauncher.helper.RecentApps
import app.olauncher.helper.Haptics
import app.olauncher.helper.copyToClipboard
import app.olauncher.helper.deletePinnedShortcut
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isSystemAnimationsDisabled
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.showKeyboard
import app.olauncher.helper.profileChoices
import app.olauncher.helper.showToast
import app.olauncher.helper.uninstall

private const val TAG = "AppDrawerFragment"

internal fun AppDrawerFragment.initAdapter() {
    adapter = AppDrawerAdapter(
        flag,
        prefs.appLabelAlignment,
        appClickListener = { appModel ->
            when (appModel) {
                is AppModel.Result -> {
                    when (val action = appModel.action) {
                        is AppModel.Result.Action.Copy -> {
                            requireContext().copyToClipboard(action.value)
                            requireContext().showToast(R.string.copied)
                            findNavController().popBackStack(R.id.mainFragment, false)
                        }

                        is AppModel.Result.Action.Web -> {
                            openWebSearch(action.query)
                            findNavController().popBackStack(R.id.mainFragment, false)
                        }

                        is AppModel.Result.Action.Event -> {
                            try {
                                startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, action.eventId)
                                    )
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "initAdapter failed", e)
                            }
                            findNavController().popBackStack(R.id.mainFragment, false)
                        }

                        is AppModel.Result.Action.Contact -> {
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.lookupUri)))
                            } catch (e: Exception) {
                                Log.w(TAG, "initAdapter failed", e)
                            }
                            findNavController().popBackStack(R.id.mainFragment, false)
                        }

                        is AppModel.Result.Action.SaveWords -> saveWords(action)

                        is AppModel.Result.Action.LookUpWord ->
                            binding.search.setQuery(":w ${action.text}", false)

                        is AppModel.Result.Action.FillQuery ->
                            binding.search.setQuery(action.query, false)

                        is AppModel.Result.Action.StartTimer -> {
                            try {
                                startActivity(
                                    Intent(AlarmClock.ACTION_SET_TIMER).apply {
                                        putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
                                        putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
                                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                                    }
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "start timer failed", e)
                                requireContext().showToast(getString(R.string.timer_no_clock_app))
                            }
                            findNavController().popBackStack(R.id.mainFragment, false)
                        }

                        is AppModel.Result.Action.SwitchProfile -> {
                            prefs.switchProfile(action.profileId)
                            requireContext().showToast(getString(R.string.profile_switched, action.name))
                            (activity as? app.olauncher.MainActivity)?.offerProfilePermissions(action.profileId)
                            viewModel.refreshHome(true)
                            findNavController().popBackStack(R.id.mainFragment, false)
                        }

                        is AppModel.Result.Action.SaveNote -> {
                            NotesStore({ prefs.notesJson }, { prefs.notesJson = it }).add(action.text, System.currentTimeMillis(), action.showFrom)
                            requireContext().showToast(getString(R.string.note_saved))
                            findNavController().popBackStack(R.id.mainFragment, false)
                        }

                        is AppModel.Result.Action.SetAlarm -> {
                            try {
                                startActivity(
                                    Intent(AlarmClock.ACTION_SET_ALARM).apply {
                                        putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                                        putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                                        putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
                                        if (action.days.isNotEmpty()) {
                                            putExtra(AlarmClock.EXTRA_DAYS, ArrayList(action.days))
                                        }
                                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                                    }
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "set alarm failed", e)
                                requireContext().showToast(getString(R.string.alarm_no_clock_app))
                            }
                            findNavController().popBackStack(R.id.mainFragment, false)
                        }

                        is AppModel.Result.Action.AddEvent -> {
                            try {
                                startActivity(
                                    Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
                                        putExtra(CalendarContract.Events.TITLE, action.title)
                                        putExtra(CalendarContract.Events.EVENT_LOCATION, action.place)
                                        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, action.beginMillis)
                                        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, action.endMillis)
                                        putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, action.allDay)
                                    }
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "add event failed", e)
                                requireContext().showToast(getString(R.string.event_no_calendar_app))
                            }
                            findNavController().popBackStack(R.id.mainFragment, false)
                        }

                        is AppModel.Result.Action.Error -> Unit

                        is AppModel.Result.Action.OpenDictionary -> {
                            if (action.openOnTap) {
                                openDictionary(action.text)
                                findNavController().popBackStack(R.id.mainFragment, false)
                            }
                        }

                        is AppModel.Result.Action.Dial -> {
                            try {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.fromParts("tel", action.number, null)
                                }
                                startActivity(intent)
                            } catch (e: Exception) {
                                Log.w(TAG, "initAdapter failed", e)
                            }
                            findNavController().popBackStack(R.id.mainFragment, false)
                        }
                    }
                }

                is AppModel.PrivateSpaceHeader, is AppModel.RecentHeader -> {}
                else -> {
                    if (flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS)
                        Haptics.tick(binding.root)
                    viewModel.selectedApp(appModel, flag, fromDrawer = true)
                    if (flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS)
                        findNavController().popBackStack(R.id.mainFragment, false)
                    else
                        findNavController().popBackStack()
                }
            }
        },
        appInfoListener = {
            openAppInfo(
                requireContext(),
                it.user,
                it.appPackage
            )
            findNavController().popBackStack(R.id.mainFragment, false)
        },
        appDeleteListener = { appModel ->
            when (appModel) {
                is AppModel.Result, is AppModel.PrivateSpaceHeader, is AppModel.RecentHeader -> {}
                is AppModel.PinnedShortcut ->
                    requireContext().deletePinnedShortcut(
                        packageName = appModel.appPackage,
                        shortcutIdToDelete = appModel.shortcutId,
                        user = appModel.user,
                    )

                is AppModel.App ->
                    AppActionHandlers.uninstallApp(requireContext(), appModel.appPackage, appModel.user)
            }
            viewModel.getAppList()
        },
        appHideListener = { appModel, position ->
            if (appModel.isArchived) {
                return@AppDrawerAdapter
            }
            if (appModel is AppModel.PinnedShortcut) {
                requireContext().showToast(R.string.hiding_shortcuts_not_supported)
                return@AppDrawerAdapter
            }
            if (appModel is AppModel.Result || appModel is AppModel.RecentHeader) {
                return@AppDrawerAdapter
            }
            adapter.removeApp(appModel)

            val hiddenKey = appModel.appPackage + "|" + appModel.user.toString()
            val change = HiddenChange(hiddenKey, wasHidden = flag == Constants.FLAG_HIDDEN_APPS)
            val newSet = change.apply(prefs.hiddenApps.toSet()).toMutableSet()

            prefs.hiddenApps = newSet
            UndoMessage.show(
                activity,
                getString(if (change.wasHidden) R.string.app_unhidden else R.string.app_hidden),
            ) {
                prefs.hiddenApps = change.undo(prefs.hiddenApps.toSet()).toMutableSet()
                viewModel.getAppList()
                viewModel.getHiddenApps()
            }
            if (newSet.isEmpty())
                findNavController().popBackStack()
            if (prefs.firstHide) {
                binding.search.hideKeyboard()
                prefs.firstHide = false
                viewModel.showDialog.postValue(Constants.Dialog.HIDDEN)
                findNavController().navigate(R.id.action_appListFragment_to_settingsFragment2)
            }
            viewModel.getAppList()
            viewModel.getHiddenApps()
        },
        appRenameListener = { appModel, renameLabel ->
            if (appModel.isArchived) {
                return@AppDrawerAdapter
            }
            val identifier = when (appModel) {
                is AppModel.PinnedShortcut -> appModel.identity
                is AppModel.App -> appModel.appPackage
                else -> return@AppDrawerAdapter
            }
            prefs.setAppRenameLabel(identifier, renameLabel)
            viewModel.getAppList()
        },
        privateSpaceToggleListener = {
            viewModel.togglePrivateSpaceLock()
        },
        privateSpaceSettingsListener = {
            viewModel.openPrivateSpaceSettings()
            findNavController().popBackStack(R.id.mainFragment, false)
        },
        context = requireContext(),
        bottomSearch = prefs.drawerBottomSearch,
        drawerRecents = prefs.drawerRecents,
        contactSourceProvider = { contactSourceOrNull() },
        calendarRowsProvider = { q -> calendarRows(q) },
        currencyRowsProvider = { q -> currencyRows(q) },
        contactLongClickListener = { result ->
            val action = result.action as? AppModel.Result.Action.Contact
            if (action != null) dialContact(action.lookupUri)
        },
        germanEnabledProvider = { prefs.germanEnabled },
        spellRowsProvider = { items -> spellRows(items) },
        lookUpRowsProvider = { text -> lookUpRows(text) },
        profileChoicesProvider = { context?.let { prefs.profileChoices(it) }.orEmpty() },
        dictionaryLongClickListener = { result ->
            val text = when (val action = result.action) {
                is AppModel.Result.Action.OpenDictionary -> action.text
                is AppModel.Result.Action.SaveWords -> action.items.singleOrNull()
                else -> null
            }
            if (text != null) showDictionaryWebsites(text)
        },
        recentAppsProvider = { RecentApps.decode(prefs.recentApps) },
        hiddenAppsProvider = { prefs.hiddenApps },
        appAddToHomeListener = { appModel ->
            when (HomeAppHelper.addToHome(prefs, appModel)) {
                is HomeAppHelper.AddResult.Added -> {
                    requireContext().showToast(R.string.added_to_home)
                    viewModel.refreshHome(true)
                }
                is HomeAppHelper.AddResult.AlreadyOnHome ->
                    requireContext().showToast(R.string.already_on_home)
                is HomeAppHelper.AddResult.HomeFull ->
                    requireContext().showToast(R.string.home_screen_full)
            }
        },
        appDistractingListener = { appModel ->
            AppActionHandlers.toggleDistracting(requireContext(), prefs, appModel.appPackage)
        },
        distractingAppsProvider = { prefs.distractingApps },
    )

    linearLayoutManager = object : LinearLayoutManager(requireContext()) {
        override fun scrollVerticallyBy(
            dx: Int,
            recycler: Recycler,
            state: RecyclerView.State,
        ): Int {
            val scrollRange = super.scrollVerticallyBy(dx, recycler, state)
            val overScroll = dx - scrollRange
            if (overScroll < -10 && binding.recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING)
                checkMessageAndExit()
            return scrollRange
        }
    }

    binding.recyclerView.layoutManager = linearLayoutManager
    binding.recyclerView.adapter = adapter
    binding.recyclerView.addOnScrollListener(getRecyclerViewOnScrollListener())
    binding.recyclerView.itemAnimator = null
    if (requireContext().isEinkDisplay())
        binding.recyclerView.overScrollMode = View.OVER_SCROLL_NEVER
    else if (requireContext().isSystemAnimationsDisabled().not())
        binding.recyclerView.layoutAnimation =
            AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_from_bottom)
}

internal fun AppDrawerFragment.getRecyclerViewOnScrollListener(): RecyclerView.OnScrollListener {
    return object : RecyclerView.OnScrollListener() {

        var atSearch = false

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            val isAtSearch = DrawerScrollHelper.isAtSearch(
                prefs.drawerBottomSearch,
                canScrollUp = recyclerView.canScrollVertically(-1),
                canScrollDown = recyclerView.canScrollVertically(1)
            )
            val isAtFarEnd = DrawerScrollHelper.isAtFarEnd(
                prefs.drawerBottomSearch,
                canScrollUp = recyclerView.canScrollVertically(-1),
                canScrollDown = recyclerView.canScrollVertically(1)
            )

            when (newState) {
                RecyclerView.SCROLL_STATE_DRAGGING -> {
                    atSearch = isAtSearch
                    if (atSearch)
                        binding.search.hideKeyboard()
                }

                RecyclerView.SCROLL_STATE_IDLE -> {
                    if (isAtFarEnd)
                        binding.search.hideKeyboard()
                    else if (isAtSearch)
                        if (!atSearch && isRemoving.not())
                            binding.search.showKeyboard(prefs.autoShowKeyboard)
                }
            }
        }
    }
}
