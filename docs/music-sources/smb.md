# SMB Music Source

TidePlayer supports SMB2 and SMB3 shares as indexed music sources. SMB1 is not
implemented and the client never negotiates or falls back to SMB1.

## Capabilities

| Capability | Status |
| --- | --- |
| Browse shares and folders | Yes |
| Search imported tracks | Yes |
| Stream through the localhost Range gateway | Yes |
| Offline download | Yes |
| Full metadata scan | Yes |
| Incremental synchronization | No |
| Change Notify | No |

The implementation uses the pure-Rust `smb2` 0.13.1 crate. It provides SMB2/3,
NTLMv2, Guest, Domain/Workgroup, signing, encryption, directory enumeration,
and positioned `read_at` without OpenSSL, Kerberos, GSSAPI, or a platform SMB
dynamic library. TidePlayer uses bounded connection and reader pools around
that client rather than exposing `smb://` URLs to a platform player.

## Account configuration

In Settings, add an SMB source and provide:

- Display name
- Server hostname or IP address
- Port (defaults to `445`)
- Share name
- Optional root folder inside the share
- Username and password, or Guest
- Optional Domain or Workgroup
- Optional Require signing and Require encryption switches

The editor stores the server, share, root, port, domain, and security switches
as non-secret provider configuration. The password is stored only through the
platform credential store. Editing an account leaves the password field empty;
saving an empty password preserves the existing credential.

Conceptual authenticated configuration:

```text
Server: nas.example.lan
Port: 445
Share: Music
Root folder: Library/Lossless
Username: music-reader
Domain: HOME
```

Conceptual Guest configuration:

```text
Server: media-box.local
Share: PublicMusic
Guest: On
```

Turning on Guest clears and hides username, password, and Domain. Credentials
must not be embedded in an address such as
`smb://user:password@server/share`; such addresses are rejected.

## Signing and encryption

Require signing rejects a session that did not establish SMB message signing.
Require encryption requires an SMB3 session with negotiated encryption keys
and cipher support. SMB 3.1.1 uses the negotiated cipher; SMB 3.0 falls back
to its protocol-defined AES-128-CCM cipher when no 3.1.1 cipher context is
available. A server that mandates either feature is allowed to negotiate it
normally. Older servers that cannot meet a selected requirement return an
Unsupported error instead of silently weakening the connection.

Encryption protects SMB traffic between TidePlayer and the server. The
localhost playback gateway remains bound to `127.0.0.1` and uses a random,
per-session token.

## Browse, scan, and search

All paths are relative to the configured root folder and use `/` internally.
The backend accepts Unicode names, including Chinese, Japanese, spaces, and
Emoji. It rejects NUL and any `..` traversal instead of allowing a path to
escape the configured share/root.

SMB v1 performs a full directory scan. The common scan layer, rather than the
SMB transport, selects supported music files:

```text
flac, mp3, m4a, mp4, ogg, oga, opus, wav, aac, ape, wv, aif, aiff
```

Fast, Standard, and Full metadata modes reuse the existing remote Range
metadata reader. A failed or inaccessible file is recorded diagnostically and
does not silently remove the rest of a successful directory result. Imported
tracks are stored in the common Room library and are available to global and
source-scoped indexed search. TidePlayer does not issue live full-text queries
to the SMB server.

Incremental synchronization and SMB Change Notify are not part of the first
version, so every requested SMB refresh is a full scan.

## Playback and Range reads

```text
Library MediaId
  -> SMB MusicSource
  -> cached StorageBackend for the account
  -> SMB positioned read_at
  -> tokenized 127.0.0.1 HTTP gateway
  -> Media3 / AVPlayer / Desktop playback engine
```

The player never receives the SMB address or credential. HEAD, full GET,
explicit ranges, suffix ranges, and repeated seeks are served by the common
gateway. The gateway uses a bounded 256 KiB block cache. The SMB backend
streams in bounded 512 KiB chunks and keeps at most eight open file readers;
it does not load a complete song into memory for playback.

One session slot is reserved for playback/positioned reads and three bounded
slots serve directory enumeration. Transient connection errors receive at
most two retries with 200 ms and 400 ms delays. Authentication failures are
not retried. A changed remote size invalidates the reader and produces a
diagnostic error. Releasing a playback session releases that file reader.

## Downloads and platform status

| Platform | Build path | Resume behavior | Status |
| --- | --- | --- | --- |
| Android | Kotlin common source + Rust `aarch64-linux-android` / `x86_64-linux-android` | WorkManager retains a `.part` file and resumes from its length | Implemented; real NAS runtime not yet verified |
| iOS | Kotlin common source + Rust `aarch64-apple-ios` / `aarch64-apple-ios-sim` | URLSession resume data works while the app process retains the loopback playback session | Implemented; process-restart/background limitation below |
| Desktop | Kotlin/JVM + host Rust library | A retained `.part` file resumes with an HTTP/local offset | Implemented; real NAS runtime not yet verified |

Android and Desktop reject a resumed HTTP response unless it returns `206`,
and all schedulers reject a changed total size. Pause retains partial data;
cancel deletes it.

iOS cannot assume that a system background URLSession can reconnect to a
short-lived localhost gateway after TidePlayer has been terminated. The first
version retains resume data and the playback session in memory, but reliable
resume across process termination is not guaranteed. Keep the app process
alive for long SMB downloads; a killed download may need Retry.

## Compatibility matrix

Status terms:

- **Tested**: exercised against that server in a recorded run.
- **Expected compatible**: protocol behavior is covered, but that product was
  not exercised.
- **Not tested**: no compatibility claim.
- **Known issue**: a reproduced incompatibility exists.

| Server | Status | Evidence / notes |
| --- | --- | --- |
| Samba 4 | Not tested locally | CI provisions an isolated SMB2/3 Samba fixture, but a successful hosted run must be recorded before changing this to Tested |
| Windows 10/11 file sharing | Expected compatible, not tested | Uses standard SMB2/3 + NTLMv2 behavior |
| Synology DSM | Expected compatible, not tested | No physical DSM test was available |
| QNAP QTS | Expected compatible, not tested | No physical QTS test was available |
| OpenMediaVault | Expected compatible, not tested | Commonly Samba-based; no appliance test was available |
| TrueNAS | Expected compatible, not tested | Commonly Samba-based; no appliance test was available |

The local implementation environment did not have Docker or an accessible NAS,
so no entry is labeled Tested solely from code compilation.

## Integration fixture

Ignored Rust integration tests are in
`rust-libs/storage-backend/tests/smb_integration.rs`. CI creates random
ephemeral credentials and the following fixture:

```text
range.bin            contains 0123456789
mutable.bin          writable size-change fixture
large.flac           4 MiB bounded-stream fixture
音乐/大海.flac       Unicode path fixture
restricted/          permission-denied fixture
```

To run them against an equivalent disposable server:

```bash
export MUSICAPP_SMB_TEST_AUTH_URL='smb://127.0.0.1/authenticated'
export MUSICAPP_SMB_TEST_GUEST_URL='smb://127.0.0.1/guest'
export MUSICAPP_SMB_TEST_USERNAME='<ephemeral-user>'
export MUSICAPP_SMB_TEST_PASSWORD='<ephemeral-password>'
export MUSICAPP_SMB_TEST_FIXTURE_DIR='<writable-fixture-directory>'
cargo test --manifest-path rust-libs/Cargo.toml \
  --package storage-backend --test smb_integration \
  -- --ignored --nocapture
```

Never use a production NAS credential for this fixture or commit its value.

## Troubleshooting

| Error | Checks |
| --- | --- |
| Invalid address | Confirm server, port, share, and that credentials are not embedded in the URI |
| Authentication failed | Check username/password and Domain; do not repeatedly retry a locked account |
| Permission denied | Confirm the account can list the share and selected root folder |
| Not found | The share, selected root, or file may have moved or been deleted |
| Timeout / unavailable | Wake the NAS, verify port 445 routing, and retry after the network is stable |
| Unsupported | Confirm SMB2/3 is enabled and the server can meet selected signing/encryption requirements |
| Playback stops after a file edit | Rescan the source; a size change intentionally invalidates the old reader/cache |

## Security recommendations

- Disable SMB1 on the server.
- Use a read-only, least-privilege account dedicated to the music share.
- Prefer signing on trusted LANs and encryption on untrusted networks.
- Do not expose port 445 directly to the public Internet; use a trusted VPN.
- Do not paste production credentials into logs, bug reports, MediaIds, or
  integration-test environment files.
