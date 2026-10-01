(function (root) {
  "use strict";

  const DOCUMENT_TYPE = "SKYFORGE_STUDIO_REGIONAL_COMPARISON_LIBRARY";
  const FORMAT_VERSION = 1;
  const DATABASE_NAME = "skyforge-studio-regional-comparisons";
  const STORE_NAME = "comparisons";
  const MAX_ITEMS = 25;
  const MAX_TITLE_LENGTH = 120;
  const MAX_PACKAGE_BYTES = 10 * 1024 * 1024;
  const MAX_LIBRARY_BYTES = 25 * 1024 * 1024;

  function isRecord(value) { return value !== null && typeof value === "object" && !Array.isArray(value); }
  function byteLength(value) { return new TextEncoder().encode(value).length; }
  function exactKeys(value, keys) {
    return isRecord(value) && Object.keys(value).sort().join(",") === keys.slice().sort().join(",");
  }
  function canonicalTimestamp(value, label) {
    if (typeof value !== "string" || !Number.isFinite(Date.parse(value))) throw new Error(label + " must be a valid timestamp");
    const normalized = new Date(value).toISOString();
    if (normalized !== value) throw new Error(label + " must be a canonical UTC timestamp");
    return normalized;
  }
  function createRecord(id, title, packageJson, createdAt, updatedAt) {
    if (typeof id !== "string" || !/^[a-zA-Z0-9-]{8,80}$/.test(id)) throw new Error("saved comparison id is invalid");
    if (typeof title !== "string" || !title.trim() || title.trim().length > MAX_TITLE_LENGTH) {
      throw new Error("saved comparison name must be between 1 and " + MAX_TITLE_LENGTH + " characters");
    }
    if (typeof packageJson !== "string") throw new Error("saved comparison package must be JSON text");
    const packageBytes = byteLength(packageJson);
    if (packageBytes === 0 || packageBytes > MAX_PACKAGE_BYTES) throw new Error("saved comparison package must be 10 MB or smaller");
    return Object.freeze({
      id,
      title: title.trim(),
      package_json: packageJson,
      package_bytes: packageBytes,
      created_at: canonicalTimestamp(createdAt, "saved comparison creation time"),
      updated_at: canonicalTimestamp(updatedAt, "saved comparison update time"),
    });
  }
  function parseRecord(value) {
    const expected = ["id", "title", "package_json", "package_bytes", "created_at", "updated_at"];
    if (!exactKeys(value, expected)) throw new Error("saved comparison record has an invalid shape");
    const parsed = createRecord(value.id, value.title, value.package_json, value.created_at, value.updated_at);
    if (value.package_bytes !== parsed.package_bytes) throw new Error("saved comparison package size does not match its contents");
    return parsed;
  }
  function normalizeRecords(records) {
    if (!Array.isArray(records) || records.length > MAX_ITEMS) throw new Error("saved comparison library has an invalid number of items");
    const parsed = records.map(parseRecord);
    const ids = new Set(parsed.map(record => record.id));
    if (ids.size !== parsed.length) throw new Error("saved comparison library contains duplicate ids");
    const total = parsed.reduce((sum, record) => sum + record.package_bytes, 0);
    if (total > MAX_LIBRARY_BYTES) throw new Error("saved comparison library exceeds its 25 MB storage limit");
    return Object.freeze(parsed.slice().sort((a, b) => b.updated_at.localeCompare(a.updated_at) || a.id.localeCompare(b.id)));
  }
  function upsertRecords(records, record) {
    const current = normalizeRecords(records);
    let parsed = parseRecord(record);
    const existing = current.find(item => item.id === parsed.id);
    if (existing) {
      parsed = parseRecord(Object.assign({}, parsed, {created_at: existing.created_at}));
    } else if (current.length >= MAX_ITEMS) {
      throw new Error("saved comparison library is full; remove an item or export a portable package");
    }
    return normalizeRecords(current.filter(item => item.id !== parsed.id).concat(parsed));
  }
  function removeRecords(records, id) {
    if (typeof id !== "string" || !/^[a-zA-Z0-9-]{8,80}$/.test(id)) throw new Error("saved comparison id is invalid");
    return normalizeRecords(records).filter(item => item.id !== id);
  }

  function openDatabase(indexedDB = root && root.indexedDB) {
    if (!indexedDB || typeof indexedDB.open !== "function") return Promise.reject(new Error("browser-local comparison storage is unavailable"));
    return new Promise((resolve, reject) => {
      let request;
      try { request = indexedDB.open(DATABASE_NAME, 1); }
      catch (error) { reject(new Error("could not open browser-local comparison storage: " + String(error.message || error))); return; }
      request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains(STORE_NAME)) db.createObjectStore(STORE_NAME, {keyPath: "id"});
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(new Error("could not open browser-local comparison storage: " + String(request.error && request.error.message || "unknown storage error")));
      request.onblocked = () => reject(new Error("browser-local comparison storage is busy in another Studio tab"));
    });
  }
  function createRepository(indexedDB = root && root.indexedDB) {
    async function list() {
      const db = await openDatabase(indexedDB);
      return new Promise((resolve, reject) => {
        let result;
        let failure;
        let transaction;
        try {
          transaction = db.transaction(STORE_NAME, "readonly");
          const request = transaction.objectStore(STORE_NAME).getAll();
          request.onsuccess = () => {
            try { result = normalizeRecords(request.result); }
            catch (error) { failure = error; try { transaction.abort(); } catch {} }
          };
          request.onerror = () => { failure = request.error || new Error("could not read saved comparisons"); try { transaction.abort(); } catch {} };
          transaction.oncomplete = () => resolve(result);
          transaction.onerror = () => reject(failure || transaction.error || new Error("could not read saved comparisons"));
          transaction.onabort = () => reject(failure || transaction.error || new Error("could not read saved comparisons"));
        } catch (error) { reject(error); }
      });
    }
    async function save(record) {
      const parsed = parseRecord(record);
      const db = await openDatabase(indexedDB);
      return new Promise((resolve, reject) => {
        let result;
        let failure;
        let transaction;
        try {
          transaction = db.transaction(STORE_NAME, "readwrite");
          const store = transaction.objectStore(STORE_NAME);
          const request = store.getAll();
          request.onsuccess = () => {
            try {
              const next = upsertRecords(request.result, parsed);
              result = next.find(item => item.id === parsed.id);
              store.put(result);
            } catch (error) { failure = error; try { transaction.abort(); } catch {} }
          };
          request.onerror = () => { failure = request.error || new Error("could not read the saved comparison library"); try { transaction.abort(); } catch {} };
          transaction.oncomplete = () => resolve(result);
          transaction.onerror = () => reject(failure || transaction.error || new Error("could not save this comparison in the browser"));
          transaction.onabort = () => reject(failure || transaction.error || new Error("could not save this comparison in the browser"));
        } catch (error) { reject(error); }
      });
    }
    async function remove(id) {
      if (typeof id !== "string" || !/^[a-zA-Z0-9-]{8,80}$/.test(id)) throw new Error("saved comparison id is invalid");
      const db = await openDatabase(indexedDB);
      return new Promise((resolve, reject) => {
        let failure;
        let transaction;
        try {
          transaction = db.transaction(STORE_NAME, "readwrite");
          const store = transaction.objectStore(STORE_NAME);
          const request = store.getAll();
          request.onsuccess = () => {
            try {
              normalizeRecords(request.result);
              store.delete(id);
            } catch (error) { failure = error; try { transaction.abort(); } catch {} }
          };
          request.onerror = () => { failure = request.error || new Error("could not read the saved comparison library"); try { transaction.abort(); } catch {} };
          transaction.oncomplete = () => resolve();
          transaction.onerror = () => reject(failure || transaction.error || new Error("could not remove the saved comparison"));
          transaction.onabort = () => reject(failure || transaction.error || new Error("could not remove the saved comparison"));
        } catch (error) { reject(error); }
      });
    }
    return Object.freeze({list, save, remove});
  }

  const api = Object.freeze({
    createRecord, parseRecord, normalizeRecords, upsertRecords, removeRecords, createRepository,
    documentType: DOCUMENT_TYPE, formatVersion: FORMAT_VERSION, maximumItems: MAX_ITEMS,
    maximumPackageBytes: MAX_PACKAGE_BYTES, maximumLibraryBytes: MAX_LIBRARY_BYTES, maximumTitleLength: MAX_TITLE_LENGTH,
  });
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  if (root) root.SkyforgeRegionalComparisonLibrary = api;
})(typeof window !== "undefined" ? window : globalThis);
