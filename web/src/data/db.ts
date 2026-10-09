import { type DBSchema, type IDBPDatabase, openDB } from 'idb'
import type { Bookmark, Category, Tombstone } from './models'

interface BookmarkDB extends DBSchema {
  bookmarks: { key: string; value: Bookmark; indexes: { byUrl: string } }
  categories: { key: string; value: Category }
  tombstones: { key: string; value: Tombstone }
  meta: { key: string; value: unknown }
}

let dbPromise: Promise<IDBPDatabase<BookmarkDB>> | null = null

export function getDb(): Promise<IDBPDatabase<BookmarkDB>> {
  dbPromise ??= openDB<BookmarkDB>('bookmark', 1, {
    upgrade(db) {
      const bookmarks = db.createObjectStore('bookmarks', { keyPath: 'id' })
      bookmarks.createIndex('byUrl', 'url')
      db.createObjectStore('categories', { keyPath: 'id' })
      db.createObjectStore('tombstones', { keyPath: 'id' })
      db.createObjectStore('meta')
    },
  })
  return dbPromise
}

/** Test helper: drop the cached connection so a fresh fake database is used. */
export async function resetDbForTests(): Promise<void> {
  if (dbPromise) (await dbPromise).close()
  dbPromise = null
  await new Promise<void>((resolve) => {
    const req = indexedDB.deleteDatabase('bookmark')
    req.onsuccess = req.onerror = req.onblocked = () => resolve()
  })
}

export async function getMeta<T>(key: string): Promise<T | undefined> {
  return (await (await getDb()).get('meta', key)) as T | undefined
}

export async function setMeta(key: string, value: unknown): Promise<void> {
  await (await getDb()).put('meta', value, key)
}
