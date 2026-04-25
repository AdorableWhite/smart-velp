import Dexie, { type Table } from 'dexie';
import type { ImportedMediaRecord, StoredTranslationProfile } from '../../types/media';

class SmartVelpDb extends Dexie {
  importedMedia!: Table<ImportedMediaRecord, string>;
  translationProfiles!: Table<StoredTranslationProfile, string>;

  constructor() {
    super('smart-velp-db');
    this.version(1).stores({
      importedMedia: 'id, createdAt',
      translationProfiles: 'id, createdAt'
    });
  }
}

export const db = new SmartVelpDb();

