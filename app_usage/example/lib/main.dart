import 'package:flutter/material.dart';
import 'package:app_usage/app_usage.dart';
import 'package:intl/intl.dart';

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
  DateTimeRange _dateRange = DateTimeRange(
      start: DateTime.now().subtract(Duration(days: 1)), end: DateTime.now());

  void onFilterPressed() async {
    final dateRange = await showDateRangePicker(
      context: context,
      firstDate: DateTime.now().subtract(Duration(days: 90)),
      lastDate: DateTime.now().endOfDay(),
      initialDateRange: _dateRange,
    );

    if (dateRange == null) return;
    setState(() => _dateRange = dateRange);

    getUsageStats();
  }

  void getUsageStats() async {
    try {
      DateTime endDate = _dateRange.end.endOfDay();
      DateTime startDate = _dateRange.start.startOfDay();

      List<AppUsageInfo> infoList =
          await AppUsage().getUsageMapFromEvents(startDate, endDate);
      setState(() => _infos = infoList);

      for (var info in infoList) {
        print(info.toString());
      }
    } on AppUsageException catch (exception) {
      print(exception);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('App Usage Example'),
        backgroundColor: Colors.green,
        actions: [
          IconButton(
            icon: Icon(Icons.filter_alt),
            onPressed: onFilterPressed,
          )
        ],
      ),
      body: ListView.builder(
          itemCount: _infos.length,
          itemBuilder: (context, index) {
            final app = _infos[index];
            return ListTile(
                title: Text(
                  "${app.appName} - ${app.packageName}",
                ),
                subtitle: Column(
                  children: [
                    Text(
                        "${app.startDate.toShortDateString()} - ${app.endDate.toShortDateString()}"),
                  ],
                ),
                trailing: Text(app.usage.toHoursMinutesSeconds()));
          }),
      floatingActionButton: FloatingActionButton(
          onPressed: getUsageStats, child: Icon(Icons.file_download)),
    );
  }
}

extension DateExt on DateTime {
  String toShortDateString() {
    return DateFormat.yMd().add_jm().format(this);
  }
}
