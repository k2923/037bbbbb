# โครงสร้างปลั๊กอิน

## ขอบเขตของแต่ละส่วน

| ส่วน | หน้าที่ |
| --- | --- |
| `*Provider.kt` | เชื่อม API ของ Cloudstream: หน้าแรก ค้นหา รายละเอียด ตอน และ callback วิดีโอ |
| `*Parser.kt` | แปลง HTML/JSON เป็นข้อมูล โดยไม่เรียกเครือข่าย เหมาะสำหรับทดสอบด้วย samples |
| `*Resolver.kt` / `*Player.kt` | อ่านเส้นทางตัวเล่นเฉพาะเว็บ; resolver ที่มีหลายขั้นตอนรับฟังก์ชัน HTTP เพื่อทดสอบโดยไม่ใช้เซิร์ฟเวอร์จริง |
| `SiteCategories.kt` / `AnimeCategories.kt` | URL/ชื่อหมวดที่เว็บแสดง และชุดหมวดหลักสำหรับหน้าแรก |
| `shared/.../CatalogPagination.kt` | สร้าง URL แบบ path/query โดยคง raw query และตรวจลิงก์ถัดไปที่ไปหน้าอื่นจริง |
| `shared/.../CdnFailoverInterceptor.kt` | ลอง CDN ที่ตัวเล่นประกาศ แล้วเก็บ URL เดิมไว้สำรอง; จำ origin ที่ตอบสำเร็จภายใน instance เดียว |
| `shared/.../BrowserSession.kt` | ใช้คุกกี้ที่มีอยู่แบบ passive และคง User-Agent เฉพาะตัวเล่น ไม่มีระบบแก้ challenge หรือ retry |
| `shared/.../PlaybackFallback.kt` | นับเฉพาะ callback วิดีโอจริง รักษา primary เมื่อ backup เสีย และส่งต่อ cancellation |
| `shared/.../ProviderBrowser.kt`, `ProviderWebView.kt` | เว็บสำรองจากหน้าตอน พร้อมควบคุมด้วยรีโมตและ cleanup ตาม lifecycle |

## ข้อกำหนดที่ต้องรักษา

- URL ในผลค้นหา/ตอนเป็น URL หน้าเว็บ; callback วิดีโอต้องเป็น media จริง ห้ามส่ง HTML/iframe เป็นไฟล์วิดีโอ
- ข้อมูลรายการใช้ cache ของแอป; URL ตัวเล่น/session/token โหลดใหม่เมื่อกดเล่น
- แต่ละ provider ที่เปิดใช้งานจำกัดคำขอหมวดพร้อมกันสามคำขอ คงหมวดหลักขนาดเล็ก
- ตัวอ่านเฉพาะเว็บเลือก container และตัวเล่น content เท่านั้น ไม่รวม sidebar/featured/ad เป็นผลค้นหาหรือวิดีโอ
- ตอนซ้ำรวมตาม season/episode (หรือ episode สำหรับเว็บที่ไม่มี season) โดยคงทุก player ที่ไม่ซ้ำ
- การสลับ CDN คง Range/Referer และ signature ของ URL เดิม ยกเลิกแล้วต้องหยุด ลิงก์ playlist ที่ไม่ต้องสลับส่งผ่านตามเดิม
- เปลี่ยนรูปแบบ CDN ผ่านตัวอ่านของเว็บนั้น ไม่ใส่กฎทุกโฮสต์ลงใน helper กลาง
- Ruka ใช้ resolver วิดีโอเดิมที่ผู้ใช้ยืนยันว่าเล่นได้
- Anime-Waku/AnimeSeven ไม่ลงทะเบียน; Movie2Free.co/FWIP ไม่อยู่ใน publication settings
  โค้ดและ samples ที่เก็บไว้ไม่ถือว่า provider เหล่านี้กลับมาใช้งานได้

## แพ็กเกจและการตรวจ

เจ็ดแพ็กเกจที่เผยแพร่คือ ExampleProvider, TwentyFiveHD, MovieDoFree, Movie2FreeHD, AHDZero, TwentyFourHD, ThaiAnime
ThaiAnime ลงทะเบียน AnimeRuka/AnimeYour/Hit-Anime/Indy-Anime/Neko-Miku/Suba-Anime รวมกับแพ็กเกจอื่นเป็นสิบสองเว็บไซต์
มีตัวเลือกสำรองจาก samples อีกห้าตัวเลือก (ThaiAnime สาม, 24HD หนึ่ง, AHDZero หนึ่ง) รวมสิบเจ็ดตัวเลือกในแอป
Indy/Hit/Your ใช้ TeamAnimeSite ระบุ route/selector/alias ของแต่ละเว็บ และใช้ TeamAnimeParser/TeamAnimePlayer/TeamAnimeProvider ร่วมกัน
Site enum เดิมยังดูแล Neko/Ruka/Suba และแหล่งที่ปิดไว้ โดยคงตัวอ่านวิดีโอเดิม
TeamAnimePlayer อ่านเฉพาะปุ่มจริง/dropdown/iframe ใน #mpPlayer; จัดสำรอง MP4/HLS ที่สังเกตได้ไว้ก่อน
MP4/HLS ต้องตรง host/path/ID ของ embed และถูกใช้เป็น source ของ Clappr; callback ระบุ VIDEO หรือ M3U8 ตามรูปแบบจริง
HLS เก็บ URL master พร้อม port/query/Referer ไม่ดึง session/key มา cache ใน plugin; การอ่าน playlist และ AES-128 เป็นหน้าที่ native player
ใช้ extractor ต่อเมื่อรูปแบบเฉพาะไม่รองรับ และรักษาการยกเลิก/ผลจากตัวเล่นที่ทำงานได้
Hit อาจ redirect หน้าตอนใน browser แม้ HTTP HTML มีตัวเล่น จึงเปิด player URL ที่ประกาศไว้เป็น web fallback เมื่ออ่านพบแล้ว
โค้ด shared ถูกคอมไพล์เข้าแต่ละแพ็กเกจโดย root build script; หากเปลี่ยน shared ต้องเพิ่ม version ของทุก consumer ที่สร้างใหม่

`ExampleProvider/src/test/.../com/providers/network/` ตรวจ helper ร่วม รวม response ownership, cancellation และ Android UI
แต่ละ module ตรวจ parser/resolver ของตัวเองจาก fixture ใน `samples/`; ข้อมูล fixture ต้องคง URL ต้นฉบับ ไม่สร้างเส้นทางจากชื่อไฟล์
คำสั่งสร้างและทดสอบแพ็กเกจที่เปิดใช้งานอยู่ใน README ส่วนแพ็กเกจที่ปิดทดสอบได้ผ่าน Gradle init script ที่ include สอง module ชั่วคราว
ผล unit test/HTML parser ไม่ยืนยันการเล่นจริงบน Mi Box หรือการเข้าถึงจากเครือข่ายผู้ใช้

## Catalog snapshots ที่ผู้ใช้เลือกเอง

เพิ่มแหล่งสำรองที่ระบุในชื่ออย่างชัดเจนสำหรับ 24HD/AHDZero/Neko/Ruka/Suba ตามคำขอใช้ samples
นี่เป็นการขยายสัญญาเดิมที่ใช้ samples เฉพาะทดสอบ: โหมดสำรองอ่าน catalog/รายละเอียด/หน้าตอนเก่าได้
ส่วนแหล่งสดใช้พฤติกรรมเดิม ตัวเล่นภายนอกและ session/media ต้องโหลดสดทั้งสองโหมด
`SampleArchive` ใช้ URL ต้นฉบับเป็นกุญแจ ไม่ fallback โดยจับคู่ชื่อเรื่องหรือส่ง HTML เป็นวิดีโอ
Generated `BundledSamples.kt` แยกตามแพ็กเกจและตรวจความตรงกันผ่านสคริปต์ก่อนเผยแพร่
