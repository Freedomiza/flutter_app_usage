package dk.cachet.app_usage

import android.annotation.TargetApi
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


class Stats {

    companion object {
        private val TAG = Stats::class.java.simpleName

        /** Check if permission for usage statistics is required,
         * by fetching usage statistics since the beginning of time
         */
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @SuppressWarnings("ResourceType")
        fun checkIfStatsAreAvailable(context: Context): Boolean {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = Calendar.getInstance().timeInMillis

            // Check if any usage stats are available from the beginning of time until now
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, 0, now)

            // Return whether or not stats are available
            return stats.isNotEmpty()
        }

        /** Produces a map for each installed app package name,
         *  with the corresponding time in foreground in seconds for that application.
         */
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @SuppressWarnings("ResourceType")
        fun getUsageMap(context: Context, start: Long, end: Long): HashMap<String, List<Double>> {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val usageStatsMap = manager.queryAndAggregateUsageStats(start, end)
            val usageMap = HashMap<String, List<Double>>()

            for (packageName in usageStatsMap.keys) {
                val us = usageStatsMap[packageName]
                try {
                    val timeMs = us?.totalTimeInForeground ?: 0
                    val timeSeconds = (timeMs / 1000).toDouble()
                    val timeMsFirst = us?.firstTimeStamp ?: 0
                    val timeSecondsStart = (timeMsFirst / 1000).toDouble()
                    val timeMsStop = us?.lastTimeStamp ?: 0
                    val timeSecondsStop = (timeMsStop / 1000).toDouble()

                    var timeSecondsLastUse = 0.0
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val timeMsLastUse = us?.lastTimeForegroundServiceUsed ?: 0
                        timeSecondsLastUse = (timeMsLastUse / 1000).toDouble()
                    }

                    val listT =
                        listOf(timeSeconds, timeSecondsStart, timeSecondsStop, timeSecondsLastUse)
                    usageMap[packageName] = listT
                } catch (e: Exception) {
                    Log.d(TAG, "Getting timeInForeground resulted in an exception")
                }
            }
            return usageMap
        }


        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @SuppressWarnings("ResourceType")
        fun getQueryUsageDaily(
            context: Context,
            start: Long,
            end: Long
        ): HashMap<String, List<Double>> {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val usageStats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)

            val usageMap = HashMap<String, List<Double>>()

            for (us in usageStats) {


                try {
                    Log.d(
                        TAG, "Pkg: " + us.getPackageName() + "\t" + "ForegroundTime: "
                                + us.getTotalTimeInForeground() + "\t" + "LastTimeUsed: "
                                + us.getLastTimeUsed()
                    );

                    val packageName = us.getPackageName()
                    val timeMs = us?.totalTimeInForeground ?: 0
                    val timeSeconds = (timeMs / 1000).toDouble()
                    val timeMsFirst = us?.firstTimeStamp ?: 0
                    val timeSecondsStart = (timeMsFirst / 1000).toDouble()
                    val timeMsStop = us?.lastTimeStamp ?: 0
                    val timeSecondsStop = (timeMsStop / 1000).toDouble()

                    var timeSecondsLastUse = 0.0
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val timeMsLastUse = us?.lastTimeForegroundServiceUsed ?: 0
                        timeSecondsLastUse = (timeMsLastUse / 1000).toDouble()
                    }

                    val listT =
                        listOf(timeSeconds, timeSecondsStart, timeSecondsStop, timeSecondsLastUse)
                    usageMap[packageName] = listT
                } catch (e: Exception) {
                    Log.d(TAG, "Getting timeInForeground resulted in an exception")
                }

            }

            return usageMap
        }

        fun getAppName(packageName: String, packageManager: PackageManager): String {

            val appName: String = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(
                        packageName,
                        PackageManager.GET_META_DATA
                    )
                ) as String
            } catch (nameNotFound: PackageManager.NameNotFoundException) {
                packageName
            }
            return appName

        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        private fun isSystemApp(event: UsageEvents.Event, packageManager: PackageManager): Boolean {
            val packageName = event.packageName ?: return false
            return try {
                val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
                (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @SuppressWarnings("ResourceType")
        fun getUsageMapFromEvents(
            context: Context,
            start: Long,
            end: Long
        ): ArrayList<UserRecord> {
            val stateMap: ArrayList<UserRecord> = ArrayList<UserRecord>()
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val packageManager = context.packageManager

            val eventList = usageStatsManager.queryEvents(start, end)



            while (eventList.hasNextEvent()) {
                val event = UsageEvents.Event()
                eventList.getNextEvent(event)
                val appName = getAppName(event.packageName, packageManager)
                val packageName = event.packageName
                // Ignore system app
                if (isSystemApp(event, packageManager) && event.eventType!= UsageEvents.Event.SCREEN_INTERACTIVE &&
                    event.eventType!= UsageEvents.Event.SCREEN_NON_INTERACTIVE ) {

                    continue
                }

                val lastSession = stateMap.lastOrNull()

                when (event.eventType) {
                    UsageEvents.Event.SCREEN_INTERACTIVE -> {
                        if(lastSession == null) {
                            stateMap.add(
                                UserRecord(
                                    packageName,
                                    SCREEN_ON_EVENT,
                                    event.timeStamp,
                                    0,
                                    event.eventType
                                )
                            )
                        } else {
                            if(lastSession.appName == SCREEN_OFF_EVENT) {
                                lastSession.endTime = event.timeStamp - 1000
                            } else {
                                if(lastSession.appName != SCREEN_ON_EVENT) {
                                    lastSession.endTime = event.timeStamp - 1000
                                }
                            }

                            stateMap.add(
                                UserRecord(
                                    event.packageName,
                                    SCREEN_ON_EVENT,
                                    event.timeStamp,
                                    0,
                                    event.eventType,
                                )
                            )
                        }
                    }

                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                        if(lastSession == null) {
                            stateMap.add(
                                UserRecord(
                                    packageName,
                                    SCREEN_OFF_EVENT,
                                    event.timeStamp,
                                    0,
                                    event.eventType
                                )
                            )
                        } else {

                            if (lastSession.appName != SCREEN_ON_EVENT && lastSession.endTime == 0L) {
                                lastSession.endTime = event.timeStamp - 1000
                            }

                            stateMap.add(
                                UserRecord(
                                    event.packageName,
                                    SCREEN_OFF_EVENT,
                                    event.timeStamp,
                                    0,
                                    event.eventType,
                                )
                            )
                        }

                    }

                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        if(lastSession == null) {
                            stateMap.add(
                                UserRecord(
                                    packageName,
                                    appName,
                                    event.timeStamp,
                                    0,
                                    event.eventType
                                )
                            )
                        } else {
                            if (lastSession.packageName == event.packageName) {


                            } else {
                                if (lastSession.endTime == 0L && lastSession.appName != SCREEN_ON_EVENT && lastSession.appName != SCREEN_OFF_EVENT) {
                                    lastSession.endTime = event.timeStamp - 1000
                                }

                                stateMap.add(
                                    UserRecord(
                                        packageName,
                                        appName,
                                        event.timeStamp,
                                        0,
                                        event.eventType
                                    )
                                )
                            }

                        }
                    }

//                    UsageEvents.Event.ACTIVITY_PAUSED -> {
//                        if(lastSession == null) {
//                            stateMap.add(
//                                UserRecord(
//                                    packageName,
//                                    appName,
//                                    start,
//                                    event.timeStamp,
//                                    event.eventType
//                                )
//                            )
//                        } else {
//                            if (lastSession.packageName == event.packageName && lastSession.endTime == 0L) {
//                                lastSession.endTime = event.timeStamp
//                                lastSession.lastEvent = event.eventType
//                            }
//                        }
//                    }
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        if(lastSession == null) {
                            stateMap.add(
                                UserRecord(
                                    packageName,
                                    appName,
                                    start,
                                    event.timeStamp,
                                    event.eventType
                                )
                            )
                        } else {
                            if (lastSession.packageName == event.packageName) {
                                // && lastSession.endTime == 0L
//                                if(lastSession.lastEvent != UsageEvents.Event.ACTIVITY_RESUMED) {
                                    lastSession.endTime = event.timeStamp
                                    lastSession.lastEvent = event.eventType
                                    continue
//                                }
                            } else {
                                Log.d(
                                    TAG,
                                    "Event stopped, prev session: ${lastSession.packageName}, start: ${lastSession.startTime}"
                                )

                                if(!findAndUpdateLastSessionApp(stateMap, UserRecord(
                                    packageName,
                                    appName,
                                    0,
                                    event.timeStamp,
                                    event.eventType
                                ))) {

                                    stateMap.add(
                                        UserRecord(
                                            packageName,
                                            appName,
                                            0,
                                            event.timeStamp,
                                            event.eventType
                                        )
                                    )
                                }
                            }
                        }
                    }

                }




//                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
//                    val packageCheck = stateMap[packageName]
//
//                    if (packageCheck != null) {
//                        packageCheck.add(
//                            UserRecord(packageName, appName, event.timeStamp, 0)
//                        )
//                    } else {
//                        stateMap[packageName] = arrayListOf()
//                        stateMap[packageName]!!.add(
//                            UserRecord(
//                                packageName,
//                                appName,
//                                event.timeStamp,
//                                0
//                            )
//                        )
//                    }
//                } else if (event.eventType == UsageEvents.Event.ACTIVITY_STOPPED) {
//                    val packageCheck = stateMap[event.packageName]
//
//                    if (packageCheck != null) {
//                        val lastSession = packageCheck.lastOrNull()
//                        // Activity already start after filter start date
//                        if (lastSession == null) {
//                            // Assign start filter as start time for this first event
//                            packageCheck.add(
//                                UserRecord(
//                                    packageName,
//                                    appName,
//                                    start,
//                                    event.timeStamp
//                                )
//                            )
//                        } else {
//                            if (lastSession.endTime == 0L) {
//                                // close last resume sessions
//                                lastSession.endTime = event.timeStamp
//                            } else {
//                                // This case should not occur when there a session without endTime
//                                // but in case it happened just record it without startTime
//                                packageCheck.add(
//                                    UserRecord(
//                                        packageName,
//                                        appName,
//                                        event.timeStamp - 1,
//                                        event.timeStamp
//                                    )
//                                )
//                            }
//                        }
//
//                    } else {
//                        // event record of prev session filter
//                        stateMap[event.packageName] = arrayListOf()
//                        stateMap[event.packageName]!!.add(
//                            UserRecord(
//                                packageName,
//                                appName,
//                                start,
//                                event.timeStamp
//                            )
//                        )
//
//                    }
//                } else {
//                    Log.d(
//                        TAG,
//                        "Event: ${event.eventType}  ${event.packageName}  ${event.className}  ${event.timeStamp}"
//                    )
//                }
            }

//            for (key in stateMap.keys) {
//                val state = stateMap[key]
//                if (state != null) {
//                    val lastSession = state.lastOrNull()
//                    if (lastSession != null) {
//                        if (lastSession.startTime != 0L && lastSession.endTime == 0L) {
//                            lastSession.endTime = getLastFilterTime(end)
//                        }
//                    }
//                }
//            }

            return stateMap
        }
        private fun getLastFilterTime(time : Long) : Long {
            val currentTime = Calendar.getInstance().time.time
            if(time < currentTime) {
                return  time
            }
            return  currentTime
        }

        private fun findAndUpdateLastSessionApp(list: ArrayList<UserRecord> , record: UserRecord): Boolean {
            var found = false
            for (i in list.lastIndex downTo 0) {

                val session = list[i]
                println(session)
                if (session.packageName == record.packageName) {
                    if(session.endTime == 0L) {
                        session.endTime = record.endTime
                        found = true
                        break
                    }
                    break
                }
            }

            return found
        }
    }
}


val SCREEN_ON_EVENT: String = "SCREEN_ON"
val SCREEN_OFF_EVENT: String = "SCREEN_OFF"




class UserRecord(var packageName: String, var appName: String, var startTime: Long, var endTime: Long, var lastEvent: Int) {
    val duration: Long
        get()  {
            if(startTime == 0L || endTime == 0L) return 0L
            var dur = endTime - startTime
            if( dur < 0L )  dur = 0L
            return dur
        }

    fun toHashMap(): HashMap<String, Any> {
        val map = HashMap<String, Any>()
        map["packageName"] = packageName
        map["appName"] = appName
        map["startTime"] = startTime / 1000
        map["endTime"] = endTime / 1000
        map["duration"] = duration / 1000
        map["event"] = lastEvent

        return map
    }

}
