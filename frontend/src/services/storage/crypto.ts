const KEY_STORAGE = 'smart-velp.encryption-key.v1';

function toBase64(bytes: Uint8Array) {
  let binary = '';
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary);
}

function fromBase64(encoded: string) {
  const binary = atob(encoded);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}

async function getCryptoKey() {
  const existing = localStorage.getItem(KEY_STORAGE);
  const raw = existing ? fromBase64(existing) : crypto.getRandomValues(new Uint8Array(32));

  if (!existing) {
    localStorage.setItem(KEY_STORAGE, toBase64(raw));
  }

  return crypto.subtle.importKey('raw', raw, 'AES-GCM', false, ['encrypt', 'decrypt']);
}

export async function encryptSecret(value: string) {
  if (!value) {
    return '';
  }

  const key = await getCryptoKey();
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encoded = new TextEncoder().encode(value);
  const encrypted = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, encoded);
  const payload = new Uint8Array(iv.length + encrypted.byteLength);
  payload.set(iv, 0);
  payload.set(new Uint8Array(encrypted), iv.length);
  return toBase64(payload);
}

export async function decryptSecret(value: string) {
  if (!value) {
    return '';
  }

  const bytes = fromBase64(value);
  const iv = bytes.slice(0, 12);
  const payload = bytes.slice(12);
  const key = await getCryptoKey();
  const decrypted = await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, key, payload);
  return new TextDecoder().decode(decrypted);
}

