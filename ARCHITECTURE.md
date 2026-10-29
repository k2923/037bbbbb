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
| `shared/.../NativePlayback.kt` | นับเฉพาะ callback วิดีโอจริง รักษา primary เมื่อ backup เสีย และส่งต่อ cancellation |

## ข้อกำหนดที่ต้องรักษา

- เมื่อดึงลิงก์เล่นไม่ได้ให้แจ้งสาเหตุในแอป ไม่เปิดหน้าต่างเว็บหรือ ACTION_VIEW ไปเบราว์เซอร์ ตามคำขอล่าสุดที่ยกเลิกพฤติกรรมเว็บสำรองเดิม
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

แปดแพ็กเกจที่เผยแพร่คือ ExampleProvider, TwentyFiveHD, MovieDoFree, Movie2FreeHD, AHDZero, TwentyFourHD, ThaiAnime, GoSeries4K
ThaiAnime ลงทะเบียน AnimeRuka/AnimeYour/Hit-Anime/Indy-Anime/Neko-Miku/Suba-Anime รวมกับแพ็กเกจอื่นและ GoSeries4K เป็นสิบสามเว็บไซต์
มีตัวเลือกสำรองจาก samples อีกห้าตัวเลือก (ThaiAnime สาม, 24HD หนึ่ง, AHDZero หนึ่ง) รวมสิบแปดตัวเลือกในแอป
Indy/Hit/Your ใช้ TeamAnimeSite ระบุ route/selector/alias ของแต่ละเว็บ และใช้ TeamAnimeParser/TeamAnimePlayer/TeamAnimeProvider ร่วมกัน
Site enum เดิมยังดูแล Neko/Ruka/Suba และแหล่งที่ปิดไว้ โดยคงตัวอ่านวิดีโอเดิม
TeamAnimePlayer อ่านเฉพาะปุ่มจริง/dropdown/iframe ใน #mpPlayer; จัดสำรอง MP4/HLS ที่สังเกตได้ไว้ก่อน
MP4/HLS ต้องตรง host/path/ID ของ embed และถูกใช้เป็น source ของ Clappr; callback ระบุ VIDEO หรือ M3U8 ตามรูปแบบจริง
HLS เก็บ URL master พร้อม port/query/Referer ไม่ดึง session/key มา cache ใน plugin; การอ่าน playlist และ AES-128 เป็นหน้าที่ native player
ใช้ extractor ต่อเมื่อรูปแบบเฉพาะไม่รองรับ และรักษาการยกเลิก/ผลจากตัวเล่นที่ทำงานได้
Hit อ่านตัวเล่นที่เว็บประกาศผ่าน native resolver; เมื่อไม่มีลิงก์วิดีโอไม่เปิด player URL เป็นหน้าต่างเว็บ
โค้ด shared ถูกคอมไพล์เข้าแต่ละแพ็กเกจโดย root build script; หากเปลี่ยน shared ต้องเพิ่ม version ของทุก consumer ที่สร้างใหม่

`ExampleProvider/src/test/.../com/providers/network/` ตรวจ helper ร่วม รวม response ownership, cancellation และผล native playback ที่ไม่เปิดเว็บ
แต่ละ module ตรวจ parser/resolver ของตัวเองจาก fixture ใน `samples/`; ข้อมูล fixture ต้องคง URL ต้นฉบับ ไม่สร้างเส้นทางจากชื่อไฟล์
คำสั่งสร้างและทดสอบแพ็กเกจที่เปิดใช้งานอยู่ใน README ส่วนแพ็กเกจที่ปิดทดสอบได้ผ่าน Gradle init script ที่ include สอง module ชั่วคราว
ผล unit test/HTML parser ไม่ยืนยันการเล่นจริงบน Mi Box หรือการเข้าถึงจากเครือข่ายผู้ใช้

## Catalog snapshots ที่ผู้ใช้เลือกเอง

เพิ่มแหล่งสำรองที่ระบุในชื่ออย่างชัดเจนสำหรับ 24HD/AHDZero/Neko/Ruka/Suba ตามคำขอใช้ samples
นี่เป็นการขยายสัญญาเดิมที่ใช้ samples เฉพาะทดสอบ: โหมดสำรองอ่าน catalog/รายละเอียด/หน้าตอนเก่าได้
ส่วนแหล่งสดใช้พฤติกรรมเดิม ตัวเล่นภายนอกและ session/media ต้องโหลดสดทั้งสองโหมด
`SampleArchive` ใช้ URL ต้นฉบับเป็นกุญแจ ไม่ fallback โดยจับคู่ชื่อเรื่องหรือส่ง HTML เป็นวิดีโอ
Generated `BundledSamples.kt` แยกตามแพ็กเกจและตรวจความตรงกันผ่านสคริปต์ก่อนเผยแพร่

## การโหลดรายละเอียด 25-HD

เมื่อ embed ระบุ `type=movie` ใช้ข้อมูลหนังจากหน้าเว็บหลักได้ทันที แล้วโหลด player/session ใหม่ตอนเล่น
ซีรีส์และรายการที่ยังไม่ทราบชนิดต้องอ่าน bootstrap จริงเพื่อสร้างตอน เมื่อ HTML ใช้ไม่ได้
ลอง `/api/embed/bootstrap` ของ origin เดิมหนึ่งครั้งด้วย id/type เท่านั้นและรักษา cancellation
API ต้องตอบสำเร็จพร้อม content; กรณีซีรีส์ห้ามใช้ movie แทนหรือสร้างตอนที่ไม่มีลิงก์
ไม่มีการเก็บ bootstrap/token เพื่อใช้เล่นครั้งถัดไป และไม่มีตัวแก้ challenge เพิ่มใน BrowserSession

## Catalog สด 24HD และแหล่งหลัก

แหล่งหลักทั้ง12เว็บไซต์ขอข้อมูลเว็บสดเสมอ; samples เป็นอีก5ตัวเลือกที่ผู้ใช้เลือกเอง ไม่เปลี่ยนเป็น samples อัตโนมัติเมื่อ HTTP403
24HD ใช้ public `/wp-json/wp/v2/posts` ของเว็บเดิม และค้น taxonomy ID ด้วย slug ของหมวดเดิม
เก็บเฉพาะ taxonomy ID; จำนวนหน้าจาก `X-WP-TotalPages`, search/page/category ส่งถึง API จริง
รายละเอียดต้องตรง URL เรื่องที่เลือก; ACF `file`/`file_2` และ `ep_list` เป็น URL ตัวเล่นจริง
ไม่สร้างตอนตามจำนวนที่โฆษณาในชื่อเรื่อง และรวมตัวเล่นสำรองของตอนหมายเลขเดียวกัน
เมื่อ API ผิดพลาดลอง parser HTML เดิมเพื่อให้เครือข่าย/เครื่องที่เข้า HTML ได้ยังใช้ได้ และรักษา cancellation
ตอนกดเล่นขอรายละเอียด/ACF ใหม่ด้วย cacheTime=0 ก่อนโหลดตัวเล่นสด ไม่มีการใช้ media/session จาก fixture
ผล403จาก sandbox ไม่ใช้เป็นเหตุปิดแหล่งหลักหรือเปลี่ยนตัวอ่านที่ทำงานบนเครื่องผู้ใช้

## Regex บน Android

Android ใช้ ICU4C; การ compile regex ผ่าน JDK ไม่ยืนยันว่าใช้ได้บนเครื่องจริง
24HD ต้อง escape ทั้งเครื่องหมายเปิด/ปิดปีกกาในตัวอ่านกฎ CSS เพื่อไม่ให้ object initializer ล้มเหลว
`test_android_regex.py` ตรวจ literal Regex จาก source24HDด้วย native ICU ก่อน build พร้อม testsเดิมที่ตรวจผลแยกโปสเตอร์/ตอน/ตัวเล่น

## Recommendations ในหน้ารายละเอียด

`RelatedTitles.kt` เติม `LoadResponse.recommendations` ให้ทุก provider ที่เปิดใช้งาน ทั้งหนัง/ซีรีส์/อนิเมะและโหมด samples
Cloudstream เป็นผู้แสดงแถวรายการแนะนำ; ปลั๊กอินไม่ได้สร้างหน้าต่างเว็บหรือแก้ UI ของแอป
ลำดับ: related cards ที่เว็บประกาศใน `.yarpp-related` / `.srelacionados` → genre จาก metadata ของเรื่อง → ค้นชื่อเรื่อง/ภาคที่คล้ายกัน
อ่าน genre เฉพาะส่วนรายละเอียดและไม่ใช้เมนูหมวดทั้งเว็บ ปี ภาษา หรือ sidebar เป็นหลักฐานว่าเรื่องอยู่แนวนั้น
24HD ที่อ่าน API ใช้การค้นชื่อผ่าน API เดิมโดยไม่ขอ HTML รายละเอียดซ้ำ
เลือกทำหนึ่งการค้นหาหรือโหลดหมวดเมื่อไม่มี related cards และให้เวลารวมสูงสุด 1500ms; ความผิดพลาดให้รายการว่าง แต่ cancellation ของผู้ใช้ต้องส่งต่อ
ไม่ขอรายละเอียดของทุกเรื่องที่แนะนำ ไม่โหลดตัวเล่น และไม่เพิ่ม cache ของ media/session; ใช้ cache HTTP เดิมของแอป
สูงสุด12เรื่อง ตัด URL เดิม/ซ้ำ/ต่างเว็บ และใช้ SearchResponse ของ provider เดิมเพื่อให้กดไปยังรายละเอียดได้
โหมด samples ค้นจาก sampleCards และใช้หน้าหมวดใน bundledSamples เท่านั้น ไม่มี network enrichment เพิ่ม
ถ้าไม่มีข้อมูลที่เกี่ยวข้องจะไม่ส่งเรื่องสุ่มแทน; ความคล้ายขึ้นกับข้อมูลเว็บและชื่อเรื่อง ไม่ใช่ระบบจัดอันดับของ AniList
AniList ไม่เป็น dependency ของรุ่นนี้; การเพิ่มภายหลังต้องจับคู่ชื่อ/ID กับรายการที่เปิดได้ใน provider จริง

## 24HD wrapper player

บางตอน เช่น Bloodhounds (2023) EP.1 ส่ง iframe ภายใน ongetplay.xyz; ตามเฉพาะ iframe player ที่ยืนยันแล้วบน playermhd.p2phls.xyz และใช้ URL ของ iframe เป็น Referer ของ HLS โดยไม่เปิดหน้าต่างเว็บ ไม่ตาม iframe โฆษณา และไม่เปลี่ยนเลขตอนเมื่อมี redirect

## GoSeries4K

โมดูลแยกใช้ MHTML ของผู้ใช้เป็น regression fixtures เท่านั้น ไม่เพิ่มโหมด samples หรือเปลี่ยน shared
อ่านการ์ดจาก #main .list-movie/.s-grid และตอนจากปุ่ม mp-ep-btn จริง แยก audio group
รวมรหัสตัวเล่นสำรองของตอนเดียวกัน ชื่อเรื่องที่ระบุ EP.1–40 ไม่ใช่รายการตอนที่พร้อมเล่น
Playback payload เก็บ page URL และ episode IDs; ดึง iframe/session ใหม่ทุกครั้ง
ตอนเริ่มต้นใช้ได้เมื่อ #mpPlayer data-post-id ตรง ID ที่เลือกเท่านั้น
ตอนอื่นใช้ JavaScript ของหน้าจริงคลิกปุ่มเดิมใน native WebView สูงสุด8วินาที
ตรวจ ID และ iframe เปลี่ยนจากตอนเริ่มต้นก่อนส่งสัญญาณผลผ่าน request เพื่อไม่พึ่ง callback ตอน network idle
Torbo ขอ config/token ใหม่ผ่าน API (timeout5วินาทีต่อ request) และตรวจ HLS manifest ก่อนส่งลิงก์
ถ้าล้มเหลวจึงจับ HLS request จริงใน WebView อีกขั้นสูงสุด8วินาที; หยุดค้นรหัสตอนซ้ำหลังได้ลิงก์
ส่ง signature/query/headers/Referer ให้ native player ไม่ส่ง HTML เป็น media ไม่เปิด UI เว็บและไม่แก้ challenge
คำแนะนำอ่านเฉพาะการ์ดซีรีส์ในหน้า detail (รวม wrapper .list-ads ซึ่งมีการ์ด content จริง) สูงสุด12รายการ
สถานะทดลอง: catalog สดยัง403ใน sandbox; snapshot tests และ Chromium Torbo ไม่ใช่ผลทดสอบ Android
