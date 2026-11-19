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
