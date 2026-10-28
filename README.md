## GoSeries4K (เพิ่มใหม่ — ทดลอง)

- เพิ่มแพ็กเกจ `GoSeries4K` แยก: หน้าแรก 4 หมวด ค้นหา แบ่งหน้า รายละเอียด ตอน และซีรีส์แนะนำจากเว็บเดียวกัน
- ใช้ MHTML ที่อัปโหลดใน `samples/goseries4k.com/` ตรวจตัวอ่าน แต่ข้อมูลใช้งานจริงโหลดจากเว็บสด
  ตัวอย่าง Tigers Sniff the Rose มีปุ่มตอน 1–14 จึงไม่สร้างตอน 15–40 จากชื่อเรื่อง
- โหลด iframe/session/HLS ใหม่ตอนกดเล่น รองรับ Torbo ที่พบในตัวอย่าง ผ่าน native WebView เบื้องหลังสูงสุด 8 วินาทีต่อขั้น
  ไม่เปิดหน้าต่างเว็บหรือเบราว์เซอร์สำรอง; หากเว็บเปลี่ยนโฮสต์จะต้องเพิ่มตัวอ่านตามข้อมูลจริง
- การเลือกตอนตรวจทั้งรหัสตอนและ iframe ที่เปลี่ยนแล้ว เพื่อไม่ส่งตอนแรกแทนตอนที่ผู้ใช้เลือก
- สถานะทดลอง: GoSeries ตอบ HTTP403 จาก sandbox; ตรวจตัวอ่านด้วย snapshots และ Torbo เล่นใน Chromium ได้
  **ยังไม่ยืนยันการเลือกตอน/การเล่นบน Android** และไม่ได้เพิ่มระบบแก้ Cloudflare challenge

## สถานะล่าสุด

- **เพิ่ม Recommendations ท้ายหน้ารายละเอียดครบ 7 แพ็กเกจ / 12 เว็บไซต์** ผ่านช่อง recommendations ของ Cloudstream
  ใช้เรื่องเกี่ยวข้องที่เว็บประกาศก่อน; ถ้าไม่มี ใช้หมวดแนวเดียวกันจาก metadata ของเรื่อง หรือค้นภาคอื่นด้วยชื่อเรื่อง
  แสดงสูงสุด 12 เรื่อง ตัดเรื่องที่กำลังเปิด/URL ซ้ำ/ลิงก์นอกเว็บ; จำกัดการค้นหาเสริม 1.5 วินาทีและไม่ทำให้รายละเอียดล้มเหลว
  แหล่ง samples ใช้ข้อมูลที่บันทึกไว้; ถ้าไม่มีเรื่องเกี่ยวข้องจะไม่เติมรายการสุ่ม ส่วนนี้ยังไม่ใช้ AniList และไม่ต้องล็อกอิน
- รุ่นระบบแนะนำ: 037-HDD v17, 25-HD v13, Moviedofree v14, Movie2FreeHD v12, AHDZero v12, 24HD v13, ThaiAnime v17

- **24HD v12** แก้ regex CSS ที่ Java ยอมรับแต่ ICU บน Android ปฏิเสธ ทำให้ตัวอ่านเริ่มทำงานไม่ได้ทั้งแหล่งสดและ samples
  เพิ่มการตรวจ regex ของ24HDด้วย ICU ในชุด Python/CI เพื่อจับปัญหาที่ JVM tests อย่างเดียวตรวจไม่พบ
- **ถอดหน้าต่างเล่นเว็บสำรองออกทุกปลั๊กอินตามคำขอ** เมื่อดึงวิดีโอไม่ได้จะแจ้งสาเหตุในแอป
  ยังคงตัวเล่นสำรอง/CDNที่คืนวิดีโอให้เครื่องเล่นในแอป และการค้นลิงก์ JavaScript ที่ไม่เปิดหน้าต่างเล่นเว็บ
  ปุ่ม Open In Browser บนหน้าข้อผิดพลาดของ Cloudstream เป็น UI ของแอปหลัก ปลั๊กอินไม่ได้สร้างปุ่มนั้น
- รุ่นแก้หน้าต่างเว็บก่อนหน้า: 037-HDD v16,25-HD v12,Moviedofree v13,Movie2FreeHD v11,AHDZero v11,24HD v12,ThaiAnime v16

- **24HD ใช้ข้อมูลสดตั้งแต่ v11** จาก public WordPress API ของ `www.24hd.today` สำหรับรายการ/หมวด/ค้นหา/แบ่งหน้า/รายละเอียด
  อ่าน ACF ตัวเล่นและตอนจริงใหม่เมื่อกดเล่น หาก API ใช้ไม่ได้จะลองตัวอ่าน HTML เดิม
  ทดสอบ 5 หมวดหลัก หน้า2 ค้นหา หนังและซีรีส์ผ่าน API ได้ แม้หน้าเว็บ HTML ตอบ403จาก sandbox
- แหล่งหลักทั้ง **12 เว็บไซต์ยังเข้าถึงเว็บสด** ตาม URL/ตัวอ่านเฉพาะเว็บ ไม่ปิดเว็บเพียงเพราะ sandbox ตอบ403
  แหล่งที่มีชื่อ **(สำรองจาก samples)** เป็นตัวเลือกข้อมูลเก่าเพิ่มอีก5แหล่ง ผู้ใช้เลือกเอง
  ผล403จาก sandbox ไม่ยืนยันผลบนเครื่องจริง; Ruka คงตัวอ่านวิดีโอที่ผู้ใช้ยืนยันว่าเล่นได้

- 25-HD (ตั้งแต่ v11) เปิดรายละเอียดหนังที่ระบุ `type=movie` ได้โดยไม่ต้องรอเว็บตัวเล่นก่อน
  เมื่อกดเล่นยังโหลดข้อมูลใหม่; ถ้า HTML ตัวเล่นเสีย ใช้ public bootstrap API ของเว็บเดิมหนึ่งครั้ง
  ซีรีส์ใช้รายการตอนจริงจาก HTML/API คง season/episode และไม่เปลี่ยนเป็นหนังเมื่อโหลดข้อมูลล้มเหลว
- Moviedofree (ตั้งแต่ v12) อ่านเรื่องย่อจาก `.blog.column` ในส่วนเนื้อหาตาม HTML ปัจจุบัน
  ความช้าของเซิร์ฟเวอร์หมวดยังไม่ได้แก้; ไม่มีผลยืนยันการเล่นบน Android ในรอบนี้

- รอบแก้ repository/samples: 037-HDD v15, 25-HD v10, Moviedofree v11, Movie2FreeHD v10,
  AHDZero v10, 24HD v10, ThaiAnime v15; แก้ URL ติดตั้งและ manifest ให้ชี้ poppo112/037bbbb
- เพิ่มตัวเลือก **(สำรองจาก samples)** อีก 5 แหล่งสำหรับ 24HD/AHDZero/Neko-Miku/AnimeRuka/Suba-Anime
  รวม 17 ตัวเลือกในแอปจาก 12 เว็บไซต์เดิม ดูวิธีใช้และข้อจำกัดท้ายเอกสาร


- ThaiAnime v14 เพิ่ม **Hit-Anime / AnimeYour** รวมกับ Indy-Anime ที่เพิ่มใน v13
  แหล่งเรียงชื่อ: AnimeRuka / AnimeYour / Hit-Anime / Indy-Anime / Neko-Miku / Suba-Anime
- Indy/Hit/Your ใช้ `TeamAnimeSite`, `TeamAnimeParser`, `TeamAnimePlayer`, `TeamAnimeProvider` ร่วมกัน
  คง route และ selector แยกตามเว็บ หมวดล่าสุด/พากย์ไทย/ซับไทย/จีน/เดอะมูฟวี่มี5หมวดต่อแหล่ง
  อ่านเฉพาะการ์ด/กลุ่มตอนจริง เรียงเลขตอนและภาษา ไม่ปนแถบแนะนำ โฆษณา หรือปุ่มแจ้งเสีย
- Hit อ่านเซิร์ฟเวอร์จาก dropdown และ lazy iframe รวม MP4 สำรอง `/server2/` ที่เว็บประกาศ
  AnimeYour ใช้โดเมนปัจจุบัน `anime-your.com` และอ่าน HLS สำรองจาก `abcdxzy.xyz/embed2/`
  ตรวจ host/path/ID ตรงกับตอน คง port/query/Referer และโหลดตัวเล่นใหม่ทุกครั้ง ไม่เก็บ session จาก playlist เก่า
- ลองตัวเล่นสำรองที่ตรวจรูปแบบได้ก่อน ส่งตัวเลือกที่เหลือให้ Cloudstream extractor แล้วแจ้งข้อผิดพลาดเมื่อไม่พบวิดีโอ
  ถ้าหน้าตอนถูก redirect ไปหน้าอื่น จะรายงานตรงสาเหตุ ไม่อ่านการ์ดหน้าแรกเป็นวิดีโอ
- ไม่เดา URL วิดีโอ ไม่ค้นข้ามเว็บด้วยชื่อเรื่อง และไม่แก้ Cloudflare challenge
  ตัวอย่าง MP4 สำรองของ Hit เล่นผ่าน cloud Chromium 1920×1080เกิน90วินาที; ยังไม่ยืนยันบน Mi Box จริง

- รอบตรวจละเอียด v12: รวมกฎแบ่งหน้าไว้ใน `CatalogPagination` และการลอง CDN ไว้ใน `CdnFailoverInterceptor`
  แต่ละเว็บคงรูปแบบ path/query/Elementor และตัวเลือกแหล่งวิดีโอเดิม ดูโครงสร้างใน [ARCHITECTURE.md](ARCHITECTURE.md)
- แก้ 25-HD เมื่อ CDN สำรองทั้งหมดใช้ไม่ได้ให้ลอง URL เดิมพร้อม signature; 25-HD/Movie2FreeHD จำ origin ที่เล่นได้ใน interceptor เดียวกัน
  คง Range/Referer ปิด response ที่ล้มเหลวก่อนลองต่อ และหยุดเมื่อยกเลิกการเล่น
- แก้24HD/Movie2FreeHD เมื่อเว็บส่งตอนเดียวกันหลายแถวให้รวมตัวเล่นและเสียงครบก่อนรวมตอนซ้ำ
  24HD ทำดัชนี CSS รูปปกครั้งเดียวต่อหน้าแทนการค้น stylesheet ใหม่ทุกเรื่อง

- ThaiAnime v14 และแพ็กเกจที่เปิดใช้งานถอดระบบยืนยัน Cloudflare อัตโนมัติแล้วตามคำขอ
  ไม่มีตัวแก้ challenge เบื้องหลัง/หน้าต่างยืนยัน/รอ 12 หรือ 75 วินาที/ลองคำขอซ้ำ
  เก็บคุกกี้เว็บเดิมไว้และคง User-Agent ที่ตัวเล่นระบุเอง เว็บที่ปฏิเสธคำขอจะส่งสถานะผิดพลาดกลับทันที
  ไม่เปิดหน้าต่างเว็บหรือเบราว์เซอร์ภายนอกเมื่อกดเล่น แสดงสาเหตุที่ดึงลิงก์ล้มเหลวเป็นข้อความในแอป
  หน้าเว็บโหลดได้ไม่ได้ยืนยันว่าตัวอ่านได้ URL วิดีโอแล้ว และไม่มีการส่ง HTML เป็นวิดีโอ
  Suba เพิ่มการค้นหา iframe ตัวเล่นที่เว็บโหลดด้วย JavaScript ผ่าน native WebView สูงสุด 8 วินาที
  จากนั้นใช้ตัวอ่าน content origin เดิมและยังกรองโฆษณา ไม่เปิดระบบยืนยัน Cloudflare
  ยังไม่ได้ยืนยันการเล่นบน Mi Box จริง
  เอาเมนูเปิดเว็บสำรองออกจากหน้าตั้งค่าปลั๊กอินตามคำขอ
  หน้าต่างเว็บสำรองถูกถอดออกแล้วตามคำขอล่าสุด
  25-HD ยังอ่านข้อมูลตอน/URL ตัวเล่นที่บันทึกจากเวอร์ชันเดิมได้
- ตรวจซ้ำตัวที่ปิดไว้: Movie2Free.co มีทางค้นหา iframe ผ่าน browser ในแพ็กเกจ diagnostic5
  FWIP TV diagnostic6 แสดงสาเหตุ API error; AnimeSeven/FWIP ยังถูกปฏิเสธ403จาก sandbox จึงยังไม่เปิดใช้งาน
  ผู้ใช้ยืนยันว่า Ruka ดูได้แล้ว คงตัวอ่านวิดีโอเดิมไว้ รายละเอียดใน [samples/README.md](samples/README.md)
- การแก้จาก v8 ที่คงไว้: Suba ใช้รหัสตัวเล่นหลักไปยังหน้า content ของ anim-esun ที่ตรวจพบจริง
  อ่านรายการวิดีโอใหม่และยังกรองโฆษณา; ไม่ใช้เส้นทางนี้กับ player2 ซึ่งเป็นคนละเซิร์ฟเวอร์
  Moviedofree ใช้ URL สามหมวดหลักตาม percent encoding และ slash ของเมนูเว็บจริง
- ปิด Anime-Waku เพิ่มตามคำขอเนื่องจาก No links found; คง AnimeSeven, Movie2Free.co และ FWIP TV ที่ปิดไว้เดิม
  ไม่เผยแพร่สองแพ็กเกจหลัง และ ThaiAnime ไม่ลงทะเบียน AnimeSeven/Anime-Waku
  เก็บ source และ samples เดิมไว้อ้างอิงเท่านั้น หากเคยติดตั้ง Movie2Free/FWIPTV ให้ถอนส่วนขยายทั้งสองในแอปด้วย
  อัปเดต ThaiAnime เป็นเวอร์ชันล่าสุดแล้วเริ่มแอปใหม่เพื่อเอา AnimeSeven/Anime-Waku ออกจากรายการ
- Moviedofree v9 เปลี่ยนหมวดตัวอย่างหนังที่เว็บตอบว่าไม่พบข้อมูลเป็น Action และอัปเดต URL หมวดให้ตรงเมนูเว็บจริง
  หน้าแรกมี หนังล่าสุด / Action / การ์ตูน / หนังไทย; หมวดปีคง query ปีขณะเปลี่ยนหน้า
- แก้ URL แบ่งหน้าซ้ำใน Moviedofree/25-HD/24HD และคง query ตัวกรองของ 25-HD
  037-HDD/25-HD/Moviedofree/Movie2FreeHD ไม่ขอหน้าถัดไปเมื่อปุ่มว่าง ชี้ # หรือชี้หน้าเดิม
- 037-HDD/25-HD/24HD/ThaiAnime โหลดหมวดพร้อมกันได้สูงสุด 3 คำขอต่อ provider เช่นเดียวกับอีกสามแพ็กเกจ
  ไม่รับประกันเวลาหรือการเล่นบน Mi Box; ตัวอ่านวิดีโอ Ruka คงเดิม
- หน้าแรกเหลือหมวดหลัก: 037-HDD 5, 25-HD 5, Moviedofree 4, AHDZero 4, 24HD 5, Movie2FreeHD 4,
  Neko-Miku 4 และ AnimeRuka 5; Suba-Anime คงเมนูเดิม
  หมวดรองปี/ประเทศ/genre ไม่แสดงในตัวกรองหน้าแรก แต่ยังค้นหาชื่อเรื่องและแบ่งหน้าหมวดหลักได้
  Cloudstream 4.8.0 รอทุกหมวดก่อนคืนผล การลดคำขอจึงลดงานเริ่มต้น ไม่ใช่การรับประกันเวลาเครือข่าย
- ประวัติ Anime-Waku (ปิดใน v11): ThaiAnime v5 เพิ่ม Anime-Waku จาก `samples/Anime-Waku/`: หน้าแรก 5 หมวดหลัก ค้นหา รายละเอียดและตอน
  อ่าน REST `url_api` และตัวเล่น `/player/` ไปยัง nya.animenani แล้วอ่าน HLS bootstrap ใหม่ตอนกดเล่น
  ใช้ iframe ที่เว็บแสดงก่อน และเรียก REST สำรองเมื่อยังไม่พบลิงก์
  v6 แก้ URL ของ REST เป็น `/post/<id>?type=tv&source=<หมายเลข>` ตาม JavaScript จริงของเว็บ
  รองรับ API ที่ส่ง iframe HTML และทดสอบกรณี HTML เริ่มต้นยังไม่มี iframe
  API ส่ง X-Requested-With ตาม jQuery และใช้ cookie/UA จากหน้าแค็ตตาล็อก
  ขั้นตอนเล่นจะไม่เปิด Cloudflare challenge 60 วินาทีซ้ำให้แต่ละ API/iframe ที่ถูกบล็อก
  หาก HTTP ยังไม่พบลิงก์ จะเปิดหน้าตอนใน WebView ของแอปด้วย native networking สูงสุด 8 วินาที
  v7 จับ URL iframe ของ Nya ตั้งแต่เริ่มโหลด จึงไม่ต้องรอคำขอ HLS หลัง Play
  ดึง bootstrap สดด้วย User-Agent ที่ตรวจว่าใช้ได้ แล้วคง Referer ของหน้าตัวเล่น
  ชิ้นส่วน `.webp` ของ Nya มี PNG นำหน้า MPEG-TS: v7 อ่านขอบเขต PNG chunks และตรวจ TS sync ก่อนตัดออก
  ไม่ยึดจำนวนไบต์ตายตัว และรองรับ logical byte range สำหรับการ seek ของ Media3
  ตรวจด้วย production parser/interceptor ถึงชิ้นส่วนวิดีโอสดและถอดภาพ H.264 1080p ได้แล้ว
  แต่เส้นทาง Waku → API → wrapper ยังติด HTTP403 จาก sandbox และยังไม่ยืนยัน WebView/การเล่นบน Android
- เมื่อรวม GoSeries4K มี 8 แพ็กเกจ รวม 13 เว็บไซต์ (และตัวเลือกสำรอง samples เดิม 5 ตัวเลือก)

## ปลั๊กอินสำหรับ Cloudstream

เพิ่ม repository ด้วย `https://raw.githubusercontent.com/poppo112/037bbbb/master/repo.json`
หากเคยเพิ่ม repository ของ `jasdwa22/037bb` ให้เพิ่ม URL ด้านบนเพื่อรับเวอร์ชันของ fork นี้
แล้วติดตั้งหรืออัปเดตปลั๊กอินจาก repository นี้ในรายการส่วนขยาย:

| ปลั๊กอิน | ชื่อแหล่งในแอป | รองรับ |
| --- | --- | --- |
| ExampleProvider | 037-HDD | หนังและซีรีส์ |
| TwentyFiveHD | 25-HD | หนัง ค้นหา ซีซันและตอนที่เว็บมีลิงก์แล้ว |
| MovieDoFree | Moviedofree | หนังและค้นหา (ซ่อนเมนูซีรีส์ที่ยังไม่รองรับ) |
| AHDZero | AHDZero | อนิเมะ ซีรีส์และเดอะมูฟวี่ พากย์ไทย/ซับไทย |
| TwentyFourHD | 24HD | หนังและซีรีส์ แยกตอนตามลิงก์ที่มีจริง |
| Movie2FreeHD | Movie2FreeHD | หนัง/ซีรีส์ ค้นหา หมวดหมู่ ตอนและแหล่งเสียงไทย/ซับไทย |
| GoSeries4K | GoSeries4K | ทดลอง: ซีรีส์สด ค้นหา ตอน และ Torbo; ต้องตรวจบน Android |
| ThaiAnime | Neko-Miku, AnimeRuka, Suba-Anime | ทดลองสำหรับ beta: อนิเมะ/หนัง ค้นหา หมวดหมู่ แยกเสียงและตอน; การเล่นยังต้องทดสอบบนอุปกรณ์ |

ปลั๊กอินเดิมติดตั้งแยกกันได้ ส่วน ThaiAnime ติดตั้งครั้งเดียวจะเพิ่มแหล่งอนิเมะทั้งสามเว็บ สำหรับ 25-HD วิดีโอบางรายการรวมหลายเสียงและคำบรรยายไว้ในลิงก์เดียว
ให้เลือกแทร็กเสียงและคำบรรยายในเครื่องเล่น ความพร้อมของวิดีโอขึ้นอยู่กับเว็บต้นทาง

เวอร์ชัน 037-HDD v7, 25-HD v2 และ Moviedofree v2 เพิ่มหมวดหมู่ตามเมนูของแต่ละเว็บ
รวมประเภท ประเทศ ผู้ให้บริการ และปีที่เว็บแสดง เลือกหมวดที่ต้องการในตัวกรองหน้าแรกได้
บางหมวดอาจว่างตามเว็บต้นทาง

25-HD v2 อ่านเส้นทาง CDN จาก HLS content steering เพื่อโหลดชิ้นส่วนวิดีโอ เสียง และซับ
ยังคงใช้ master playlist เดิมเพื่อให้เลือกแทร็กเสียงและคำบรรยายในเครื่องเล่นได้
Moviedofree v2 เพิ่ม Referer สำหรับรูป รองรับรูป lazy-load และแก้การเปลี่ยนหน้าของหมวดปี

AHDZero v1 และ 24HD v1 อ่านหมวดหมู่และตอนจาก HTML ตัวอย่างใน `samples/`
AHDZero มีหมวดซับไทย พากย์ไทย เดอะมูฟวี่ และ genre; 24HD มีประเภทและชุดหนังตาม tag
24HD อ่านรูปปกจาก CSS รวมรูปที่เว็บซ่อนด้วย lazy-load และใช้ลิงก์เปลี่ยนหน้าของรายการหลัก
ทั้งสองใช้คุกกี้เว็บที่มีอยู่โดยไม่เปิดระบบยืนยัน Cloudflare อัตโนมัติ
การตรวจใน sandbox ยืนยันตัวอ่าน HTML และไฟล์สตรีมตัวอย่าง ยังต้องทดสอบบนเครื่องจริง

## ประวัติการเพิ่มและแก้ไข (บางแหล่งปิดแล้วตามสถานะล่าสุด)

- Movie2FreeHD v1 ใช้หน้า `/movies/` และ `/series/` สำหรับแบ่งหน้า อ่านผลค้นหาแยกจากรายการแนะนำ
  รูปปกรองรับ CSS และ lazy-load; ตอนเรียงตามซีซัน/เลขตอน และรวมลิงก์ซ้ำโดยคงชื่อพากย์ไทย/ซับไทยไว้
  ดึงลิงก์ Meeplayer ใหม่เมื่อกดเล่น รักษา HLS master สำหรับแทร็กเสียง และลอง CDN สำรองเมื่อโหลดชิ้นส่วนวิดีโอผิดพลาด
- Movie2Free.co v1 เพิ่มเป็นปลั๊กอินแยกจาก Movie2FreeHD อ่านเฉพาะรายการหลัก 36 เรื่องในหน้าแรก
  ไม่ปนแถว TOP 10/แนะนำ มีหมวดหนังและค้นหาผ่าน AJAX ของเว็บ พร้อมรายละเอียด/ปี/ประเภท
  อ่าน wrapper ssplayer และตัวเล่น FastCDN/ตัวเล่นไว/สำรองใหม่ทุกครั้ง รักษา Referer
  ข้าม mirror ที่เสียโดยยังลองตัวที่เหลือ จำกัดการวน iframe และไม่ดึง URL โฆษณามาเป็นหนัง
  ใช้ `status = 3` จนกว่าจะทดสอบ Cloudflare และการเล่นจริงบน Android
- FWIP TV v3 อ่าน API `player123up.xyz` ตามสคริปต์ตัวเล่นที่ผู้ใช้ส่งมา
  ส่ง origin ของ iframe/เว็บแม่ ดึงลิงก์ใหม่ แยกแหล่งเสียงที่พร้อม และลอง backup ผ่าน extractor ของแอป
  รับเฉพาะ HLS/ไฟล์วิดีโอที่ระบุรูปแบบได้ ไม่แสดง iframe หรือ URL opaque ที่ยังไม่ยืนยันเป็นวิดีโอ
  คง `status = 3`: API จริงยังตอบ 403 จาก sandbox จึงยังไม่ยืนยันการเล่นบน Android
  HTML ใหม่ยืนยันว่า “Playback stopped.” ถูกแสดงเมื่อ guard ตรวจ DevTools ทำงาน
  รวมคลิกขวา, F12, คีย์ลัด Inspect/View source และการตรวจจับอัตโนมัติเมื่อเปิด blockDevTools
  เป็นเงื่อนไขบนเว็บต้นทาง การแก้ provider ไม่ได้เปลี่ยน JavaScript ของเว็บไซต์
- ThaiAnime v1 เพิ่ม Neko-Miku, AnimeRuka, AnimeSeven และ Suba-Anime จาก HTML ที่ผู้ใช้บันทึก
  เมนูมาจากเว็บจริง แยก Anime/AnimeMovie, พากย์ไทย/ซับไทยและซีซัน โดยไม่แสดงตอนที่ยังไม่มีลิงก์
  ค้นหา Suba อ่านเฉพาะผลค้นหา ไม่รวมรายการล่าสุดที่เว็บแทรกไว้; รองรับรูป lazy-load และการแบ่งหน้าของแต่ละเว็บ
  AnimeRuka อ่านตัวเลือก REST ใหม่ทุกครั้งที่เล่น; AnimeSeven อ่านตัวเล่นซ้อนและ manifest จาก bootstrap ใหม่
  Suba อ่าน content iframe ของ Kokora ไปยัง anim-esun และเก็บลิงก์วิดีโอจริงตามความละเอียด โดยไม่ใช้ preroll
  โฮสต์ Akuma/Abyss/AnimeMami/OK.ru ส่งต่อให้ extractor ที่มีในแอป ความพร้อมขึ้นกับแอปและเว็บปลายทาง
  ตั้ง status = 3 จนกว่าจะตรวจการเล่นบนอุปกรณ์จริงได้
- 24HD v2 และ AHDZero v2 ใช้ HTTP cache ของแอปสำหรับรายการ/รายละเอียด และโหลดหน้าใหม่ตอนกดเล่น
  ลดการโหลดหน้าแรกซ้ำทางเครือข่ายของ 24HD; รองรับปุ่ม load-more ที่มี `data-next-page` แบบ relative
  AHDZero เรียงตอนตามเลข และ 24HD อ่านรูป lazy-load เมื่อไม่มี CSS รูปปก
- 25-HD v3 แก้ชื่อเมนูปีให้ตรง URL, รองรับรูป lazy-load และไม่ทิ้ง master playlist เมื่อโหลด steering ไม่สำเร็จ
  ตัวเล่นสำรองที่ล้มเหลวไม่ทำให้ลิงก์ที่เจอแล้วหายไป
- 037-HDD v8 เก็บ query เมื่อเปลี่ยนหน้า และข้าม extractor สำรองที่ผิดพลาดเพื่อทำงานกับแหล่งถัดไป
- Moviedofree v3 ซ่อนเมนูซีรีส์ที่ไม่ตรงกับความสามารถของ provider (ยังรองรับหนังเท่านั้น)
  ตรวจหน้าหมวดซีรีส์ฝรั่งแล้วเว็บส่ง “ไม่พบข้อมูล”

### สถานะการตรวจเว็บ

Movie2FreeHD ตรวจหน้าแรก/หนัง/ซีรีส์/ค้นหา/หน้าที่สองและ API ตัวเล่นจริงได้
ตรวจ Meeplayer ของซีรีส์ตัวอย่างถึง HLS master, media playlist และข้อมูลชิ้นส่วนวิดีโอแบบจำกัดขนาดแล้ว
ไม่ได้ยืนยันว่าทุกเรื่องหรือทุกแทร็กเสียงเล่นได้ และยังไม่ได้ทดสอบแอปบนอุปกรณ์ Android
037-HDD, 25-HD และ Moviedofree ตรวจตัวอ่านกับ HTML หน้าแรกที่ดึงใหม่ได้
24HD, AHDZero และ FWIP ถูก Cloudflare บล็อกใน sandbox จึงใช้ HTML ตัวอย่างและ unit tests ตรวจโครงสร้าง

Nekomiku (`nekomiku.net`), AnimeRuka (`animeruka.com`), AnimeSeven (`www.anime-seven.com`)
และ Suba-Anime (`suba-anime.com`) เพิ่มในแพ็กเกจ ThaiAnime แล้ว โดยใช้ samples ที่ผู้ใช้เพิ่มบน master
สามเว็บแรกและ FWIP/ตัวเล่น player123up ยังถูก Cloudflare บล็อกทั้ง HTTP และ Chromium
Suba เปิดหน้าเว็บและตัวเล่นใน Chromium ได้ ตรวจ content source ที่ anim-esun และดึงชิ้นส่วนวิดีโอ
1,024 ไบต์ผ่าน Chromium ได้ HTTP 206 (video/x-m4v) แต่ curl ดึงไฟล์เดียวกันหมดเวลา
จึงยังไม่ได้ยืนยันเส้นทางโหลดวิดีโอของแอปหรือการเล่นจนจบบน Android
ผล tests ยืนยันตัวอ่าน HTML/ตัวเลือกเสียง/เส้นทางตัวเล่น ไม่ยืนยันว่าโฮสต์ทุกตัวเล่นได้
รายละเอียดไฟล์ที่ใช้ได้และตัวอย่างที่ยังขาดอยู่ใน [samples/README.md](samples/README.md)
Movie2FreeHD และ Movie2Free.co เป็นคนละเว็บและมีปลั๊กอินแยกกัน
Movie2Free.co ตรวจสด wrapper/FastCDN/HLS master/variant และ media range ได้ แต่หน้าเว็บหลักติด Cloudflare
FastCDN ตัวอย่างมี PNG prefix 545 ไบต์ก่อน MPEG-TS จึงยังต้องทดสอบความเข้ากันได้กับตัวเล่น Android

ทดสอบทุก provider และสร้างแพ็กเกจ:

```bash
GITHUB_REPOSITORY=poppo112/037bbbb ./gradlew make makePluginsJson testDebugUnitTest
```

ใช้ JDK 17, Android SDK 35 และกำหนด `ANDROID_HOME` ให้ถูกต้อง
Unit tests ใช้ HTML fixtures และ API จำลองในหน่วยทดสอบ จึงไม่ต้องเข้าถึงเว็บต้นทางและไม่อ่านไฟล์ `temp`

---

**⚠️ This is currently under development, dont use it yet if you're not comfortable with constantly merging new changes**

# `Cloudstream3 Plugin Repo Template`

Template for a [Cloudstream3](https://github.com/recloudstream) plugin repo

**⚠️ Make sure you check "Include all branches" when using this template**

 
## Getting started with writing your first plugin

This template includes 1 example plugin.

1. Open the root build.gradle.kts, read the comments and replace all the placeholders
2. Familiarize yourself with the project structure. Most files are commented
3. Build or deploy your first plugin using:
   - Windows: `.\gradlew.bat ExampleProvider:make` or `.\gradlew.bat ExampleProvider:deployWithAdb`
   - Linux & Mac: `./gradlew ExampleProvider:make` or `./gradlew ExampleProvider:deployWithAdb`


## Granting All Files Access on Newer Android Devices

For local plugin testing, you need to grant the app "All Files Access" on newer Android devices (Android 11 and above). Here’s how to do it:

### Using ADB

* `adb shell appops set --uid PACKAGE_NAME MANAGE_EXTERNAL_STORAGE allow`
* Replace `PACKAGE_NAME` with the name of the package for the Cloudstream3 version you are using:
   - debug: `com.lagradost.cloudstream3.prerelease.debug`
   - prerelease: `com.lagradost.cloudstream3.prerelease`
   - stable: `com.lagradost.cloudstream3`

### Manually

1. **Open Settings**: Go to your device’s Settings menu.

2. **Navigate to Special Access**:
   - Tap on "Apps & notifications" or "Apps".
   - Select "Special app access" or "Special access".

3. **Select All Files Access**:
   - Tap on "All files access".
   - It may be under the three vertical dots menu towards the top of the screen.

4. **Grant Access to the App**: Find the app in the list and tap on it to toggle it, if it is not already enabled.

6. **Restart the App**: Close and reopen the app to apply the changes.


## License

Everything in this repo is released into the public domain. You may use it however you want with no conditions whatsoever


## Attribution

This template as well as the gradle plugin and the whole plugin system is **heavily** based on [Aliucord](https://github.com/Aliucord).
*Go use it, it's a great mobile discord client mod!*

## แหล่งสำรองจาก HTML ที่บันทึกไว้

เพิ่ม 24HD / AHDZero / Neko-Miku / AnimeRuka / Suba-Anime ที่มีชื่อท้ายว่า **(สำรองจาก samples)**
เลือกแหล่งนี้ใน Cloudstream เมื่อแหล่งสดเข้าไม่ได้ หน้าแรกและค้นหาทำงานจากข้อมูลที่รวมอยู่ในแพ็กเกจ
ไม่ต้องดาวน์โหลด samples จาก GitHub ตอนใช้งาน และไม่มีการลองผ่าน Cloudflare ซ้ำก่อนแสดงรายการ
รายการไม่ครบและไม่ใช่ข้อมูลล่าสุด; เรื่องที่มีรายละเอียดบันทึกไว้จะอยู่ก่อนรายการอื่น
รายละเอียดหรือตอนที่ไม่มีในชุดสำรองยังต้องโหลดเว็บจริง รูปปกยังอาศัยเครือข่าย

แหล่งสำรองอ่านเฉพาะหน้า catalog/รายละเอียด/ตอนจาก snapshots; ตัวเล่นภายนอก, API, session และ media
ยังโหลดใหม่เมื่อกดเล่น ไม่รับประกันว่าจะผ่าน Cloudflare หรือวิดีโอยังอยู่
สคริปต์สร้างชุดสำรองลบ JavaScript ที่บันทึกไว้ คง JSON-LD สำหรับ URL embed และกู้ iframe เฉพาะ URL
ที่ Chrome บันทึกไว้จริงในไฟล์ลูก ไม่มีการเดา URL จากชื่อไฟล์ และไม่มีการนำ player response เก่ามาเล่น

สร้างใหม่หลังแก้ samples: `python3 scripts/generate_sample_catalog.py`
ตรวจว่าไฟล์ที่รวมไว้ตรงกับ samples: `python3 scripts/generate_sample_catalog.py --check`

repo.json และ URL ติดตั้งชี้ที่ `poppo112/037bbbb` แล้ว; workflow ต้องทำงานบน master/main
หรือกด Run workflow หลังรวมการเปลี่ยนแปลง เพื่อเผยแพร่ไฟล์ใหม่บน builds
workflow รันทดสอบก่อนเผยแพร่ และเพิ่ม commit ใหม่บน builds โดยไม่เขียนประวัติทับ

### ตรวจ regex ที่ใช้บน Android

ชุด Python tests ต้องมี ICU runtime (Ubuntu: `sudo apt-get install libicu-dev`)
`python3 -m unittest discover -s scripts -p 'test_*.py'` จะตรวจ literal Regex ของตัวอ่าน24HDด้วย ICU
ซึ่งเป็น backend ของ Android แทนการอาศัยผล compile ของ JDK อย่างเดียว
ระบุ `ANDROID_REGEX_ICU_LIBRARY` เป็น path ของ libicui18n.so.<version> ได้เมื่อใช้ ICU ที่แตกไฟล์ไว้แยกจากระบบ
