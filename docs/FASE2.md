# Fase 2 — Terminal nyata + Claude Code (login akun)

Scaffold arsitektur untuk MVP: terminal interaktif sungguhan + menjalankan
**Claude Code CLI asli** dengan **login akun Claude (OAuth)** — tanpa API key.

## Mengapa harus seperti ini

Anda hanya punya akun Claude Pro (tanpa API key). Satu-satunya cara yang **sesuai
ToS** untuk memakai langganan di aplikasi adalah membiarkan **binary `claude`
resmi** yang melakukan OAuth — aplikasi kita cuma menyediakan terminal tempat ia
berjalan. (Menyuntik token langganan ke app sendiri = pelanggaran & diblokir.)

Konsekuensinya, app harus bisa **menjalankan program Linux** (Node + claude) di
perangkat — itulah inti Fase 2.

## Komponen scaffold (sudah ada di repo, meng-compile)

| Berkas | Peran |
|--------|-------|
| `engine/LinuxEnvironment.kt` | Layout prefix ala-Termux ($PREFIX/$HOME/PATH), env vars |
| `engine/BootstrapInstaller.kt` | Ekstrak userland (Node+claude) dari assets/zip, set +x, symlink |
| `engine/PtyProcess.kt` | Jembatan JNI ke PTY (forkpty) — wajib agar `claude` login interaktif jalan |
| `engine/TerminalSession.kt` | Sesi shell: stream output, kirim input, resize; fallback non-interaktif sebelum PTY aktif |
| `engine/ClaudeLauncher.kt` | Perintah setup (`pkg/npm`), `claude` (launch), login/logout |
| `cpp/pty.c`, `cpp/CMakeLists.txt` | Implementasi native PTY (belum di-wire ke build) |

Renderer output memakai `AnsiParser` yang sudah ada.

## Arsitektur runtime

```
TerminalScreen (Compose)
   │  bytes keluar ──► AnsiParser ──► tampilan
   │  keystroke  ◄──  keyboard + tombol khusus (Esc/Tab/Ctrl/panah)
   ▼
TerminalSession ──► PtyProcess (JNI forkpty)  ──►  bash
                         (fallback: ProcessBuilder)     └► node ► claude  ──OAuth──► akun Claude
   ▲
BootstrapInstaller ► LinuxEnvironment ($PREFIX berisi node, npm, claude)
```

## Yang MASIH kurang (gap jujur)

1. **Binari bootstrap (Node + Claude Code) untuk ARM Android.** Besar & spesifik
   per-arsitektur; tidak di-commit. Dua opsi mengisinya (Fase 5):
   - **Bundle**: taruh `app/src/main/assets/bootstrap-<abi>.zip` (offline).
   - **Unduh saat pertama jalan**: dari GitHub Releases (`downloadUrlFor`).
   Sumber bootstrap: pakai bootstrap Termux + `npm i -g @anthropic-ai/claude-code`,
   atau bangun custom (lihat termux-packages).
2. **Wiring native PTY** ke Gradle (lihat di bawah).
3. **`targetSdk 28`**: agar boleh meng-eksekusi binari dari data dir (Android ≥10
   memblokir exec dari data dir; Termux memakai targetSdk 28 untuk ini).
4. **TerminalScreen Compose** (render + keyboard khusus) + integrasi ke navigasi.
5. **Foreground service** agar sesi panjang tidak dibunuh OS.

## Cara mengaktifkan native PTY (langkah berikutnya)

Di `app/build.gradle`, dalam `android { defaultConfig { } }`:
```gradle
externalNativeBuild { cmake { } }
ndk { abiFilters 'arm64-v8a', 'armeabi-v7a', 'x86_64' }
```
dan dalam `android { }`:
```gradle
externalNativeBuild { cmake { path 'src/main/cpp/CMakeLists.txt' } }
```
Runner GitHub Actions sudah punya NDK+CMake, jadi cloud build tetap jalan.
Setelah aktif, `PtyProcess.isAvailable()` menjadi true dan terminal jadi
interaktif penuh.

## Urutan kerja Fase 2

1. ✅ Scaffold engine — **selesai**.
2. ✅ `TerminalScreen` Compose + tombol keyboard khusus + wire ke navigasi (jadi layar utama) — **selesai**.
3. ✅ Aktifkan native PTY (CMake + targetSdk 28) → terminal interaktif — **selesai, build hijau**.
4. ✅ Unduh bootstrap saat runtime (`installFromUrl`) → tinggal **publikasikan** arsipnya.
5. ⏳ Publikasikan `bootstrap-<abi>.zip` (Node + Claude Code) ke GitHub Releases (tag `bootstrap-v1`).
6. ⏳ Alur sekali-jalan: Setup Claude → unduh bootstrap → `claude` → **login akun** → siap.

### Status: sisi-aplikasi Fase 2 SELESAI
Yang tersisa adalah **satu artifact ops**, bukan kode: membuat & meng-upload
arsip bootstrap. Tanpa itu, aplikasi tetap jalan sebagai **terminal interaktif
(shell sistem via PTY)**; tombol "Setup Claude" akan mengunduh bootstrap begitu
arsipnya dipublikasikan.

### Cara membuat & publikasi bootstrap

**Opsi A — otomatis (experimental):** jalankan workflow **"Build Bootstrap
(experimental)"** (Actions → Run workflow). Ia memakai `scripts/build-bootstrap.sh`
untuk: unduh bootstrap Termux per-ABI → pasang Node + Claude Code via emulasi
qemu → repackage → publish ke Release `bootstrap-v1`. ⚠️ Belum tervalidasi di
perangkat; kemungkinan perlu iterasi (lihat caveat di bawah).

**Opsi B — manual (paling andal):**
1. Di Termux (HP): `pkg install nodejs git && npm i -g @anthropic-ai/claude-code`
2. Zip **isi** prefix (`bin/`, `lib/`, `SYMLINKS.txt`, …) → `bootstrap-arm64-v8a.zip`
3. Buat Release tag **`bootstrap-v1`**, lampirkan zip-nya
4. App mengunduhnya saat "Setup Claude".

#### Relokasi path (sudah ditangani app)
Biner/skrip Termux di-build untuk prefix `/data/data/com.termux/...`, sedangkan
app ini memakai `/data/data/com.zeroxare.claudemobile/...`. Saat install,
`BootstrapInstaller.relocatePaths()` **otomatis menulis ulang** path Termux →
prefix app pada semua file teks (shebang skrip, config, wrapper). Biner ELF
di-skip dan menemukan lib-nya via `LD_LIBRARY_PATH=$PREFIX/lib`
(`LinuxEnvironment.buildEnv`).

#### Catatan jujur soal sumber bootstrap
- **Opsi B (on-device) = paling andal.** Biner Termux adalah **bionic** (libc
  Android), jadi paling aman dibuat di Android (Termux) lalu di-zip. App akan
  merelokasi path-nya otomatis saat install.
- **Opsi A (qemu di CI) = experimental & kemungkinan TIDAK jalan apa adanya:**
  `qemu-user` di runner glibc tak punya linker/libc bionic, sehingga menjalankan
  `apt`/`npm` Termux saat build sering gagal. Untuk CI yang benar-benar jalan
  perlu emulator Android penuh atau rootfs glibc + proot — di luar lingkup MVP.
- Yang masih perlu **divalidasi di perangkat**: apakah `node` (ELF bionic) jalan
  di prefix app dengan `LD_LIBRARY_PATH` + path ter-relokasi. Dari situ kita
  iterasi (mis. tambah `patchelf`/`termux-exec` bila perlu).

## Catatan cepat untuk test SEKARANG (interim)

Selagi bootstrap belum siap, cara tercepat & sah menguji "Claude Code + akun di
HP": pasang **Termux (F-Droid)** → `pkg install nodejs` →
`npm i -g @anthropic-ai/claude-code` → `claude` → login akun. App ini adalah versi
terpadu/terpoles dari alur tersebut.
