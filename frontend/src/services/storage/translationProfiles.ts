import type { StoredTranslationProfile, TranslationProfile } from '../../types/media';
import { decryptSecret, encryptSecret } from './crypto';
import { db } from './db';

export async function listTranslationProfiles() {
  const rows = await db.translationProfiles.orderBy('createdAt').reverse().toArray();
  return Promise.all(
    rows.map(async (row) => ({
      id: row.id,
      name: row.name,
      provider: row.provider,
      baseUrl: row.baseUrl,
      model: row.model,
      apiKey: await decryptSecret(row.encryptedApiKey),
      createdAt: row.createdAt
    }))
  );
}

export async function getTranslationProfile(profileId?: string) {
  if (!profileId) {
    return undefined;
  }

  const row = await db.translationProfiles.get(profileId);
  if (!row) {
    return undefined;
  }

  return {
    id: row.id,
    name: row.name,
    provider: row.provider,
    baseUrl: row.baseUrl,
    model: row.model,
    apiKey: await decryptSecret(row.encryptedApiKey),
    createdAt: row.createdAt
  };
}

export async function saveTranslationProfile(profile: Omit<TranslationProfile, 'id' | 'createdAt'> & { id?: string }) {
  const record: StoredTranslationProfile = {
    id: profile.id ?? crypto.randomUUID(),
    name: profile.name,
    provider: profile.provider,
    baseUrl: profile.baseUrl,
    model: profile.model,
    encryptedApiKey: await encryptSecret(profile.apiKey),
    createdAt: Date.now()
  };

  await db.translationProfiles.put(record);
  return record.id;
}

export async function deleteTranslationProfile(profileId: string) {
  await db.translationProfiles.delete(profileId);
}
