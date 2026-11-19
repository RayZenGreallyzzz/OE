# Permanent OE owner signing identity — PUBLIC fingerprint only (v35+)

**OWNER PRIVATE KEY AND PASSWORD ARE NEVER STORED IN THIS REPOSITORY.**

As of 2026-11-19, the owner approved a one-time signing-key rotation to continue
releasing their private full Phoenix UV44 + OE builds on Android without a PC.

- Android application ID (unchanged): `game.oe.editor.phoenix013fix1`
- First build signed with the new permanent key: **versionCode 35**
- NEW Android X.509 signing certificate SHA-256:
  `edca742dbebab11e9b36b5fee15af0475a0e57315a1173c1d076689d61e262bb`
- Previous v30–v33 cert SHA-256 (cannot update in-place to NEW):
  `a677c007060b0ab17abb6200b9abddd094005ef1bce0dd86f8c760a1a256ae27`
- Owner backup file delivered privately:
  `OE_Phoenix_NEW_PERMANENT_SIGNING_KEY_BACKUP_KEEP_PRIVATE.zip`
- Private PKCS#12 name **within owner-only backup**:
  `OE_Phoenix_2026_NEW_PERMANENT_Signing_Key_KEEP_PRIVATE.p12`
- Password name **within owner-only backup**:
  `OE_Phoenix_2026_NEW_PERMANENT_Signing_Key_Password_KEEP_PRIVATE.txt`

**For every future compatible v36+ APK:** request that the owner provide the
private backup archive securely when it is not available in runtime; extract
privately, verify its X.509 cert SHA-256 against the NEW public fingerprint
above, and sign the same package ID with increasing versionCode. Never generate
another key or post the backup to public GitHub.

**One-time migration:** Android does not install an APK signed with a new
certificate over an installed app with the old certificate. The owner has said
their OE city is empty and Phoenix JSON is saved. However, uninstalling the old
app removes its internal data: first verify that Phoenix JSON, all referenced
textures, and exported GLB files are independently saved in Android
Downloads/Documents. Do not suggest uninstalling before that check.

**Protected builder:** full v35 owner-only APK copies the exact
`assets/phoenix/` tree from v33 (all 1910 files verified equal by SHA-256),
plus the owner's private medieval asset pack, without changing any Phoenix UI,
build tabs, editing tools, or builder assets. The compiled OE v35 core comes
from successful Android CI. No private assets in this public repository.

Note: a successful CI compile and offline APK v2 signature verification do not
replace an actual Android tablet installation and graphics smoke test.

## 2026-11-19 v35 INSTALL HOTFIX — resource table ZIP alignment

The first 112,693,377-byte private v35 signed APK was **not installable** on the
owner's tablet. Investigation of its ZIP local entry headers established that
`resources.arsc` was stored uncompressed at byte offset **3291317** (offset
modulo 4 = 1). Android 11+ rejects APKs targeting API 30+ unless stored
`resources.arsc` starts at a **4-byte boundary** (parse error -124).

Corrected owner-only APK:
`OE_0.7.13_Phoenix_UV44_v35_FIXED_NEW_PERMANENT_KEY_INSTALL.apk`
(112,693,391 bytes; SHA-256
`29e19568343c4c2c9a6432bae3aa9e4c3b5345fecb9efb56d7817cfa24f5697b`).

The fixed payload is rebuilt by aligning **every STORED ZIP member** to 4
bytes, with `lib/arm64-v8a/liboe_native.so` at a **16 KiB boundary**.
`resources.arsc` now starts at offset **3291324**, modulo 4 = 0;
`liboe_native.so` at **131072**, modulo 16384 = 0. All 1910 Phoenix
builder members match v33 **by SHA-256** (not merely CRC); Android CI v35
manifest, DEX, native library and resources byte-for-byte match the compiled
artifact. The APK ZIP integrity and RSA-v2 signature both verify. The **same
NEW permanent signing certificate** `edca742d…d61e262bb` signs the hotfix;
no second signing key was generated. The owner's existing new key backup is
still valid unchanged. **Do not distribute the earlier broken APK.**

This hotfix changes only private APK ZIP packaging/offsets, *not source code,
Phoenix builder, models, GLB project, or application ID*. No device-side QA
claim can be made until the owner successfully installs and runs the hotfix.
