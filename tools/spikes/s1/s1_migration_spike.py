#!/usr/bin/env python3
"""S1 spike: v8 fixture -> v9 schema + compatibility VIEW.

Run from E:\\code\\MediaDock:
    python tools/spikes/s1/s1_migration_spike.py

This is a throw-away validation, not production code. It uses only the Python
stdlib and the exported Room v8 schema JSON.
"""
from __future__ import annotations

import hashlib
import json
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SCHEMA_PATH = ROOT / "app/schemas/com.lovelev.mediadock.data.local.MediaDatabase/8.json"


def uid(value: str) -> str:
    return hashlib.md5(value.encode("utf-8")).hexdigest()


def create_v8(conn: sqlite3.Connection) -> None:
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))["database"]
    assert schema["version"] == 8, schema["version"]
    for entity in schema["entities"]:
        conn.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            conn.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))


FIXTURE_MEDIA = [
    # path, name, size, lastModified, parsedTitle, year, season, episode, scrapeState, tmdbId,
    # poster, backdrop, overview, rating, scrapedAt
    ("电影/测试电影.2020.mp4", "测试电影.2020.mp4", 1000, "Mon, 01 Jan 2024 00:00:00 GMT",
     "测试电影", 2020, None, None, "MATCHED", 100, "/p100.jpg", "/b100.jpg", "电影简介", 7.5, 1700000000000),
    ("电影/测试电影.4K.mp4", "测试电影.4K.mp4", 2000, "Tue, 02 Jan 2024 00:00:00 GMT",
     "测试电影", 2020, None, None, "MATCHED", 100, "/p100.jpg", "/b100.jpg", "电影简介", 7.5, 1700000000000),
    ("动漫/测试剧/S01/测试剧01.mp4", "测试剧01.mp4", 3000, "Wed, 03 Jan 2024 00:00:00 GMT",
     "测试剧", 2023, 1, 1, "MATCHED", 200, "/p200.jpg", "/b200.jpg", "剧简介", 8.0, 1700000000000),
    ("动漫/测试剧/S02/29.简日双语.mp4", "29.简日双语.mp4", 4000, "Thu, 04 Jan 2024 00:00:00 GMT",
     "测试剧", 2023, 2, 29, "MATCHED", 200, "/p200.jpg", "/b200.jpg", "剧简介", 8.0, 1700000000000),
    ("local/电影/本地电影.mp4", "本地电影.mp4", 5000, None,
     "本地电影", 2019, None, None, "UNSCRAPED", None, None, None, None, None, None),
    ("content://media/external/video/media/123", "手机视频.mp4", 6000, None,
     "手机视频", None, None, None, "UNSCRAPED", None, None, None, None, None, None),
]

FIXTURE_PROGRESS = [
    ("电影/测试电影.2020.mp4", 120000, 600000, 1700000010000),
    ("动漫/测试剧/S01/测试剧01.mp4", 500000, 600000, 1700000020000),
    ("local/电影/本地电影.mp4", 550000, 600000, 1700000030000),
]

FIXTURE_FAVORITES = [
    ("电影/测试电影.2020.mp4", 1700000010000),
    ("动漫/测试剧/S01/测试剧01.mp4", 1700000020000),
    ("动漫/测试剧/S02/29.简日双语.mp4", 1700000030000),
]

FIXTURE_OVERRIDES = [("电影/测试电影.2020.mp4", "测试电影改名", 2021)]

FIXTURE_TITLE_DETAILS = [
    ("电影/测试电影.2020.mp4", 100, "2020-01-01", 120, "剧情,喜剧", "中国", "[]",
     1700000000000, None, None),
    ("动漫/测试剧/S01/测试剧01.mp4", 200, "2023-09-29", 24, "动画,剧情", "日本", "[]",
     1700000000000, 38, 2),
]

FIXTURE_EPISODE_META = [
    (200, 1, 1, "第1集", "/s1.jpg", 24, 1700000000000),
    (200, 2, 29, "第29集", "/s29.jpg", 25, 1700000000000),
]

V9_SCHEMA = """
CREATE TABLE sources(
  id TEXT PRIMARY KEY, type TEXT NOT NULL, name TEXT NOT NULL,
  base_url TEXT, root_path TEXT, credential_ref TEXT,
  enabled INTEGER NOT NULL, priority INTEGER NOT NULL, created_at INTEGER NOT NULL,
  last_scan_at INTEGER, health TEXT, root_generation INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE users(
  id TEXT PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL,
  pin_hash TEXT, created_at INTEGER NOT NULL
);

CREATE TABLE media_files(
  id TEXT PRIMARY KEY, source_id TEXT NOT NULL, remote_key TEXT NOT NULL,
  legacy_path TEXT, provider_item_id TEXT, media_kind TEXT NOT NULL,
  name TEXT NOT NULL, size_bytes INTEGER, mtime TEXT, etag TEXT,
  scan_run_id INTEGER, missing_at INTEGER,
  UNIQUE(source_id, remote_key)
);

CREATE TABLE works(
  id TEXT PRIMARY KEY, logical_key TEXT NOT NULL UNIQUE,
  tmdb_id INTEGER, type TEXT NOT NULL, title TEXT NOT NULL, sort_title TEXT,
  year INTEGER, poster_path TEXT, backdrop_path TEXT, overview TEXT, rating REAL,
  total_seasons INTEGER, total_episodes INTEGER, collection_id TEXT,
  metadata_source TEXT, external_ids_json TEXT,
  added_at INTEGER NOT NULL, latest_file_mtime INTEGER, updated_at INTEGER,
  scrape_state TEXT NOT NULL DEFAULT 'UNSCRAPED', scraped_at INTEGER,
  release_date TEXT, runtime_minutes INTEGER, country TEXT,
  genres_json TEXT, cast_json TEXT
);

CREATE TABLE episodes(
  id TEXT PRIMARY KEY, logical_key TEXT NOT NULL UNIQUE, work_id TEXT NOT NULL,
  season INTEGER NOT NULL, episode INTEGER NOT NULL,
  title TEXT, still_path TEXT, runtime INTEGER, air_date TEXT,
  UNIQUE(work_id, season, episode)
);

CREATE TABLE movie_versions(
  work_id TEXT NOT NULL, file_id TEXT NOT NULL, source_id TEXT NOT NULL,
  quality_label TEXT, size_bytes INTEGER, pinned INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY(work_id, file_id)
);

CREATE TABLE episode_versions(
  episode_id TEXT NOT NULL, file_id TEXT NOT NULL, source_id TEXT NOT NULL,
  quality_label TEXT, size_bytes INTEGER, pinned INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY(episode_id, file_id)
);

CREATE TABLE file_segments(
  file_id TEXT NOT NULL, episode_id TEXT NOT NULL, segment_index INTEGER NOT NULL,
  start_ms INTEGER, end_ms INTEGER,
  PRIMARY KEY(file_id, segment_index)
);

CREATE TABLE movie_progress(
  work_id TEXT NOT NULL, user_id TEXT NOT NULL, position_ms INTEGER NOT NULL,
  duration_ms INTEGER NOT NULL, completed INTEGER NOT NULL, updated_at INTEGER NOT NULL,
  PRIMARY KEY(work_id, user_id)
);
CREATE TABLE episode_progress(
  episode_id TEXT NOT NULL, user_id TEXT NOT NULL, position_ms INTEGER NOT NULL,
  duration_ms INTEGER NOT NULL, completed INTEGER NOT NULL, updated_at INTEGER NOT NULL,
  PRIMARY KEY(episode_id, user_id)
);
CREATE TABLE file_progress(
  file_id TEXT NOT NULL, user_id TEXT NOT NULL, position_ms INTEGER NOT NULL,
  duration_ms INTEGER NOT NULL, updated_at INTEGER NOT NULL,
  PRIMARY KEY(file_id, user_id)
);

CREATE TABLE favorites(
  work_id TEXT NOT NULL, user_id TEXT NOT NULL, added_at INTEGER NOT NULL,
  PRIMARY KEY(work_id, user_id)
);
CREATE TABLE playlists(
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, user_id TEXT NOT NULL,
  name TEXT NOT NULL, created_at INTEGER NOT NULL
);
CREATE TABLE playlist_items(
  playlist_id INTEGER NOT NULL, work_id TEXT NOT NULL, added_at INTEGER NOT NULL,
  PRIMARY KEY(playlist_id, work_id)
);
CREATE TABLE title_overrides(
  work_id TEXT PRIMARY KEY, title TEXT NOT NULL, year INTEGER
);
CREATE TABLE provider_health(
  provider TEXT PRIMARY KEY, state TEXT NOT NULL, last_error TEXT, updated_at INTEGER NOT NULL
);
"""

VIEW_SQL = """
CREATE VIEW media_items AS
SELECT
  mf.legacy_path AS path,
  mf.name AS name,
  mf.size_bytes AS sizeBytes,
  mf.mtime AS lastModified,
  COALESCE(mw.title, ew.title, mf.name) AS parsedTitle,
  COALESCE(mw.year, ew.year) AS year,
  e.season AS season,
  e.episode AS episode,
  COALESCE(mw.scrape_state, ew.scrape_state, 'UNSCRAPED') AS scrapeState,
  COALESCE(mw.tmdb_id, ew.tmdb_id) AS tmdbId,
  COALESCE(mw.poster_path, ew.poster_path) AS posterPath,
  COALESCE(mw.backdrop_path, ew.backdrop_path) AS backdropPath,
  COALESCE(mw.overview, ew.overview) AS overview,
  COALESCE(mw.rating, ew.rating) AS rating,
  COALESCE(mw.scraped_at, ew.scraped_at) AS scrapedAt
FROM media_files mf
LEFT JOIN movie_versions mv ON mv.file_id = mf.id
LEFT JOIN works mw ON mw.id = mv.work_id
LEFT JOIN (
  SELECT ev.file_id, e.id, e.work_id, e.season, e.episode
  FROM episode_versions ev
  JOIN episodes e ON e.id = ev.episode_id
  JOIN file_segments fs ON fs.file_id = ev.file_id AND fs.episode_id = e.id
  WHERE fs.segment_index = (
    SELECT MIN(fs2.segment_index) FROM file_segments fs2 WHERE fs2.file_id = ev.file_id
  )
) e ON e.file_id = mf.id
LEFT JOIN works ew ON ew.id = e.work_id
"""


def insert_v8(conn: sqlite3.Connection) -> None:
    conn.executemany(
        "INSERT INTO media_items(path,name,sizeBytes,lastModified,parsedTitle,year,season,episode,"
        "scrapeState,tmdbId,posterPath,backdropPath,overview,rating,scrapedAt) "
        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        FIXTURE_MEDIA,
    )
    conn.executemany(
        "INSERT INTO playback_progress(path,positionMs,durationMs,updatedAt) VALUES(?,?,?,?)",
        FIXTURE_PROGRESS,
    )
    conn.executemany("INSERT INTO favorites(path,addedAt) VALUES(?,?)", FIXTURE_FAVORITES)
    conn.executemany("INSERT INTO title_overrides(path,title,year) VALUES(?,?,?)", FIXTURE_OVERRIDES)
    conn.executemany(
        "INSERT INTO title_details(path,tmdbId,releaseDate,runtimeMinutes,genres,country,castJson,"
        "fetchedAt,totalEpisodes,totalSeasons) VALUES(?,?,?,?,?,?,?,?,?,?)",
        FIXTURE_TITLE_DETAILS,
    )
    conn.executemany(
        "INSERT INTO episode_meta(tmdbId,season,episode,name,stillPath,runtimeMinutes,fetchedAt) "
        "VALUES(?,?,?,?,?,?,?)",
        FIXTURE_EPISODE_META,
    )
    conn.execute("INSERT INTO playlists(id,name,createdAt) VALUES(1,'我的片单',1700000000000)")
    conn.executemany(
        "INSERT INTO playlist_items(playlistId,path,addedAt) VALUES(1,?,?)",
        [(m[0], 1700000000000) for m in FIXTURE_MEDIA[:4]],
    )


def migrate(conn: sqlite3.Connection) -> None:
    conn.execute("INSERT INTO sources VALUES('src-webdav','WEBDAV','默认 WebDAV',NULL,NULL,NULL,1,0,0,NULL,'HEALTHY',0)")
    conn.execute("INSERT INTO sources VALUES('src-local','LOCAL','本地',NULL,NULL,NULL,1,10,0,NULL,'HEALTHY',0)")
    conn.execute("INSERT INTO users VALUES('default','默认','ADULT',NULL,0)")

    path_map: dict[str, dict] = {}
    for row in conn.execute("SELECT * FROM media_items ORDER BY path"):
        (path, name, size, last_modified, parsed_title, year, season, episode,
         scrape_state, tmdb_id, poster, backdrop, overview, rating, scraped_at) = row
        source_id = "src-local" if path.startswith("local/") or path.startswith("content://") else "src-webdav"
        file_id = uid("file:" + path)
        conn.execute(
            "INSERT INTO media_files VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            (file_id, source_id, path, path, None, "main", name, size, last_modified, None, 1, None),
        )
        is_episode = episode is not None or season is not None
        if is_episode:
            if tmdb_id is not None:
                work_key = f"tmdb:tv:{tmdb_id}"
                ep_key = f"tmdb:tv:{tmdb_id}:s{season or 1}:e{episode or 0}"
            else:
                work_key = f"file:{source_id}:{path}#series"
                ep_key = f"file:{source_id}:{path}#episode"
            work_id = uid("work:" + work_key)
            episode_id = uid("episode:" + ep_key)
            conn.execute(
                "INSERT OR IGNORE INTO works(id,logical_key,tmdb_id,type,title,year,"
                "poster_path,backdrop_path,overview,rating,added_at,updated_at,scrape_state,scraped_at) "
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                (work_id, work_key, tmdb_id, "tv", parsed_title, year, poster, backdrop,
                 overview, rating, 1700000000000, 1700000000000, scrape_state, scraped_at),
            )
            conn.execute(
                "INSERT OR IGNORE INTO episodes(id,logical_key,work_id,season,episode,title) "
                "VALUES(?,?,?,?,?,?)",
                (episode_id, ep_key, work_id, season or 1, episode or 0, parsed_title),
            )
            conn.execute(
                "INSERT INTO episode_versions(episode_id,file_id,source_id,quality_label,size_bytes,pinned) "
                "VALUES(?,?,?,?,?,0)",
                (episode_id, file_id, source_id, None, size),
            )
            conn.execute(
                "INSERT INTO file_segments(file_id,episode_id,segment_index,start_ms,end_ms) "
                "VALUES(?,?,0,0,0)",
                (file_id, episode_id),
            )
            path_map[path] = {"source": source_id, "work": work_id, "episode": episode_id, "file": file_id}
        else:
            work_key = f"tmdb:movie:{tmdb_id}" if tmdb_id is not None else f"file:{source_id}:{path}"
            work_id = uid("work:" + work_key)
            conn.execute(
                "INSERT OR IGNORE INTO works(id,logical_key,tmdb_id,type,title,year,"
                "poster_path,backdrop_path,overview,rating,added_at,updated_at,scrape_state,scraped_at) "
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                (work_id, work_key, tmdb_id, "movie", parsed_title, year, poster, backdrop,
                 overview, rating, 1700000000000, 1700000000000, scrape_state, scraped_at),
            )
            conn.execute(
                "INSERT INTO movie_versions(work_id,file_id,source_id,quality_label,size_bytes,pinned) "
                "VALUES(?,?,?,?,?,0)",
                (work_id, file_id, source_id, None, size),
            )
            path_map[path] = {"source": source_id, "work": work_id, "episode": None, "file": file_id}

    # title_details -> works
    for (path, tmdb_id, release_date, runtime, genres, country, cast_json,
         _fetched, total_eps, total_seasons) in conn.execute(
            "SELECT path,tmdbId,releaseDate,runtimeMinutes,genres,country,castJson,"
            "fetchedAt,totalEpisodes,totalSeasons FROM title_details"):
        m = path_map.get(path)
        if not m:
            continue
        conn.execute(
            "UPDATE works SET release_date=?, runtime_minutes=?, country=?, genres_json=?, cast_json=?,"
            " total_episodes=COALESCE(?, total_episodes), total_seasons=COALESCE(?, total_seasons)"
            " WHERE id=?",
            (release_date, runtime, country, genres, cast_json, total_eps, total_seasons, m["work"]),
        )

    # episode_meta -> episodes
    for (tmdb_id, season, episode, title, still, runtime, _fetched) in conn.execute(
            "SELECT tmdbId,season,episode,name,stillPath,runtimeMinutes,fetchedAt FROM episode_meta"):
        conn.execute(
            "UPDATE episodes SET title=?, still_path=?, runtime=? "
            "WHERE id IN (SELECT id FROM episodes WHERE season=? AND episode=? "
            "AND work_id=(SELECT id FROM works WHERE logical_key=?))",
            (title, still, runtime, season, episode, f"tmdb:tv:{tmdb_id}"),
        )

    # progress
    for path, position, duration, updated in conn.execute(
            "SELECT path,positionMs,durationMs,updatedAt FROM playback_progress"):
        m = path_map[path]
        completed = 1 if position >= duration * 0.9 else 0
        if m["episode"] is not None:
            conn.execute(
                "INSERT INTO episode_progress(episode_id,user_id,position_ms,duration_ms,completed,updated_at)"
                " VALUES(?, 'default', ?, ?, ?, ?)",
                (m["episode"], position, duration, completed, updated),
            )
        else:
            conn.execute(
                "INSERT INTO movie_progress(work_id,user_id,position_ms,duration_ms,completed,updated_at)"
                " VALUES(?, 'default', ?, ?, ?, ?)",
                (m["work"], position, duration, completed, updated),
            )

    # playlists
    for playlist_id, name, created in conn.execute("SELECT id,name,createdAt FROM playlists_legacy"):
        conn.execute(
            "INSERT INTO playlists(id,user_id,name,created_at) VALUES(?, 'default', ?, ?)",
            (playlist_id, name, created),
        )

    # favorites / playlist items / overrides
    for path, added in conn.execute("SELECT path,addedAt FROM favorites_legacy"):
        m = path_map[path]
        conn.execute("INSERT OR IGNORE INTO favorites(work_id,user_id,added_at) VALUES(?, 'default', ?)",
                     (m["work"], added))
    for playlist_id, path, added in conn.execute(
            "SELECT playlistId,path,addedAt FROM playlist_items_legacy"):
        m = path_map[path]
        conn.execute("INSERT OR IGNORE INTO playlist_items(playlist_id,work_id,added_at) VALUES(?,?,?)",
                     (playlist_id, m["work"], added))
    for path, title, year in conn.execute("SELECT path,title,year FROM title_overrides_legacy"):
        m = path_map[path]
        conn.execute("INSERT OR REPLACE INTO title_overrides(work_id,title,year) VALUES(?,?,?)",
                     (m["work"], title, year))


def verify(conn: sqlite3.Connection) -> int:
    failures: list[str] = []

    def check(name: str, actual, expected) -> None:
        if actual != expected:
            failures.append(f"{name}: actual={actual!r} expected={expected!r}")
        print(("PASS " if actual == expected else "FAIL ") + f"{name}: {actual!r}")

    # Rename legacy table, create compatibility view
    conn.execute("ALTER TABLE media_items RENAME TO media_items_legacy")
    conn.execute(VIEW_SQL)

    check("view row count", conn.execute("SELECT COUNT(*) FROM media_items").fetchone()[0], 6)
    check("legacy row count", conn.execute("SELECT COUNT(*) FROM media_items_legacy").fetchone()[0], 6)

    columns = ["path", "name", "sizeBytes", "lastModified", "parsedTitle", "year",
               "season", "episode", "scrapeState", "tmdbId", "posterPath", "backdropPath",
               "overview", "rating", "scrapedAt"]
    legacy = {
        r[0]: dict(zip(columns, r))
        for r in conn.execute(f"SELECT {','.join(columns)} FROM media_items_legacy")
    }
    view = {
        r[0]: dict(zip(columns, r))
        for r in conn.execute(f"SELECT {','.join(columns)} FROM media_items")
    }
    for path, old in legacy.items():
        new = view.get(path)
        if new is None:
            failures.append(f"view missing {path}")
            continue
        for col in columns:
            if old[col] != new[col]:
                failures.append(f"{path} {col}: old={old[col]!r} new={new[col]!r}")
    check("view matches legacy rows", not [f for f in failures if "view" in f or "new=" in f], True)

    check("view IN(path) count",
          conn.execute("SELECT COUNT(*) FROM media_items WHERE path IN (?,?)",
                       ("电影/测试电影.2020.mp4", "动漫/测试剧/S02/29.简日双语.mp4")).fetchone()[0], 2)
    check("works", conn.execute("SELECT COUNT(*) FROM works").fetchone()[0], 4)
    check("episodes", conn.execute("SELECT COUNT(*) FROM episodes").fetchone()[0], 2)
    check("media_files", conn.execute("SELECT COUNT(*) FROM media_files").fetchone()[0], 6)
    check("movie_versions", conn.execute("SELECT COUNT(*) FROM movie_versions").fetchone()[0], 4)
    check("episode_versions", conn.execute("SELECT COUNT(*) FROM episode_versions").fetchone()[0], 2)
    check("movie_progress", conn.execute("SELECT COUNT(*) FROM movie_progress").fetchone()[0], 2)
    check("episode_progress", conn.execute("SELECT COUNT(*) FROM episode_progress").fetchone()[0], 1)
    check("favorites deduped to works", conn.execute("SELECT COUNT(*) FROM favorites").fetchone()[0], 2)
    check("playlist_items deduped to works",
          conn.execute("SELECT COUNT(*) FROM playlist_items").fetchone()[0], 2)
    check("title_overrides", conn.execute("SELECT COUNT(*) FROM title_overrides").fetchone()[0], 1)
    check("episode meta applied",
          conn.execute("SELECT title FROM episodes WHERE season=2 AND episode=29").fetchone()[0], "第29集")
    check("title details applied",
          conn.execute("SELECT total_episodes FROM works WHERE logical_key='tmdb:tv:200'").fetchone()[0], 38)

    if failures:
        print("\nFAILURES:")
        for failure in failures:
            print(" -", failure)
        return 1
    print("\nS1 SQL migration spike: ALL CHECKS PASSED")
    return 0


def main() -> int:
    if not SCHEMA_PATH.exists():
        print(f"missing schema: {SCHEMA_PATH}", file=sys.stderr)
        return 2
    conn = sqlite3.connect(":memory:")
    try:
        create_v8(conn)
        insert_v8(conn)
        # Keep v8 tables whose shape changes as legacy for rollback.
        for table in ("favorites", "playlists", "playlist_items", "title_overrides"):
            conn.execute(f"ALTER TABLE {table} RENAME TO {table}_legacy")
        conn.executescript(V9_SCHEMA)
        migrate(conn)
        return verify(conn)
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main())
