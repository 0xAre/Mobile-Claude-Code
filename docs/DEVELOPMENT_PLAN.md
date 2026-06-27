# Rencana Pengembangan — Mobile Claude Code

Dokumen ini berisi analisis kondisi proyek saat ini dan rencana pengembangan
bertahap hingga aplikasi **dapat di-build, diinstall, dan digunakan di Android**
sebagai terminal + asisten coding bertenaga Claude — dengan mengadopsi pola dari
Termux, Claude Code CLI, dan proyek komunitas yang relevan.

---

## 1. Ringkasan Eksekutif

Proyek saat ini adalah aplikasi Android (Kotlin + Jetpack Compose) yang
menampilkan **emulasi terminal palsu** plus panel chat Claude sederhana. Konsepnya
bagus, tetapi belum dapat di-build (tidak ada Gradle wrapper), terminalnya bukan
terminal sungguhan (tidak ada PTY/userland Linux), dan integrasi Claude hanya
chat biasa — bukan agen "Claude Code" yang bisa membaca/menulis file & menjalankan
perintah.

Rencana ini membaginya menjadi **6 fase**. Fase 0–1 menjadikan proyek bisa
di-build & diinstall. Fase 2–4 mengubahnya menjadi terminal nyata + agen coding.
Fase 5 menyiapkan distribusi (APK rilis, GitHub Releases, opsi F-Droid).

---

## 2. Analisis Kondisi Saat Ini

### Struktur yang sudah ada
```
app/src/main/java/com/zeroxare/claudemobile/
├── MainActivity.kt
├── terminal/TerminalManager.kt     # "terminal" simulasi
├── terminal/AnsiParser.kt          # parser warna ANSI → AnnotatedString
├── data/api/ClaudeApiService.kt    # Anthropic Messages API (OkHttp)
├── data/api/models/ClaudeModels.kt
├── data/prefs/AppPreferences.kt    # DataStore (apiKey, model, fontSize)
├── viewmodel/MainViewModel.kt
└── ui/...                          # MainScreen, SettingsScreen, ClaudePanel, TerminalView
```

### Yang sudah berfungsi (di level kode)
- UI Compose yang rapi: terminal view, command input + history, panel chat slide-in.
- Parser ANSI (16/256 warna) → `AnnotatedString`.
- Integrasi Anthropic Messages API dengan injeksi konteks output terminal.
- Penyimpanan setting via DataStore.

### Gap kritis (alasan belum "bisa diinstall & digunakan")
| # | Masalah | Dampak |
|---|---------|--------|
| **G1** | **Tidak ada Gradle wrapper** (`gradlew`, `gradle/wrapper/...`) | Proyek **tidak bisa di-build** secara reproducible / di CI. **Blocker utama.** |
| **G2** | Terminal **simulasi** — `ls`/`cat`/`grep` ditulis ulang manual + `ProcessBuilder("sh")` di sandbox Android | Bukan terminal sungguhan: tidak ada PTY, tidak interaktif (vim/top/ssh mati), tidak ada package manager, `sh` Android sangat terbatas |
| **G3** | Integrasi Claude hanya **chat**, bukan agen | Tidak bisa baca/tulis file atau jalankan perintah atas nama user → bukan "Claude Code" |
| **G4** | Tidak ada **streaming** respons | UX lambat, terasa menggantung untuk jawaban panjang |
| **G5** | API key disimpan **plaintext** di DataStore | Risiko keamanan kredensial |
| **G6** | Tidak ada **test, CI, signing config, ProGuard rules nyata** | Tidak ada jaminan kualitas & tidak ada APK rilis |
| **G7** | `MANAGE_EXTERNAL_STORAGE` diminta tanpa pemakaian jelas | Akan **ditolak Google Play**; izin berlebihan |
| **G8** | Bug kecil (mis. `isClauldLoading` typo, history index, model di request tidak ikut dipilih user) | Polish/kualitas |

---

## 3. Keputusan Arsitektur (paling penting — pilih dulu)

Ada tiga jalur untuk mewujudkan "terminal + Claude Code di Android". Ini
menentukan semua fase berikutnya.

### Path A — Bungkus Termux + jalankan Claude Code CLI asli
Adopsi inti terminal Termux (`terminal-emulator` + `terminal-view`), bundel
**bootstrap rootfs** Linux, lalu user `pkg install nodejs` dan
`npm i -g @anthropic-ai/claude-code` → menjalankan **Claude Code CLI yang asli**.
- ➕ Paling powerful & paling "asli"; dapat semua tool Claude Code gratis.
- ➖ Kompleksitas native (JNI, bootstrap per-ABI) tinggi; **lisensi Termux GPLv3**
  memaksa seluruh aplikasi jadi GPLv3; ukuran APK besar.

### Path B — Agen Claude Code native (Kotlin) ⭐ Rekomendasi awal
Pertahankan UI Compose. Bangun **terminal nyata berbasis PTY** (boleh pakai
library terminal Termux yang permisif sebagai komponen view), lalu implementasi
**loop agen tool-use di Kotlin**: tool `read_file`, `write_file`, `run_command`,
`list_dir`, dll. memanggil Anthropic API dengan tool calling terhadap workspace
sandbox aplikasi.
- ➕ Kontrol penuh atas UX & keamanan; APK ringan; tidak wajib GPLv3.
- ➖ Harus implementasi sendiri loop agen + tool (tapi terukur & bertahap).

### Path C — Hybrid (target akhir)
UI native (Path B) + opsi bootstrap ala Termux untuk shell sungguhan, sehingga
user bisa memilih: agen native **atau** menjalankan CLI asli.

> **Rekomendasi:** mulai **Path B** (cepat sampai "usable"), siapkan abstraksi
> agar bisa berkembang ke **Path C**. Catat keputusan lisensi sejak awal: jika
> kelak menyalin kode Termux non-permisif, repo harus GPLv3.

---

## 4. Referensi yang Diadopsi

| Sumber | Yang diambil | Catatan lisensi |
|--------|--------------|-----------------|
| [termux/termux-app](https://github.com/termux/termux-app) | Arsitektur PTY (emulasi → service → eksekusi native), pola bootstrap rootfs per-ABI yang di-embed di APK | **GPLv3** — menyalin kode = repo jadi GPLv3 |
| [termux/termux-app `terminal-view`](https://github.com/termux/termux-app) | Widget render terminal & input keyboard | GPLv3 |
| Claude Code CLI (`@anthropic-ai/claude-code`) | Pola agen: tool-use, slash commands, sesi, izin per-aksi | Acuan desain UX, bukan kode |
| [thejaustin/termux-ai-app](https://github.com/thejaustin/termux-ai-app) | Contoh integrasi Claude Code di terminal Android | Cek lisensi sebelum adopsi |
| [ferrumclaudepilgrim/claude-code-android](https://github.com/ferrumclaudepilgrim/claude-code-android) | Cara menjalankan Claude Code via Termux/AVF tanpa root | Acuan |
| [MannanSaood/termi](https://github.com/MannanSaood/termi) | Terminal Compose + jembatan SAF-VFS untuk batasan storage Android | Acuan pola SAF |
| [Anthropic SDK / Messages API](https://docs.anthropic.com) | Streaming (SSE), tool use, prompt caching | Resmi |

> Catatan model: gunakan model Claude terbaru saat implementasi (mis.
> `claude-opus-4-8`, `claude-sonnet-4-6`, `claude-haiku-4-5`). Buat daftar model
> dapat dikonfigurasi, jangan hardcode satu saja.

---

## 5. Roadmap Bertahap

### Fase 0 — Bisa di-build & diinstall (BLOCKER) — prioritas tertinggi
Tujuan: `./gradlew assembleDebug` menghasilkan APK yang bisa dipasang.
- [ ] Tambah **Gradle wrapper** (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` + `.properties`, pin versi Gradle yang cocok dengan AGP 8.2.2).
- [ ] Tambah `local.properties` template + dokumentasi SDK path; `.gitignore` Android standar.
- [ ] Lengkapi resource yang hilang: `res/values/strings.xml`, `colors.xml`, ikon launcher final, `themes`/`styles` konsisten.
- [ ] Audit `AndroidManifest`: **hapus `MANAGE_EXTERNAL_STORAGE`** dan izin storage legacy yang tidak dipakai (G7).
- [ ] Build debug APK lokal + smoke test di emulator/device.
- **Deliverable:** APK debug terpasang & terbuka.

### Fase 1 — Fondasi kualitas & keamanan
- [ ] Perbaiki bug: rename `isClauldLoading`→`isClaudeLoading`, kirim **model terpilih** ke `ClaudeRequest` (saat ini default), perbaiki batas `historyIndex`.
- [ ] Simpan API key di **EncryptedSharedPreferences / Android Keystore** (G5).
- [ ] Tambah unit test (AnsiParser, TerminalManager, parsing model) + 1 UI test asap.
- [ ] **CI GitHub Actions**: `assembleDebug` + `test` + lint pada setiap PR.
- [ ] ProGuard/R8 rules nyata untuk build release.
- **Deliverable:** CI hijau, kredensial aman, test dasar.

### Fase 2 — Terminal nyata (PTY)
Tujuan: mengganti terminal simulasi (G2) dengan shell interaktif sungguhan.
- [ ] Integrasi **PTY**: adopsi `terminal-emulator`/`terminal-view` Termux **atau**
      JNI minimal `forkpty()` + native helper (putuskan sesuai lisensi).
- [ ] Render output PTY di Compose (bisa via `AndroidView` membungkus `TerminalView`, atau renderer Compose memakai `AnsiParser` yang sudah ada).
- [ ] Input keyboard: tombol khusus (Esc, Tab, Ctrl, panah, pipe) ala Termux.
- [ ] Workspace di direktori privat app + integrasi **SAF** untuk akses folder user (pola dari `termi`).
- **Deliverable:** shell interaktif (vim/top/git berjalan), bukan command palsu.

### Fase 3 — Agen "Claude Code" (tool use)
Tujuan: menjadikan Claude benar-benar agentic (G3).
- [ ] Tambah **tool calling** ke `ClaudeApiService`: definisi tool `read_file`, `write_file`, `edit_file`, `run_command`, `list_dir`, `grep`.
- [ ] Loop agen di ViewModel: kirim tool → terima `tool_use` → eksekusi terhadap workspace/PTY → kirim `tool_result` → ulangi sampai selesai.
- [ ] **Sistem izin**: konfirmasi user sebelum tulis file / jalankan perintah (UX dari Claude Code), dengan mode allow/deny/always.
- [ ] Slash commands sederhana (`/clear`, `/model`, `/help`, `/cwd`).
- **Deliverable:** "buat file X", "perbaiki bug ini", "jalankan test" bekerja end-to-end.

### Fase 4 — Streaming & UX
- [ ] **Streaming SSE** untuk respons Claude (G4) — token tampil real-time.
- [ ] Render markdown (code block, syntax highlight) di panel chat.
- [ ] **Prompt caching** untuk hemat token pada konteks panjang.
- [ ] Indikator token/biaya, riwayat sesi persisten.
- [ ] Foreground service agar tugas panjang tidak terbunuh OS.
- **Deliverable:** pengalaman setara "Claude Code di HP".

### Fase 5 — Distribusi & instalasi
- [ ] **Signing config** rilis (keystore di GitHub Secrets, jangan di-commit).
- [ ] CI build **release APK** + bundel **AAB**; lampirkan ke **GitHub Releases** otomatis pada tag.
- [ ] (Opsional) **F-Droid**: metadata + reproducible build (lebih cocok bila Path A/GPLv3).
- [ ] Dokumentasi instalasi end-user: download APK rilis → install → isi API key → mulai.
- [ ] Privacy policy & daftar izin minimal (syarat bila ke Play Store).
- **Deliverable:** APK rilis tertandatangani yang bisa diunduh & dipasang siapa pun.

---

## 6. Urutan Prioritas & Estimasi Kasar

| Fase | Nilai | Usaha | Catatan |
|------|-------|-------|---------|
| 0 — Build & install | 🔴 Wajib | Kecil | Lakukan **pertama**; tanpa ini tidak ada yang bisa dipakai |
| 1 — Kualitas & keamanan | 🟠 Tinggi | Kecil–Sedang | Bisa paralel dgn Fase 0 |
| 2 — Terminal PTY | 🟠 Tinggi | **Besar** | Inti "terminal sungguhan"; tentukan lisensi dulu |
| 3 — Agen tool-use | 🟢 Tinggi | Sedang–Besar | Inti "Claude Code" |
| 4 — Streaming & UX | 🟢 Sedang | Sedang | Polish penting |
| 5 — Distribusi | 🟠 Tinggi | Sedang | Agar "bisa diinstall" oleh publik |

**Jalur tercepat menuju "usable & installable":** Fase 0 → 1 → 5 (debug→release)
memberi APK yang dapat dipasang dengan fitur saat ini. **Jalur menuju produk
sebenarnya:** lanjut Fase 2 → 3 → 4.

---

## 7. Risiko & Mitigasi

- **Lisensi Termux (GPLv3):** menyalin kode Termux mewajibkan seluruh repo GPLv3.
  *Mitigasi:* di Path B, batasi pada komponen permisif / tulis PTY sendiri; putuskan sebelum Fase 2.
- **Batasan eksekusi Android 10+ (W^X):** sejak Android 10, biner di `app data`
  tidak boleh dieksekusi langsung (kecuali via `app_native_library_dir`/proot).
  *Mitigasi:* ikuti pola bootstrap/proot Termux; itulah alasan PTY tidak sepele.
- **Keamanan eksekusi agen:** agen bisa menjalankan perintah destruktif.
  *Mitigasi:* sistem izin per-aksi + sandbox workspace (Fase 3).
- **Kebijakan Play Store:** `MANAGE_EXTERNAL_STORAGE` & eksekusi kode sering ditolak.
  *Mitigasi:* izin minimal; distribusi via GitHub Releases / F-Droid sebagai jalur utama.
- **Biaya API:** loop agen boros token. *Mitigasi:* prompt caching, batas konteks, indikator biaya.

---

## 8. Langkah Berikutnya yang Konkret

1. **Fase 0 item #1** — generate Gradle wrapper & `.gitignore`, pastikan build hijau. (Saya bisa langsung kerjakan ini setelah Anda setuju.)
2. Putuskan **Path A vs B vs C** (rekomendasi: B → C) dan **lisensi** (MIT/Apache vs GPLv3).
3. Konfirmasi target minSdk (sekarang 26) — PTY/bootstrap punya implikasi di versi lama.

> Setelah arah disetujui, saya lanjut mengeksekusi Fase 0 (membuat proyek
> benar-benar bisa di-build & menghasilkan APK debug), lalu naik per fase.
