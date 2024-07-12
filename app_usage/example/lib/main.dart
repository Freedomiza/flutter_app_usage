import 'package:flutter/material.dart';
import 'package:app_usage/app_usage.dart';
import 'package:flutter/widgets.dart';
import 'package:intl/intl.dart';
import 'package:to_csv/to_csv.dart' as exportCSV;

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  @override
  void initState() {
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(home: HomePage());
  }
}

extension DurationExt on Duration {
  String toHoursMinutesSeconds() {
    return "${this.inHours}h ${this.inMinutes.remainder(60)}m ${this.inSeconds.remainder(60)}s";
  }
}

extension DateTimeExt on DateTime {
  String toLocalizeString() {
    final date = this;
    return "${date.day}/${date.month}/${date.year} ${date.hour}:${date.minute}:${date.second}";
  }

  DateTime endOfDay() {
    return DateTime(this.year, this.month, this.day, 23, 59, 59, 999, 999);
  }

  DateTime startOfDay() {
    return DateTime(this.year, this.month, this.day, 0, 0, 0, 0, 0);
  }
}

class HomePage extends StatefulWidget {
  const HomePage({Key? key}) : super(key: key);

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  List<AppUsageInfo> _infos = [];
  // List<List<AppUsageInfo>> _groupedFilterInfoList = [];
  List<AppUsageInfo> _timeLineList = [];
  DateTime _dateRange = DateTime.now();

  void onFilterPressed() async {
    final dateRange = await showDatePicker(
      context: context,
      firstDate: DateTime.now().subtract(Duration(days: 90)),
      lastDate: DateTime.now().endOfDay(),
      initialDate: _dateRange,
    );

    if (dateRange == null) return;
    setState(() => _dateRange = dateRange);

    getUsageStats();
  }

  DateTime get startDate =>
      _dateRange.startOfDay().subtract(Duration(hours: 6));
  DateTime get endDate => _dateRange.startOfDay().add(Duration(hours: 9));
  void getUsageStats() async {
    try {
      setState(() {
        _infos = [];
        // _groupedFilterInfoList = [];
        _timeLineList = [];
      });

      List<AppUsageInfo> infoList = [];

      // if (result == 1) {
      //   infoList = await AppUsage().getAppUsage(startDate, endDate);
      // } else {
      //   if (result == 2) {
      //     infoList = await AppUsage().getUsageMapFromEvents(startDate, endDate);
      //   }

      // }

      infoList = await AppUsage().getRawsUsageFromEvents(startDate, endDate);

      setState(() {
        _infos =
            infoList.where((element) => _inFilterList(element)).toList(); // Re;

        _timeLineList = _buildTimelineList();
      });
    } on AppUsageException catch (exception) {
      print(exception);
    }
  }

  List<AppUsageInfo> _buildTimelineList() {
    List<AppUsageInfo> items = [];

    List<AppUsageInfo> checkedItem = [];

    int cursor = 0;
    while (cursor < _infos.length) {
      final current = _infos[cursor];
      if (checkedItem.contains(current)) {
        cursor++;
        continue;
      }
      checkedItem.add(current);
      if (current.event == "ACTIVITY_RESUMED") {
        current.setDuration(Duration.zero);
        int time = 0;
        DateTime resumeDate = current.startDate;
        items.add(current);
        var nextCursor = cursor + 1;
        while (nextCursor < _infos.length) {
          final nextItem = _infos[nextCursor];
          if (nextItem.packageName == current.packageName) {
            checkedItem.add(nextItem);
            if (nextItem.event == "ACTIVITY_PAUSED") {
              time += nextItem.startDate.millisecondsSinceEpoch -
                  resumeDate.millisecondsSinceEpoch;
              resumeDate = nextItem.startDate;
            } else if (nextItem.event == "ACTIVITY_RESUMED") {
              resumeDate = nextItem.startDate;
            } else if (nextItem.event == "ACTIVITY_STOPPED") {
              time += nextItem.startDate.millisecondsSinceEpoch -
                  resumeDate.millisecondsSinceEpoch;
              current.setEndDate(nextItem.startDate);
              break;
            }
          }
          nextCursor++;
        }

        current.setDuration(Duration(milliseconds: time));
      } else {
        if (current.event == "SCREEN_INTERACTIVE") {
          current.setDuration(
            _calculateScreenInteractiveDuration(
              current.startDate,
              _infos,
              cursor,
            ),
          );
          items.add(current);
        } else if (current.event == "SCREEN_NON_INTERACTIVE") {
          current.setDuration(
            _calculateScreenNonInteractiveDuration(
              current.startDate,
              _infos,
              cursor,
            ),
          );
          items.add(current);
        }
      }
      cursor++;
    }
    return items;
  }

  Duration _calculateScreenInteractiveDuration(
      DateTime startDate, List<AppUsageInfo> infoList, int index) {
    int counter = index - 1;
    Duration duration = Duration.zero;

    while (counter >= 0) {
      AppUsageInfo lastAppUsed = infoList[counter];
      if (lastAppUsed.event == 'SCREEN_NON_INTERACTIVE') {
        duration = startDate.difference(lastAppUsed.startDate);
        break;
      } else if (lastAppUsed.event == 'SCREEN_INTERACTIVE') {
        duration = startDate
            .subtract(Duration(milliseconds: 1))
            .difference(lastAppUsed.startDate);
        break;
      }

      counter--;
    }

    return duration;
  }

  Duration _calculateScreenNonInteractiveDuration(
      DateTime startDate, List<AppUsageInfo> infoList, int index) {
    int counter = index - 1;
    Duration duration = Duration.zero;
    while (counter >= 0) {
      AppUsageInfo lastAppUsed = infoList[counter];
      if (lastAppUsed.event == 'SCREEN_NON_INTERACTIVE') {
        duration = startDate.difference(lastAppUsed.startDate);
        break;
      } else if (lastAppUsed.event == 'SCREEN_INTERACTIVE') {
        duration = startDate
            .subtract(Duration(milliseconds: 1))
            .difference(lastAppUsed.startDate);
        break;
      }
      counter--;
    }

    return duration;
  }

  void exportDataToCSV() async {
    try {
      if (_infos.isEmpty) {
        return;
      }

      List<String> header = [];
      header.add('App Name.');
      header.add('Package Name');
      header.add('Start');
      header.add('End');
      header.add('Duration');
      List<List<String>> listOfLists =
          []; //Outter List which contains the data List

      for (var info in _infos) {
        List<String> row = [];
        row.add(info.appName);
        row.add(info.packageName);
        row.add(info.startDate.toShortDateString());
        row.add(info.endDate.toShortDateString());
        row.add(info.usage.toHoursMinutesSeconds());
        listOfLists.add(row);
      }

      // print(csv);
      exportCSV.myCSV(header, listOfLists);
    } on AppUsageException catch (exception) {
      print(exception);
    }
  }

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('App Usage Example'),
          backgroundColor: Colors.blueAccent,
          actions: [
            IconButton(
              icon: Icon(Icons.filter_alt),
              onPressed: onFilterPressed,
            ),
          ],
          bottom: TabBar(tabs: [
            Tab(
              text: "Interactive",
            ),
            Tab(
              text: "Timeline",
            ),
          ]),
        ),
        body: TabBarView(
          children: [
            _buildFirstTab(),
            _buildSecondTab(),
          ],
        ),
        floatingActionButton: FloatingActionButton(
            onPressed: exportDataToCSV, child: Icon(Icons.file_download)),
      ),
    );
  }

  Widget _buildFirstTab() {
    return ListView.builder(
        itemCount: _infos.length,
        itemBuilder: (context, index) {
          final app = _infos[index];
          return _buildListItem(app);
        });
  }

  Widget _buildSecondTab() {
    return ListView.builder(
        itemCount: _timeLineList.length,
        itemBuilder: (context, index) {
          final app = _timeLineList[index];
          return ListTile(
              tileColor: () {
                switch (app.event) {
                  case 'SCREEN_INTERACTIVE':
                    return Colors.green;
                  case 'SCREEN_NON_INTERACTIVE':
                    return Colors.red;
                  case 'KEYGUARD_HIDDEN':
                    return Colors.yellow;
                  case 'KEYGUARD_SHOWN':
                    return Colors.orange;
                  default:
                    return Colors.white;
                }
              }(),
              title: Text(
                "${app.packageName} (${app.appName})",
              ),
              subtitle: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    padding: EdgeInsets.all(5),
                    color: app.isSystemApp ? Colors.red : Colors.green,
                    child: Text(app.isSystemApp ? "S" : "U",
                        style: TextStyle(
                          color: Colors.white,
                        )),
                  ),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                          "${app.startDate.toShortDateString()} - ${app.endDate.toTimeString()}"),
                    ],
                  ),
                ],
              ),
              trailing: app.usage.inSeconds == 0
                  ? Text("N/A")
                  : Text(
                      "${app.event == "SCREEN_INTERACTIVE" ? "Off :" : app.event == "SCREEN_NON_INTERACTIVE" ? "On :" : ""} " +
                          app.usage.toHoursMinutesSeconds()));
        });
  }

  Widget _buildListItem(AppUsageInfo app) {
    return ListTile(
        tileColor: () {
          switch (app.event) {
            case 'SCREEN_INTERACTIVE':
              return Colors.green;
            case 'SCREEN_NON_INTERACTIVE':
              return Colors.red;
            case 'KEYGUARD_HIDDEN':
              return Colors.yellow;
            case 'KEYGUARD_SHOWN':
              return Colors.orange;
            default:
              return Colors.white;
          }
        }(),
        title: Text(
          "${app.packageName} (${app.appName})",
        ),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Container(
                  padding: EdgeInsets.all(5),
                  color: app.isSystemApp ? Colors.red : Colors.green,
                  child: Text(app.isSystemApp ? "System App" : "User App",
                      style: TextStyle(
                        color: Colors.white,
                      )),
                ),
                Text(
                    "${app.startDate.toShortDateString()} ${app.endDate.toTimeString()}"),
              ],
            ),
            Row(
              children: [
                Text("Event:"),
                Text(app.event),
              ],
            ),
          ],
        ),
        trailing: app.usage.inSeconds == 0
            ? null
            : Text(app.usage.toHoursMinutesSeconds()));
  }

  bool _inFilterList(AppUsageInfo element) {
    // return true;
    return [
      'ACTIVITY_RESUMED',
      'ACTIVITY_PAUSED',
      'USER_INTERACTION',
      'ACTIVITY_STOPPED',
      'ACTIVITY_DESTROYED',
      'USER_UNLOCKED',
      'USER_STOPPED',
      'SCREEN_INTERACTIVE',
      'SCREEN_NON_INTERACTIVE',

      // 'NOTIFICATION_INTERRUPTION',
      // 'NOTIFICATION_SEEN',
      // 'FOREGROUND_SERVICE_START',
      // 'FOREGROUND_SERVICE_STOP',
    ].contains(element.event);
  }

  List<List<AppUsageInfo>> _cutFilterListIntoInteractiveSections(
      List<AppUsageInfo> filterInfoList) {
    List<List<AppUsageInfo>> newFilterInfoList = [];

    if (filterInfoList.isEmpty) return newFilterInfoList;
    int index = 0;
    int startIndex = 0;

    while (startIndex < filterInfoList.length) {
      final current = filterInfoList[startIndex];

      if (newFilterInfoList.length <= index) newFilterInfoList.add([]);
      newFilterInfoList[index].add(current);
      startIndex++;
      if (current.event == "SCREEN_NON_INTERACTIVE") {
        index++;
      }
    }

    newFilterInfoList.removeWhere((element) => element.isEmpty);
    return newFilterInfoList;
  }
}

extension DateExt on DateTime {
  String toShortDateString() {
    if (this.millisecondsSinceEpoch == 0) return "";
    return DateFormat.yMd().add_jm().format(this);
  }

  String toTimeString() {
    if (this.millisecondsSinceEpoch == 0) return "";
    return DateFormat.jm().format(this);
  }
}
