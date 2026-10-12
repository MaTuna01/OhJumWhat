#!/usr/bin/env python3
"""측정 산출물을 마크다운 표로 만든다(표준 라이브러리만).

    python3 loadtest/report.py loadtest/out/steady-300 loadtest/out/steady-2026-10-10T01-00-00.csv

첫 인자는 collect.sh의 폴더(prom-*.txt 스냅샷), 둘째(선택)는 k6 summary CSV. 출력:
  1) k6 요청 이름별 횟수·p50/p95/p99
  2) actuator 첫·마지막 스냅샷 차분으로 uri·status별 요청 수·초당 요청·평균 응답(ms)
  3) 스냅샷 전체에서 본 최대값: Hikari active/pending, 힙, Tomcat busy, 채팅 연결, GC pause 합
"""
import glob
import os
import re
import sys

# 라벨 값 안에 }가 있을 수 있다(uri="/api/orgs/{orgId}")
LINE = re.compile(r'^([a-zA-Z_:][a-zA-Z0-9_:]*)(\{(?:[^}"]|"[^"]*")*\})?\s+([-+0-9.eENaIninf]+)')


def parse(path):
    metrics = {}
    with open(path, encoding='utf-8') as f:
        for line in f:
            if line.startswith('#'):
                continue
            m = LINE.match(line)
            if not m:
                continue
            name, labels, value = m.group(1), m.group(2) or '', float(m.group(3))
            metrics[(name, labels)] = value
    return metrics


def label(labels, key):
    m = re.search(key + r'="([^"]*)"', labels)
    return m.group(1) if m else ''


def epoch(path):
    return int(re.search(r'prom-(\d+)\.txt$', path).group(1))


def requests_table(first, last, seconds):
    rows = []
    for (name, labels), count in last.items():
        if name != 'http_server_requests_seconds_count':
            continue
        before = first.get((name, labels), 0.0)
        delta = count - before
        if delta <= 0:
            continue
        total = last.get(('http_server_requests_seconds_sum', labels), 0.0) - first.get(('http_server_requests_seconds_sum', labels), 0.0)
        rows.append((label(labels, 'method') + ' ' + label(labels, 'uri'), label(labels, 'status'), delta, delta / seconds, 1000 * total / delta))
    rows.sort(key=lambda r: -r[2])
    out = ['| 요청 | 상태 | 횟수 | 초당 | 평균 ms |', '|---|---|---:|---:|---:|']
    for r in rows:
        out.append(f'| `{r[0]}` | {r[1]} | {int(r[2])} | {r[3]:.1f} | {r[4]:.1f} |')
    return '\n'.join(out)


def peaks(snapshots):
    want = {
        'hikaricp_connections_active': 'Hikari active',
        'hikaricp_connections_pending': 'Hikari pending',
        'hikaricp_connections_timeout_total': 'Hikari timeout(누적)',
        'tomcat_threads_busy_threads': 'Tomcat busy',
        'ohjumwhat_chat_connections': '채팅 연결',
        'jvm_threads_live_threads': 'JVM 스레드',
    }
    best = {}
    heap = 0.0
    gc = 0.0
    for snap in snapshots:
        h = sum(v for (n, l), v in snap.items() if n == 'jvm_memory_used_bytes' and 'area="heap"' in l)
        heap = max(heap, h)
        g = sum(v for (n, l), v in snap.items() if n == 'jvm_gc_pause_seconds_sum')
        gc = max(gc, g)
        for (n, l), v in snap.items():
            if n in want:
                best[n] = max(best.get(n, 0.0), v)
    out = ['| 지표 | 최대 |', '|---|---:|']
    for n, title in want.items():
        if n in best:
            out.append(f'| {title} | {best[n]:.0f} |')
    out.append(f'| 힙 사용 | {heap / 1024 / 1024:.0f} MB |')
    out.append(f'| GC pause 합(누적) | {gc:.2f} s |')
    return '\n'.join(out)


def k6_table(csv_path):
    out = ['| 요청 이름 | 횟수 | 평균 ms | p50 | p95 | p99 | 최대 |', '|---|---:|---:|---:|---:|---:|---:|']
    with open(csv_path, encoding='utf-8') as f:
        next(f)
        for line in f:
            cells = line.rstrip('\n').split(',')
            if len(cells) < 7:
                continue
            out.append('| `' + cells[0] + '` | ' + ' | '.join(cells[1:7]) + ' |')
    return '\n'.join(out)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    folder = sys.argv[1]
    files = sorted(glob.glob(os.path.join(folder, 'prom-*.txt')), key=epoch)
    if len(sys.argv) > 2:
        print('### k6 요청별 응답 시간\n')
        print(k6_table(sys.argv[2]))
        print()
    if len(files) < 2:
        print(f'스냅샷이 {len(files)}개뿐입니다: {folder}')
        return
    snapshots = [parse(p) for p in files]
    seconds = max(1, epoch(files[-1]) - epoch(files[0]))
    print(f'### 서버 지표 ({seconds}초, 스냅샷 {len(files)}개)\n')
    print(requests_table(snapshots[0], snapshots[-1], seconds))
    print()
    print(peaks(snapshots))


if __name__ == '__main__':
    main()
