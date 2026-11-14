import json
import statistics
from datetime import datetime, timedelta
from typing import Any, Callable, ContextManager

from sqlalchemy import text


def _row_dict(row: Any) -> dict[str, Any]:
    return dict(row._mapping)


class BuildingSafetyTools:
    """Read-only, parameterised business tools exposed to the Agent Loop."""

    def __init__(self, db_factory: Callable[[], ContextManager[Any]], user_id: int,
                 project_id: int | None, request_context: dict[str, Any] | None = None,
                 data_scope: int = 1):
        self._db_factory = db_factory
        self.user_id = user_id
        self.project_id = project_id
        self.request_context = request_context or {}
        self.data_scope = data_scope
    def _device_scope_clause(self, alias: str = 'd') -> tuple[str, dict[str, Any]]:
        if self.data_scope == 1:
            return '', {}
        return (
            f''' AND EXISTS (
                SELECT 1 FROM asset_device_location scoped_location
                WHERE scoped_location.id={alias}.location_id
                  AND scoped_location.responsible_user_id=:scope_user_id
                  AND scoped_location.is_deleted=0
            )''',
            {'scope_user_id': self.user_id},
        )

    def _alarm_scope_clause(self, alias: str = 'a') -> tuple[str, dict[str, Any]]:
        if self.data_scope == 1:
            return '', {}
        return (
            f''' AND EXISTS (
                SELECT 1 FROM asset_device scoped_device
                INNER JOIN asset_device_location scoped_location
                    ON scoped_location.id=scoped_device.location_id
                   AND scoped_location.is_deleted=0
                   AND scoped_location.responsible_user_id=:scope_user_id
                WHERE scoped_device.id={alias}.device_id
                  AND scoped_device.is_deleted=0
            )''',
            {'scope_user_id': self.user_id},
        )

    def safety_overview(self) -> dict[str, Any]:
        """Return the current device and project alarm overview."""
        started_at = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
        with self._db_factory() as conn:
            device_scope_sql, scope_params = self._device_scope_clause('d')
            devices = _row_dict(conn.execute(text('''
                SELECT COUNT(*) AS total,
                       SUM(CASE WHEN d.online_status=1 THEN 1 ELSE 0 END) AS online,
                       SUM(CASE WHEN d.online_status=0 THEN 1 ELSE 0 END) AS offline
                FROM asset_device d
                WHERE d.is_deleted=0 AND d.enable_status=1''' + device_scope_sql), scope_params).one())
            result: dict[str, Any] = {'deviceSummary': devices}
            if self.project_id is None:
                result['alarmSummary'] = None
                result['projectNotice'] = '当前会话未绑定项目，无法读取项目告警数据。'
                return result
            alarm_scope_sql, alarm_scope_params = self._alarm_scope_clause('a')
            alarm_params = {'project_id': self.project_id, 'started_at': started_at, **alarm_scope_params}
            result['alarmSummary'] = _row_dict(conn.execute(text('''
                SELECT COUNT(*) AS total,
                       SUM(CASE WHEN a.alarm_level=1 THEN 1 ELSE 0 END) AS warning,
                       SUM(CASE WHEN a.alarm_level=2 THEN 1 ELSE 0 END) AS alarm,
                       SUM(CASE WHEN a.alarm_level=3 THEN 1 ELSE 0 END) AS control,
                       SUM(CASE WHEN a.status=0 THEN 1 ELSE 0 END) AS pending
                FROM biz_alarm_record a
                WHERE a.project_id=:project_id AND a.is_deleted=0 AND a.alarm_time>=:started_at''' + alarm_scope_sql),
                alarm_params).one())
            result['recentAlarms'] = [_row_dict(row) for row in conn.execute(text('''
                SELECT a.alarm_no, a.alarm_level, a.alarm_type_code, a.alarm_content,
                       a.device_id, a.alarm_time, a.status
                FROM biz_alarm_record a
                WHERE a.project_id=:project_id AND a.is_deleted=0''' + alarm_scope_sql + '''
                ORDER BY a.alarm_time DESC, a.id DESC LIMIT 5'''),
                alarm_params).all()]
            return result

    def alarm_analysis(self, days: int = 1, device_keyword: str | None = None) -> dict[str, Any]:
        """Return a bounded alarm distribution for the currently authorised project."""
        if self.project_id is None:
            return {'records': [], 'notice': '当前会话未绑定项目，无法分析项目告警。'}
        days = max(1, min(int(days), 30))
        params: dict[str, Any] = {
            'project_id': self.project_id,
            'started_at': datetime.now() - timedelta(days=days),
        }
        alarm_scope_sql, alarm_scope_params = self._alarm_scope_clause('a')
        params.update(alarm_scope_params)
        device_filter = ''
        if device_keyword:
            params['device_keyword'] = '%' + device_keyword.strip() + '%'
            device_filter = ' AND (d.device_name LIKE :device_keyword OR d.device_code LIKE :device_keyword)'
        with self._db_factory() as conn:
            summary = _row_dict(conn.execute(text('''
                SELECT COUNT(*) AS total,
                       SUM(CASE WHEN a.alarm_level=1 THEN 1 ELSE 0 END) AS warning,
                       SUM(CASE WHEN a.alarm_level=2 THEN 1 ELSE 0 END) AS alarm,
                       SUM(CASE WHEN a.alarm_level=3 THEN 1 ELSE 0 END) AS control
                FROM biz_alarm_record a
                LEFT JOIN asset_device d ON d.id=a.device_id AND d.is_deleted=0
                WHERE a.project_id=:project_id AND a.is_deleted=0 AND a.alarm_time>=:started_at''' + device_filter + alarm_scope_sql), params).one())
            records = [_row_dict(row) for row in conn.execute(text('''
                SELECT a.alarm_type_code, a.alarm_level, COUNT(*) AS count,
                       MAX(a.alarm_time) AS latest_alarm_time
                FROM biz_alarm_record a
                LEFT JOIN asset_device d ON d.id=a.device_id AND d.is_deleted=0
                WHERE a.project_id=:project_id AND a.is_deleted=0 AND a.alarm_time>=:started_at''' + device_filter + alarm_scope_sql + '''
                GROUP BY a.alarm_type_code, a.alarm_level
                ORDER BY count DESC, a.alarm_level DESC LIMIT 10'''), params).all()]
            trend = self._alarm_trends(conn, params, device_filter + alarm_scope_sql)
            return {'days': days, 'summary': summary, 'distribution': records, **trend}
    @staticmethod
    def _alarm_trends(conn: Any, params: dict[str, Any], extra_sql: str) -> dict[str, Any]:
        rows = conn.execute(text('''
            SELECT a.alarm_time
            FROM biz_alarm_record a
            LEFT JOIN asset_device d ON d.id=a.device_id AND d.is_deleted=0
            WHERE a.project_id=:project_id AND a.is_deleted=0 AND a.alarm_time>=:started_at''' + extra_sql + '''
            ORDER BY a.alarm_time ASC LIMIT 2000'''), params).all()
        hourly: dict[int, int] = {}
        daily: dict[str, int] = {}
        for row in rows:
            alarm_time = row._mapping.get('alarm_time')
            if alarm_time is None:
                continue
            if isinstance(alarm_time, str):
                alarm_time = datetime.fromisoformat(alarm_time[:19])
            hourly[alarm_time.hour] = hourly.get(alarm_time.hour, 0) + 1
            day = alarm_time.strftime('%Y-%m-%d')
            daily[day] = daily.get(day, 0) + 1
        return {
            'hourlyDistribution': [{'hour': hour, 'count': count} for hour, count in sorted(hourly.items())],
            'dailyTrend': [{'date': date, 'count': count} for date, count in sorted(daily.items())],
            'peakHour': max(hourly, key=hourly.get) if hourly else None,
        }

    def device_health(self, device_id: int | None = None, device_keyword: str | None = None,
                      days: int = 7) -> dict[str, Any]:
        """Return a device's ledger state, recent alarms and latest realtime data."""
        days = max(1, min(int(days), 30))
        selected_id = device_id or self.request_context.get('deviceId')
        with self._db_factory() as conn:
            device_scope_sql, device_scope_params = self._device_scope_clause('d')
            if selected_id is not None:
                device = conn.execute(text('''
                    SELECT d.id, d.device_code, d.device_name, d.brand, d.model, d.online_status,
                           d.enable_status, d.last_online_time, d.location_id
                    FROM asset_device d WHERE d.id=:device_id AND d.is_deleted=0
                      AND d.enable_status=1''' + device_scope_sql),
                    {'device_id': selected_id, **device_scope_params}).first()
            elif device_keyword:
                device = conn.execute(text('''
                    SELECT d.id, d.device_code, d.device_name, d.brand, d.model, d.online_status,
                           d.enable_status, d.last_online_time, d.location_id
                    FROM asset_device d
                    WHERE d.is_deleted=0 AND d.enable_status=1
                      AND (d.device_name LIKE :keyword OR d.device_code LIKE :keyword)''' + device_scope_sql + '''
                    ORDER BY d.id LIMIT 1'''),
                    {'keyword': '%' + device_keyword.strip() + '%', **device_scope_params}).first()
            else:
                return {'device': None, 'notice': '请提供设备编号、设备名称，或前端 context.deviceId。'}
            if device is None:
                return {'device': None, 'notice': '未找到该设备，请确认设备名称或编码。'}
            data = _row_dict(device)
            params = {'device_id': data['id'], 'started_at': datetime.now() - timedelta(days=days)}
            alarm_scope_sql, alarm_scope_params = self._alarm_scope_clause('a')
            params.update(alarm_scope_params)
            data['recentAlarmSummary'] = _row_dict(conn.execute(text('''
                SELECT COUNT(*) AS total,
                       MAX(a.alarm_level) AS max_level,
                       SUM(CASE WHEN a.alarm_level=1 THEN 1 ELSE 0 END) AS warning,
                       SUM(CASE WHEN a.alarm_level=2 THEN 1 ELSE 0 END) AS alarm,
                       SUM(CASE WHEN a.alarm_level=3 THEN 1 ELSE 0 END) AS control
                FROM biz_alarm_record a
                WHERE a.device_id=:device_id AND a.is_deleted=0 AND a.alarm_time>=:started_at''' + alarm_scope_sql), params).one())
            data['towerCraneLatest'] = [_row_dict(row) for row in conn.execute(text('''
                SELECT device_id, collect_time, load_weight, amplitude, moment,
                       moment_percent, wind_speed, height, slew_angle, alarm_status
                FROM biz_tower_crane_realtime
                WHERE device_id=:device_code
                ORDER BY collect_time DESC, id DESC LIMIT 1'''), {'device_code': data['device_code']}).all()]
            data['hoistLatest'] = [_row_dict(row) for row in conn.execute(text('''
                SELECT device_id, collect_time, load_weight, load_percent, height,
                       run_direction, front_door_status, rear_door_status,
                       passenger_count, wind_speed, interlock_alarm
                FROM biz_hoist_realtime
                WHERE device_id=:device_code
                ORDER BY collect_time DESC, id DESC LIMIT 1'''), {'device_code': data['device_code']}).all()]
            data['healthLevel'], data['healthMetrics'] = self._health_metrics(conn, data['device_code'], days, data['recentAlarmSummary'])
            return {'days': days, 'device': data}

    @staticmethod
    def _series_metrics(rows: list[dict[str, Any]], value_key: str) -> dict[str, Any]:
        values = [float(row[value_key]) for row in rows if row.get(value_key) is not None]
        if not values:
            return {'sampleCount': 0, 'peak': None, 'average': None, 'range': None}
        return {
            'sampleCount': len(values),
            'peak': max(values),
            'average': round(statistics.fmean(values), 3),
            'range': round(max(values) - min(values), 3),
        }

    def _health_metrics(self, conn: Any, device_code: str, days: int,
                        alarm_summary: dict[str, Any]) -> tuple[str, dict[str, Any]]:
        started_at = datetime.now() - timedelta(days=days)
        tower_rows = [_row_dict(row) for row in conn.execute(text('''
            SELECT moment_percent, wind_speed, alarm_status
            FROM biz_tower_crane_realtime
            WHERE device_id=:device_code AND collect_time>=:started_at
            ORDER BY collect_time DESC LIMIT 2000'''), {'device_code': device_code, 'started_at': started_at}).all()]
        hoist_rows = [_row_dict(row) for row in conn.execute(text('''
            SELECT load_percent, wind_speed, interlock_alarm
            FROM biz_hoist_realtime
            WHERE device_id=:device_code AND collect_time>=:started_at
            ORDER BY collect_time DESC LIMIT 2000'''), {'device_code': device_code, 'started_at': started_at}).all()]
        metrics: dict[str, Any] = {}
        if tower_rows:
            metrics['tower'] = {
                'momentPercent': self._series_metrics(tower_rows, 'moment_percent'),
                'windSpeed': self._series_metrics(tower_rows, 'wind_speed'),
                'realtimeAlarmCount': sum(1 for row in tower_rows if (row.get('alarm_status') or 0) > 0),
            }
        if hoist_rows:
            metrics['hoist'] = {
                'loadPercent': self._series_metrics(hoist_rows, 'load_percent'),
                'windSpeed': self._series_metrics(hoist_rows, 'wind_speed'),
                'interlockAlarmCount': sum(1 for row in hoist_rows if (row.get('interlock_alarm') or 0) > 0),
            }
        max_level = int(alarm_summary.get('max_level') or 0)
        realtime_alarm = any(
            (value.get('realtimeAlarmCount') or value.get('interlockAlarmCount') or 0) > 0
            for value in metrics.values()
        )
        volatile = any(
            (value.get('momentPercent', {}).get('range') or 0) > 20
            or (value.get('loadPercent', {}).get('range') or 0) > 20
            for value in metrics.values()
        )
        if max_level >= 3 or realtime_alarm:
            level = '严重'
        elif max_level >= 2:
            level = '异常'
        elif max_level == 1 or volatile:
            level = '关注'
        else:
            level = '健康'
        return level, metrics
    def environment_latest(self) -> dict[str, Any]:
        """Return the latest reading for every monitored metric and point."""
        if self.data_scope != 1 and not self.request_context.get('monitorPointIds'):
            return {'records': [], 'notice': '当前数据权限未提供可查询的环境监测点范围。'}
        point_ids = self.request_context.get('monitorPointIds') or []
        params: dict[str, Any] = {}
        point_filter = ''
        if self.data_scope != 1:
            placeholders = ','.join(f':point_{index}' for index, _ in enumerate(point_ids))
            point_filter = f' AND current_data.parent_id IN ({placeholders})'
            params.update({f'point_{index}': value for index, value in enumerate(point_ids)})
        with self._db_factory() as conn:
            records = [_row_dict(row) for row in conn.execute(text('''
                SELECT metric_type, metric_value, unit, status, collect_time, parent_id
                FROM biz_env_monitor_data current_data
                WHERE NOT EXISTS (
                    SELECT 1 FROM biz_env_monitor_data newer
                    WHERE newer.parent_id=current_data.parent_id
                      AND newer.metric_type=current_data.metric_type
                      AND (newer.collect_time > current_data.collect_time
                           OR (newer.collect_time=current_data.collect_time AND newer.id > current_data.id)))
                ''' + point_filter + ''' ORDER BY parent_id, metric_type LIMIT 100'''), params).all()]
            return {'records': records}

    @staticmethod
    def stringify(value: dict[str, Any]) -> str:
        return json.dumps(value, ensure_ascii=False, default=str, separators=(',', ':'))
