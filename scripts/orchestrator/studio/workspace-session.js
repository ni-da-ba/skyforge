(function (root) {
  "use strict";

  const DATABASE_NAME = "skyforge-studio-inspection-session";
  const DATABASE_VERSION = 1;
  const STORE_NAME = "inspection";
  const RECORD_KEY = "current";
  const RECORD_VERSION = 1;

  function byteLength(value) {
    return new TextEncoder().encode(value).byteLength;
  }

  function validateRecord(value, maximumBytes = 25 * 1024 * 1024) {
    if (!value || typeof value !== "object" || Array.isArray(value)) {
      throw new Error("saved inspection record must be an object");
    }
    const allowed = new Set(["format_version", "enabled", "workspace_json"]);
    if (Object.keys(value).some(key => !allowed.has(key))) {
      throw new Error("saved inspection record contains unsupported fields");
    }
    if (Object.keys(value).length !== allowed.size ||
        !Object.prototype.hasOwnProperty.call(value, "format_version") ||
        !Object.prototype.hasOwnProperty.call(value, "enabled") ||
        !Object.prototype.hasOwnProperty.call(value, "workspace_json")) {
      throw new Error("saved inspection record is incomplete");
    }
    if (value.format_version !== RECORD_VERSION) {
      throw new Error("unsupported saved inspection record version");
    }
    if (value.enabled !== true) throw new Error("saved inspection record is not enabled");
    if (value.workspace_json !== null) {
      if (typeof value.workspace_json !== "string" || byteLength(value.workspace_json) > maximumBytes) {
        throw new Error("saved inspection workspace exceeds its supported size");
      }
      try {
        JSON.parse(value.workspace_json);
      } catch {
        throw new Error("saved inspection workspace is not valid JSON");
      }
    }
    return Object.freeze({
      format_version: RECORD_VERSION,
      enabled: true,
      workspace_json: value.workspace_json,
    });
  }

  function storageError(action, cause) {
    const detail = cause && typeof cause.message === "string" ? ": " + cause.message : "";
    return new Error("browser storage could not " + action + detail);
  }

  function createIndexedDbStore(indexedDB = root.indexedDB, maximumBytes = 25 * 1024 * 1024) {
    function open() {
      return new Promise((resolve, reject) => {
        if (!indexedDB || typeof indexedDB.open !== "function") {
          reject(storageError("open this inspection"));
          return;
        }
        let request;
        try {
          request = indexedDB.open(DATABASE_NAME, DATABASE_VERSION);
        } catch (error) {
          reject(storageError("open this inspection", error));
          return;
        }
        request.onupgradeneeded = () => {
          const database = request.result;
          if (!database.objectStoreNames.contains(STORE_NAME)) {
            database.createObjectStore(STORE_NAME);
          }
        };
        request.onerror = () => reject(storageError("open this inspection", request.error));
        request.onblocked = () => reject(storageError("open this inspection", new Error("another Studio tab is holding the database upgrade")));
        request.onsuccess = () => {
          const database = request.result;
          database.onversionchange = () => database.close();
          resolve(database);
        };
      });
    }

    function transact(mode, action, operation) {
      return open().then(database => new Promise((resolve, reject) => {
        let result;
        let transaction;
        try {
          transaction = database.transaction(STORE_NAME, mode);
        } catch (error) {
          database.close();
          reject(storageError(action, error));
          return;
        }
        transaction.oncomplete = () => {
          database.close();
          resolve(result);
        };
        transaction.onerror = () => {
          database.close();
          reject(storageError(action, transaction.error));
        };
        transaction.onabort = () => {
          database.close();
          reject(storageError(action, transaction.error));
        };
        try {
          operation(transaction.objectStore(STORE_NAME), value => { result = value; });
        } catch (error) {
          try { transaction.abort(); } catch {}
          database.close();
          reject(storageError(action, error));
        }
      }));
    }

    return Object.freeze({
      read() {
        return transact("readonly", "read the saved inspection", (store, setResult) => {
          const request = store.get(RECORD_KEY);
          request.onsuccess = () => setResult(request.result ?? null);
          request.onerror = () => { throw request.error || new Error("read request failed"); };
        });
      },
      write(value) {
        const record = validateRecord(value, maximumBytes);
        return transact("readwrite", "save the inspection", store => {
          store.put(record, RECORD_KEY);
        });
      },
      remove() {
        return transact("readwrite", "forget the inspection", store => {
          store.delete(RECORD_KEY);
        });
      },
    });
  }

  function createController(store, maximumBytes = 25 * 1024 * 1024) {
    if (!store || typeof store.read !== "function" ||
        typeof store.write !== "function" || typeof store.remove !== "function") {
      throw new Error("inspection session storage adapter is incomplete");
    }

    async function read() {
      const value = await store.read();
      return value == null ? null : validateRecord(value, maximumBytes);
    }

    async function enable(workspaceJson = null) {
      const record = validateRecord({
        format_version: RECORD_VERSION,
        enabled: true,
        workspace_json: workspaceJson,
      }, maximumBytes);
      await store.write(record);
      return record;
    }

    async function save(workspaceJson) {
      const current = await read();
      if (!current?.enabled) return false;
      const record = validateRecord({
        format_version: RECORD_VERSION,
        enabled: true,
        workspace_json: workspaceJson,
      }, maximumBytes);
      await store.write(record);
      return true;
    }

    async function forgetWorkspace() {
      const current = await read();
      if (!current?.enabled) return false;
      await store.write(validateRecord({
        format_version: RECORD_VERSION,
        enabled: true,
        workspace_json: null,
      }, maximumBytes));
      return true;
    }

    async function disable() {
      await store.remove();
    }

    return Object.freeze({ read, enable, save, forgetWorkspace, disable });
  }

  const api = Object.freeze({
    createIndexedDbStore,
    createController,
    validateRecord,
    maximumRecordVersion: RECORD_VERSION,
  });
  if (root && typeof root === "object") root.SkyforgeStudioWorkspaceSession = api;
  if (typeof module !== "undefined" && module.exports) module.exports = api;
})(typeof globalThis !== "undefined" ? globalThis : this);
