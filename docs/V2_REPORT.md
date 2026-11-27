# NOVA PILL V2: laporan akhir

Laporan ini mengikuti bagian 15 dari prompt perbaikan V2. Satu hal harus jelas sejak awal: **lapisan Android (Compose, overlay, kamera, MediaProjection, notifikasi) sudah berhasil dikompilasi dan di-build di GitHub Actions (APK debug jadi, unit test lulus), tetapi belum dijalankan di perangkat.** Build hijau membuktikan kode bisa dikompilasi dan dikemas, bukan bahwa fiturnya bekerja di HP. Yang dijalankan sungguhan: 188 unit test logika murni (lokal) serta build dan unit test Gradle di CI. Hasilnya dikirim sebagai ZIP dan sudah ada di repo (commit `b9db0a0`, `1b46d58`, `bda8a5a`).

## 1. RINGKASAN PERBAIKAN V2

- Bug flashlight diperbaiki di akar: status "On" tidak lagi ditebak, hanya mengikuti laporan layanan kamera.
- Panel idle menampilkan jam, tanggal, cuaca (Open-Meteo), dan baterai yang nyata, dengan status memuat/offline/basi.
- Panel cepat dua halaman dengan 12 tile yang bisa disembunyikan satu per satu; tile yang tidak bisa jalan memberi tahu.
- Notifikasi menampilkan nama aplikasi, pengirim, dan pratinjau satu baris; tiga tingkat privasi; balas cepat hanya bila ada `RemoteInput`.
- Perekam layar nyata berbasis MediaProjection, disimpan sebagai MP4 di `Movies/NOVA-PILL`.
- Freeform untuk aplikasi lain lewat API publik (bergantung perangkat) dan kalkulator mengambang internal.
- Pengaturan baru: Quick panel, Screen recorder, Floating windows, plus opsi tambahan di bagian lama.
- Layar intro memuat penanda "V2", kartu What's new, versi 2.0.0 (code 2), skema pengaturan 2 dengan migrasi dari 1.
- README bergambar (9 ilustrasi desain), README.id, CHANGELOG, checklist uji manual.

## 2. AUDIT SEBELUM PERBAIKAN

| Area | Keadaan di 1.0 | Masalah |
|---|---|---|
| Flashlight | Kartu tools ringkas menulis "On" tetap; panel memakai boolean tersimpan | Status bisa salah dan tidak sinkron dengan hardware |
| Idle | Hanya jam yang digambar | Cuaca dan baterai ada di desain tetapi belum terhubung |
| Panel cepat | Kartu tools dengan aksi terbatas; halaman "More" hanya ada di spesifikasi | Sebagian tampilan belum terhubung ke fungsi |
| Notifikasi | Peek memakai ringkasan sederhana | Pengirim dan isi tidak ditampilkan terstruktur; tidak ada balas |
| Perekam | Hanya sinyal OS dan timer manual | Tidak ada perekaman layar nyata |
| Shortcut | Membuka aplikasi seperti biasa | Tidak ada freeform maupun jendela mengambang |
| Pengaturan | Tidak ada bagian untuk fitur di atas | Tidak ada kontrol bagi pengguna |

## 3. DAFTAR FILE YANG DIUBAH

Path relatif terhadap `app/src/main/java/app/novapill/android/` kecuali disebut lain.

**Logika murni (diuji unit test)**
- `core/TorchTracker.kt` (baru): status senter per kamera (no flash, unknown, off, on, busy), pending, error.
- `core/RecorderMachine.kt` (baru): state machine perekam (Idle, Preparing, Recording, Stopping, Saved, Error).
- `core/CalcEngine.kt` (baru): mesin kalkulator untuk jendela mengambang.
- `core/FloatingGeometry.kt` (baru): geometri jendela (geser, ubah ukuran, batas layar).
- `core/StorageFormat.kt` (baru): format ukuran dan durasi untuk kartu perekam.
- `settings/NovaSettings.kt`: skema 2, field V2, migrasi dari skema 1.
- `notif/NotificationText.kt`: teks pengirim dan isi menurut tingkat privasi; kunci dedup.
- `overlay/IslandLayout.kt`: pelebaran idle di sekitar cutout; tinggi peek.

**Senter, status perangkat, aplikasi sistem**
- `util/Flashlight.kt`: callback torch per kamera, konfirmasi 1,8 detik, kekuatan (API 33).
- `util/DeviceStatus.kt` (baru): status jaringan untuk tile Internet.
- `util/SystemApps.kt` (baru): shortcut aplikasi sistem (Alarms, Calendar, Phone, Camera, Settings).
- `util/Freeform.kt` (baru): peluncuran dengan batas freeform lewat API publik, dengan hasil yang jujur.
- `util/RecordIndicator.kt`: sumber baru "perekam NOVA PILL".

**Notifikasi**
- `notif/NotificationHub.kt`: peek berisi pengirim, isi, kemampuan balas; dedup; penanganan penghapusan.
- `notif/ReplyRegistry.kt` (baru): daftar aksi balas hanya di memori.
- `notif/QuickReplyActivity.kt` (baru): input balas kecil yang mengirim lewat aksi notifikasi.

**Perekam**
- `record/RecorderService.kt` (baru): foreground service, MediaProjection, MediaRecorder, VirtualDisplay, MediaStore.
- `record/RecordConsentActivity.kt` (baru): persetujuan layar (dan izin mikrofon bila perlu).
- `record/RecorderHub.kt` (baru): jembatan status perekam ke island.
- `service/Channels.kt`: channel notifikasi rekaman.
- `service/IslandService.kt`: penyesuaian siklus hidup layanan untuk komponen V2.

**Overlay dan UI**
- `overlay/IslandCoordinator.kt`: integrasi semua sumber V2 (senter, perekam, balas, jendela, cuaca, jaringan).
- `overlay/IslandModels.kt`: `ToolsUi`, `RecordUi`, `RecorderUi`, aksi baru.
- `overlay/IslandWindowController.kt`: penyesuaian jendela untuk ukuran panel baru.
- `overlay/OverlayOwner.kt` (baru): pemilik lifecycle bersama untuk jendela overlay Compose.
- `overlay/FloatingManager.kt` (baru): jendela mengambang internal (geser, ubah ukuran, tutup).
- `overlay/ui/QuickPanel.kt` (baru), `Panels.kt`, `UtilityPanels.kt`, `IslandRoot.kt`: panel cepat dua halaman, idle, peek dengan Balas, kartu perekam.
- `overlay/ui/FloatingCalculator.kt` (baru): UI kalkulator mengambang.
- `ui/components/NovaIcon.kt`: ikon vektor baru (Wi-Fi, kamera, kalkulator, stop, kirim, jendela, balas).

**Layar aplikasi, manifest, build**
- `ui/screens/SettingsV2.kt` (baru), `SettingsHub.kt`, `Routes.kt`, `SettingsMore.kt`, `SettingsNotifMusic.kt`, `Preview.kt`, `Dashboard.kt`, `Onboarding.kt` (penanda V2), `WhatsNew.kt` (baru).
- `NovaGraph.kt`: singleton baru (perekam, jendela mengambang, status perangkat, senter).
- `res/values/strings.xml`: teks pemberitahuan baru.
- `AndroidManifest.xml`: izin dan komponen baru (layanan perekam bertipe `mediaProjection|microphone`, activity persetujuan dan balas).
- `app/build.gradle.kts`: versionCode 2, versionName 2.0.0.

**Tes (di `app/src/test/`)**: `TorchTrackerTest`, `RecorderMachineTest`, `CalcEngineTest`, `FloatingGeometryTest`, `StorageFormatTest`, `NotificationTextTest`, `NovaSettingsCodecTest`, `IslandLayoutTest`.

**Dokumen**: `README.md`, `README.id.md`, `CHANGELOG.md`, `docs/MANUAL_TEST_CHECKLIST.md`, `docs/V2_REPORT.md`, `docs/images/**`.

## 4. FLASHLIGHT

- **Akar bug:** kartu tools ringkas menulis "On" tetap dan panel memakai boolean tersimpan.
- **Kontrol:** permintaan dikirim ke `CameraManager` untuk kamera yang punya flash; status berubah hanya saat callback `TorchCallback` melaporkan.
- **Sinkronisasi:** callback terdaftar hanya selama layanan island berjalan dan dilepas saat berhenti; `resync()` membaca ulang saat perlu.
- **Kegagalan:** tidak ada laporan dalam 1,8 detik menjadi "Not confirmed"; kamera dipakai aplikasi lain menjadi "In use"; tidak ada flash menjadi "No flash".
- **Pil ringkas:** menulis "On" hanya bila `flashOn` terkonfirmasi; selain itu "Tools".
- **Kekuatan:** slider pada Android 13+ bila hardware mendukung.
- **Status:** IMPLEMENTED, DEVICE TEST PENDING (logika tracker: unit test lulus).

## 5. IDLE STATE

- **Jam dan tanggal:** waktu sistem, mengikuti pengaturan 12/24 jam.
- **Cuaca:** Open-Meteo lewat HTTP; lokasi perkiraan (izin opsional). Status: memuat ("Updating"), tidak ada ("No weather"), offline, basi; sumber dan waktu pembaruan ditampilkan.
- **Baterai:** `BatteryMonitor` dari sistem Android; ikon dan persen.
- **Tidak tersedia:** panel menampilkan status eksplisit, bukan angka karangan.
- **Cutout:** pil melebar di sekeliling cutout kamera.
- **Mode:** Clock, Clock and battery, Full (bawaan).

## 6. NOTIFICATION

- **Sumber:** `NotificationListenerService` (akses notifikasi). Nama aplikasi dari `PackageManager`; pengirim dan isi dari data notifikasi (gaya pesan bila ada, judul dan teks bila tidak).
- **Privasi:** tiga tingkat (hanya aplikasi; aplikasi + pengirim/judul; ditambah satu baris isi maks. 120 karakter). Notifikasi rahasia atau saat terkunci selalu hanya nama aplikasi.
- **Penyimpanan:** hanya di memori selama peek; tidak ditulis ke disk, tidak di-log, tidak diunggah.
- **Dedup dan hapus:** notifikasi yang sama diposting ulang tidak menumpuk; penghapusan menghilangkan peek.
- **Balas:** tombol hanya bila notifikasi punya `RemoteInput`; terkirim lewat aksi milik aplikasi itu.
- **Status:** IMPLEMENTED, DEVICE TEST PENDING; balas: PARTIALLY IMPLEMENTED (hanya bila aplikasi menyediakan).

## 7. SCREEN RECORDER

- **API:** `MediaProjectionManager.createScreenCaptureIntent()`, `MediaProjection`, `VirtualDisplay`, `MediaRecorder`, `MediaStore`.
- **Izin dan layanan:** persetujuan layar setiap kali; foreground service bertipe `mediaProjection` (+ `microphone` hanya bila suara aktif dan `RECORD_AUDIO` diberikan); `POST_NOTIFICATIONS` di Android 13+.
- **Proses:** persetujuan, izin mikrofon bila perlu, hitung mundur (0/3/5 detik), siapkan, rekam, hentikan, finalisasi file. Status Idle, Preparing, Recording, Stopping, Saved, Error; timer hanya berjalan saat Recording.
- **Hasil:** MP4 di `Movies/NOVA-PILL` lewat MediaStore (Android 10+ ditandai pending sampai selesai; file kosong atau gagal dihapus). Android 8-9: folder Movies milik aplikasi, dengan pemberitahuan.
- **Opsi:** 720p/1080p, 30/60 fps, mikrofon, hitung mundur, sembunyikan pil saat merekam.
- **Tidak didukung:** audio internal perangkat.
- **Status:** IMPLEMENTED, DEVICE TEST PENDING.

## 8. SHORTCUT / FREEFORM

- **Freeform eksternal:** shortcut meminta batas jendela freeform lewat `ActivityOptions` (API publik saja, tanpa API tersembunyi). Hanya berlaku pada perangkat yang mendukung dan mengaktifkan freeform. Bila tidak, aplikasi dibuka biasa dan pil memberi tahu. Status: PARTIALLY IMPLEMENTED.
- **Jendela mengambang internal:** kalkulator yang digambar NOVA PILL sendiri sebagai jendela overlay (geser, ubah ukuran, tombol X). Ini bukan jendela aplikasi lain dan tidak berpura-pura begitu. Status: IMPLEMENTED, DEVICE TEST PENDING.
- Apakah perangkat target mendukung freeform eksternal: **belum diketahui**; perlu uji di perangkat.

## 9. IDE TAMBAHAN

- Tile bisa disembunyikan satu per satu (supaya panel ringkas tetap ringkas).
- Kalkulator mengambang internal sebagai jalan keluar jujur bila freeform tidak ada.
- Hitung mundur sebelum merekam dan opsi menyembunyikan pil saat merekam.
- Slider kekuatan senter (bila hardware mendukung).
- Tiga tingkat privasi pratinjau notifikasi, plus aturan terkunci/rahasia.
- Kartu What's new satu kali dan penanda V2 di intro.

## 10. PENGUJIAN

**Dijalankan:**
- 188 unit test logika murni (kompilasi `kotlinc` 2.0.21 + JUnit lewat skrip lokal): hasil OK. Cakupan: codec pengaturan dan migrasi, state machine, layout, torch tracker, state machine perekam, kalkulator, geometri jendela, teks/filter/dedup notifikasi, parser lirik, timer, JSON, kode cuaca, rute.
- Pemeriksaan statis lokal: impor dan nama simbol proyek, nama resource (`R.string.*` semua ada), kelas komponen manifest, deklarasi top-level ganda (tidak ada), pemanggilan konstruktor.
- Percobaan kompilasi seluruh sumber dengan `kotlinc` tanpa SDK Android: gagal secara wajar (ribuan "unresolved reference" untuk tipe Android/Compose), tidak bisa dipakai sebagai bukti.
- Build GitHub Actions (run #3, commit `bda8a5a`, 2026-11-27): `:app:assembleDebug` sukses (APK debug sekitar 10 MB), `:app:testDebugUnitTest` sukses, `:app:lintDebug` jalan dan laporannya terunggah. Kompilator Kotlin memberi 3 peringatan (satu kondisi yang selalu true, dua API yang ditandai deprecated) dan tidak ada error.
- Dua run sebelumnya gagal dan sudah diperbaiki: run #1 gagal di langkah setup SDK (paket `tools` sudah tidak ada di sdkmanager; workflow kini memasang `platform-tools` saja). Run #2 gagal kompilasi dengan 4 error "Platform declaration clash" di `Preview.kt` (properti `volume` dan `toolsPage` bentrok dengan fungsi `setVolume` dan `setToolsPage`; diganti nama menjadi `volumeFraction` dan `toolsPageIndex`).

**Tidak dijalankan:**
- Pengujian di perangkat fisik atau emulator.
- Review independen oleh agen lain: dua percobaan gagal karena batas pemakaian.

## 11. KETERBATASAN

- Build hijau tidak menjamin perilaku di HP: semua fitur yang butuh perangkat belum dijalankan dan perlu diuji dengan `docs/MANUAL_TEST_CHECKLIST.md`.
- Overlay tidak bisa tampil di atas sebagian layar sistem; sebagian produsen membatasi overlay dan layanan latar.
- Konten yang diblokir untuk tangkapan layar terekam hitam.
- Audio internal tidak didukung.
- Freeform eksternal bergantung perangkat.
- Balas hanya bila notifikasi menyediakan `RemoteInput`.
- Android 8-9: rekaman tidak masuk folder galeri bersama.

## 12. STATUS AKHIR PER FITUR

| Fitur | Status |
|---|---|
| State machine island dan prioritas kartu | DONE & VERIFIED (logika saja, unit test) |
| Codec pengaturan dan migrasi 1 ke 2 | DONE & VERIFIED (logika saja, unit test) |
| Flashlight mengikuti status hardware | IMPLEMENTED, DEVICE TEST PENDING |
| Slider kekuatan senter | IMPLEMENTED, DEVICE TEST PENDING |
| Idle: jam, tanggal, baterai | IMPLEMENTED, DEVICE TEST PENDING |
| Idle: cuaca | IMPLEMENTED, DEVICE TEST PENDING |
| Panel cepat 2 halaman, 12 tile | IMPLEMENTED, DEVICE TEST PENDING |
| Notifikasi: aplikasi, pengirim, pratinjau | IMPLEMENTED, DEVICE TEST PENDING |
| Balas dari peek | PARTIALLY IMPLEMENTED |
| Perekam layar (video) | IMPLEMENTED, DEVICE TEST PENDING |
| Mikrofon perekam | IMPLEMENTED, DEVICE TEST PENDING |
| Audio internal perekam | NOT SUPPORTED |
| Freeform untuk aplikasi lain | PARTIALLY IMPLEMENTED |
| Kalkulator mengambang internal | IMPLEMENTED, DEVICE TEST PENDING |
| Layar pengaturan baru | IMPLEMENTED, DEVICE TEST PENDING |
| Penanda V2 di intro dan What's new | IMPLEMENTED, DEVICE TEST PENDING |
| Fitur lama dari 1.0 | IMPLEMENTED, DEVICE TEST PENDING (uji ulang) |
| Build GitHub Actions 2.0.0 (assembleDebug + unit test) | DONE & VERIFIED (run #3, commit `bda8a5a`) |

Tidak ada fitur yang diberi label DONE & VERIFIED hanya karena tampilannya atau karena berhasil dikompilasi; label itu di tabel ini hanya dipakai untuk logika yang dijalankan unit test dan untuk build di CI.
