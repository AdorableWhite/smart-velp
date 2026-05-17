import type { StoredTranslationProfile, TranslationProfile } from '../../types/media';
import { decryptSecret, encryptSecret } from './crypto';
import { db } from './db';
import { defaultTranslationPrompt } from './translationServiceCatalog';

function toProfile(row: StoredTranslationProfile, apiKey: string): TranslationProfile {
  return {
    id: row.id,
    name: row.name,
    provider: row.provider,
    tier: row.tier ?? 'custom',
    baseUrl: row.baseUrl,
    model: row.model,
    apiKey,
    prompt: row.prompt ?? defaultTranslationPrompt,
    requiresApiKey: row.requiresApiKey ?? true,
    enabled: row.enabled ?? true,
    isDefault: row.isDefault ?? false,
    createdAt: row.createdAt
  };
}

export async function listTranslationProfiles() {
  const rows = await db.translationProfiles.orderBy('createdAt').reverse().toArray();
  return Promise.all(
    rows.map(async (row) => toProfile(row, await decryptSecret(row.encryptedApiKey)))
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

  return toProfile(row, await decryptSecret(row.encryptedApiKey));
}

export async function getPreferredTranslationProfile(profileId?: string) {
  const selected = await getTranslationProfile(profileId);
  if (selected?.enabled) {
    return selected;
  }

  const rows = await listTranslationProfiles();
  return rows.find((row) => row.enabled && row.isDefault) ?? rows.find((row) => row.enabled);
}

export async function saveTranslationProfile(
  profile: Omit<TranslationProfile, 'id' | 'createdAt' | 'isDefault'> & { id?: string; isDefault?: boolean }
) {
  const existing = profile.id ? await db.translationProfiles.get(profile.id) : undefined;
  const record: StoredTranslationProfile = {
    id: profile.id ?? crypto.randomUUID(),
    name: profile.name,
    provider: profile.provider,
    tier: profile.tier,
    baseUrl: profile.baseUrl,
    model: profile.model,
    encryptedApiKey: await encryptSecret(profile.apiKey),
    prompt: profile.prompt,
    requiresApiKey: profile.requiresApiKey,
    enabled: profile.enabled,
    isDefault: profile.isDefault ?? existing?.isDefault ?? false,
    createdAt: existing?.createdAt ?? Date.now()
  };

  await db.translationProfiles.put(record);
  return record.id;
}

export async function updateTranslationProfileState(profileId: string, patch: Pick<StoredTranslationProfile, 'enabled'>) {
  await db.translationProfiles.update(profileId, patch);
}

export async function setDefaultTranslationProfile(profileId: string) {
  const rows = await db.translationProfiles.toArray();
  await db.transaction('rw', db.translationProfiles, async () => {
    await Promise.all(
      rows.map((row) =>
        db.translationProfiles.update(row.id, {
          isDefault: row.id === profileId,
          enabled: row.id === profileId ? true : row.enabled
        })
      )
    );
  });
}

export async function deleteTranslationProfile(profileId: string) {
  await db.translationProfiles.delete(profileId);
}
