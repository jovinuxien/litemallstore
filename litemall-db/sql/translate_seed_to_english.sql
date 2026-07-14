-- Translate remaining Chinese seed/demo content to English (2026-07-10)
-- Scope: coupon, brand, groupon_rules, keyword, ad, issue, topic, system,
--        goods.brief (CJ supplier-note cleanup), goods_attribute, goods_product/cart/order_goods specifications.
-- Excluded on purpose: litemall_comment (993 demo reviews - pending decision translate vs remove).

SET NAMES utf8mb4;
START TRANSACTION;

-- ---------- coupons ----------
UPDATE litemall_coupon SET name='Limited-Time Discount Coupon', `desc`='Valid storewide', tag='No restrictions' WHERE id IN (1,2);
UPDATE litemall_coupon SET name='New User Coupon', `desc`='Valid storewide', tag='No restrictions' WHERE id=3;
UPDATE litemall_coupon SET name='Redeemable Coupon', `desc`='Valid storewide', tag='Redeem by code only' WHERE id=8;

-- ---------- brands (names normalized + descriptions translated) ----------
UPDATE litemall_brand SET name='Rollei Manufacturer', `desc`='Our team set out to craft a breathable, soft silk quilt - from cocoon raw material to thermal performance, tested and refined through multiple selections. Made by the Rollei manufacturer, hand-finished for premium comfort.' WHERE id=1001045;
UPDATE litemall_brand SET name='Sperry Manufacturer', `desc`='We compared vulcanized-shoe makers and visited multiple factories before choosing the Sperry brand manufacturer - a comfortable, well-shaped pair of high-quality canvas shoes.' WHERE id=1018000;
UPDATE litemall_brand SET name='Marc Jacobs Manufacturer', `desc`='We sought out the manufacturer behind the design brand Marc Jacobs: strict material selection and refined weaving details bring you elegant, premium accessories.' WHERE id=1021000;
UPDATE litemall_brand SET name='WMF Manufacturer', `desc`='We searched for the manufacturer of WMF, Germany''s century-old premium kitchenware, and chose a stainless-steel factory with 14 years of experience - quality cookware that gets more done.' WHERE id=1024000;
UPDATE litemall_brand SET name='Timberland Manufacturer', `desc`='To build quality, stylish work boots our team visited major boot factories at home and abroad and chose the Timberland manufacturer - 15 years of shoemaking expertise, professional quality guaranteed.' WHERE id=1025000;
UPDATE litemall_brand SET name='Under Armour Manufacturer', `desc`='In search of great socks we visited leading sock-producing regions and selected the Under Armour brand''s partner manufacturer - raw material, craft and quality screened at every step.' WHERE id=1026001;
UPDATE litemall_brand SET name='Gucci Manufacturer', `desc`='For an elegant, fashionable dress hat we partnered with a large felt-hat maker with a decade of experience - good design, good craft, good materials.' WHERE id=1028000;
UPDATE litemall_brand SET name='Armani Manufacturer', `desc`='We work with an internationally certified factory that has long produced for Armani, Alexander Wang and other famous brands - professional imported equipment and precise quality control for a premium home experience.' WHERE id=1033003;
UPDATE litemall_brand SET name='Hermès Group Manufacturer', `desc`='We use the fragrance supplier of the European luxury house Hermès; after repeated blending, testing and selection, we bring you a uniquely rich scent experience.' WHERE id=1033004;
UPDATE litemall_brand SET name='Atsugi Manufacturer', `desc`='Quality-obsessed about socks, we partnered with the Atsugi brand manufacturer, holding 12 years of production credentials - a light, elegant, comfortably slimming sock series.' WHERE id=1037000;
UPDATE litemall_brand SET name='Tescom Manufacturer', `desc`='For stylish, healthy personal-care appliances we chose the Tescom brand manufacturer - one of the world''s largest personal-care appliance factories, 20 years of experience, exporting to 180+ countries.' WHERE id=1040000;

-- ---------- groupon rules: goods were already renamed in English, copy names over ----------
UPDATE litemall_groupon_rules r JOIN litemall_goods g ON g.id=r.goods_id SET r.goods_name=g.name WHERE NOT r.deleted;

-- ---------- keywords ----------
UPDATE litemall_keyword SET keyword='Mother''s Day' WHERE id=1;
UPDATE litemall_keyword SET keyword='Japanese Style' WHERE id=2;
UPDATE litemall_keyword SET keyword='Summer Quilt' WHERE id=3;
UPDATE litemall_keyword SET keyword='New Arrivals' WHERE id=4;
UPDATE litemall_keyword SET keyword='Sunglasses' WHERE id=5;
UPDATE litemall_keyword SET keyword='Gift Pack Early Access' WHERE id=6;
UPDATE litemall_keyword SET keyword='Flat Shoes' WHERE id=7;

-- ---------- ads / banners (text fields; images handled separately) ----------
UPDATE litemall_ad SET name='Partnership - Which One''s for You?', content='Partnership - Which One''s for You?' WHERE id=1;
UPDATE litemall_ad SET name='Event - Food Festival', content='Event - Food Festival' WHERE id=2;
UPDATE litemall_ad SET name='Event - Mother''s Day', content='Mother''s Day Special' WHERE id=3;

-- ---------- FAQ / issues ----------
UPDATE litemall_issue SET question='How is the shipping fee charged?', answer='Orders of $88 or more (excluding shipping) ship free; orders under $88 incur a flat $10 shipping fee per order.' WHERE id=1;
UPDATE litemall_issue SET question='Which courier is used for delivery?', answer='We ship with our default express partner by default (a few items use other carriers), covering most regions nationwide.' WHERE id=2;
UPDATE litemall_issue SET question='How do I request a return?', answer='1. Within 30 days of receiving the item you can apply for a no-hassle return; the refund is returned via the original payment method (bank processing times vary). 2. Please keep the item and packaging in good condition.' WHERE id=3;
UPDATE litemall_issue SET question='How do I get an invoice?', answer='1. To receive a standard invoice, select "I need an invoice" when placing the order and fill in the details. 2. E-invoices are sent by email after the order is delivered.' WHERE id=4;

-- ---------- topics (title + subtitle) ----------
UPDATE litemall_topic SET title='Seasonal Picks from Our Designers', subtitle='New original-design spring collection' WHERE id=264;
UPDATE litemall_topic SET title='One Silk Scarf Instantly Elevates Your Style', subtitle='Remember the state-gift silk scarves we co-created with the G20 gift maker? A look back at our finest weaves.' WHERE id=266;
UPDATE litemall_topic SET title='The Secret to Great Rice: A Pot That Breathes', subtitle='This January we partnered with Nagatani-en of Iga, Japan - a kiln with 180 years of Iga-yaki history.' WHERE id=268;
UPDATE litemall_topic SET title='The New Art of Lounging', subtitle='Relax in style, live well.' WHERE id=271;
UPDATE litemall_topic SET title='Cooking, Refined and Simple', subtitle='Enjoy natural flavors - keep every day fresh.' WHERE id=272;
UPDATE litemall_topic SET title='Summer Isn''t Summer Without Cork Slippers', subtitle='Barely April and it''s already 30°C - early buyers say they''re the most comfortable slippers they own.' WHERE id=274;
UPDATE litemall_topic SET title='An Armful of Softness to Soothe Your Days', subtitle='The taiko cushion almost missed its season - its plush feel turned out to be made for year-round comfort.' WHERE id=277;
UPDATE litemall_topic SET title='The New Stripe Trend', subtitle='Classic, versatile, clean lines.' WHERE id=281;
UPDATE litemall_topic SET title='Snacks That Fill the Room', subtitle='Office-snack picks our team swears by.' WHERE id=282;
UPDATE litemall_topic SET title='The One Pair Every Growing Kid Needs', subtitle='Caterpillar shoes: flexible, breathable and easy to slip on - a parent favorite from a 2-year-old''s mom on our team.' WHERE id=283;
UPDATE litemall_topic SET title='Guilt-Free Sweet and Crispy', subtitle='All the crunch, none of the oil - the snack our food team can''t put down.' WHERE id=286;
UPDATE litemall_topic SET title='The New Show Home', subtitle='One soft-furnishing style, one home.' WHERE id=287;
UPDATE litemall_topic SET title='Pro Sports Socks, Great Value', subtitle='Good shoes deserve good socks - professional support without the premium price.' WHERE id=289;
UPDATE litemall_topic SET title='A New Take on Comfort', subtitle='How to choose the pieces that fit your life.' WHERE id=291;
UPDATE litemall_topic SET title='A Pot to Pass Down for Generations', subtitle='Buy the century-heritage enamel pot, get a mini macaron-color pot free.' WHERE id=294;
UPDATE litemall_topic SET title='A New Life They Found Here', subtitle='Instant discounts on many items - save up to $400.' WHERE id=295;
UPDATE litemall_topic SET title='Travelers Call It a Game-Changer', subtitle='No more drying-underwear woes on the road - pack fresh, travel light.' WHERE id=299;
UPDATE litemall_topic SET title='The All-Natural Soap That Beats Chemical Detergents', subtitle='Gentle on skin and clothes - a laundry-day peacemaker.' WHERE id=300;
UPDATE litemall_topic SET title='Five Holiday Gift Problems, Solved at Once', subtitle='The gift lists they actually want.' WHERE id=313;
UPDATE litemall_topic SET title='Care for Every Step of Their Growth', subtitle='Same factory as the pro sports brands - caterpillar shoes, buy 2 get 1 free.' WHERE id=314;

-- ---------- system setting ----------
UPDATE litemall_system SET key_value='Shanghai' WHERE key_name='litemall_mall_address';

-- ---------- CJ goods briefs: strip supplier-note Chinese fragments ----------
UPDATE litemall_goods SET brief=REGEXP_REPLACE(brief,'[\\x{4e00}-\\x{9fff}]+','') WHERE NOT deleted AND brief REGEXP '[\\x{4e00}-\\x{9fff}]';

-- ---------- variant specification labels ----------
UPDATE litemall_goods_product SET specifications=REPLACE(specifications,'标准','Standard') WHERE specifications LIKE '%标准%';
UPDATE litemall_goods_product SET specifications=REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(specifications,'1.5m床垫*1+枕头*2','1.5m mattress x1 + 2 pillows'),'1.8m床垫*1+枕头*2','1.8m mattress x1 + 2 pillows'),'浅杏粉','Apricot Pink'),'玛瑙红','Agate Red'),'烟白灰','Smoke Gray') WHERE specifications REGEXP '[\\x{4e00}-\\x{9fff}]';
-- same labels copied into carts and historical order lines
UPDATE litemall_cart SET specifications=REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(specifications,'标准','Standard'),'1.5m床垫*1+枕头*2','1.5m mattress x1 + 2 pillows'),'1.8m床垫*1+枕头*2','1.8m mattress x1 + 2 pillows'),'浅杏粉','Apricot Pink'),'玛瑙红','Agate Red'),'烟白灰','Smoke Gray') WHERE specifications REGEXP '[\\x{4e00}-\\x{9fff}]';
UPDATE litemall_order_goods SET specifications=REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(specifications,'标准','Standard'),'1.5m床垫*1+枕头*2','1.5m mattress x1 + 2 pillows'),'1.8m床垫*1+枕头*2','1.8m mattress x1 + 2 pillows'),'浅杏粉','Apricot Pink'),'玛瑙红','Agate Red'),'烟白灰','Smoke Gray') WHERE specifications REGEXP '[\\x{4e00}-\\x{9fff}]';

-- ---------- goods attributes (care/tip boilerplate, grouped) ----------
-- generic textile care note
UPDATE litemall_goods_attribute SET value='Textiles go through dyeing, weaving and other processes; a slight odor when first unpacked is normal and fades after a rinse and air-dry. All fabrics are certified to national standards and use eco-friendly reactive dyes.' WHERE NOT deleted AND attribute='Kind tips' AND value LIKE '%纺织品经历印染%' AND value NOT LIKE '%拉链%' AND value NOT LIKE '%深色纯棉毛巾被%';
-- textile + zipper batch note
UPDATE litemall_goods_attribute SET value='1. A slight odor when first unpacked is normal and fades after a rinse and air-dry; fabrics use certified eco-friendly reactive dyes. 2. Fabric and zipper may vary slightly between batches; the delivered item prevails.' WHERE NOT deleted AND attribute='Kind tips' AND value LIKE '%拉链%';
-- dark cotton towel quilt
UPDATE litemall_goods_attribute SET value='1. Dark cotton towel quilts can shed fine lint - rinse 1-2 times before use. 2. Wash separately from clothing; harsh machine washing can cause lint or pulled threads. 3. A slight odor on unpacking is normal and fades after rinsing and drying.' WHERE NOT deleted AND attribute='Kind tips' AND value LIKE '%深色纯棉毛巾被%';
-- wool blanket
UPDATE litemall_goods_attribute SET value='1. Woven from natural wool with traditional machine weaving; slight prickliness against bare skin is normal. 2. Some loose fibers may shed at first - dry cleaning is recommended. 3. Pure wool may carry a slight odor at first; air it out for 2-3 days.' WHERE NOT deleted AND attribute='Kind tips' AND value LIKE '%羊毛毯采取传统机织工艺%';
-- wool rug
UPDATE litemall_goods_attribute SET value='1. A slight wool scent is normal - air the room and it fades. 2. New wool rugs shed a little at first; vacuum a few times and shedding subsides. 3. Natural wool can feel slightly prickly underfoot - not a quality defect.' WHERE NOT deleted AND attribute='Kind tips' AND value LIKE '%羊毛地毯%';
-- wool quilt (carbonized)
UPDATE litemall_goods_attribute SET value='1. Naturally carbonized, washed and combed wool may carry a slight odor - air for 2-3 days and it fades. 2. To reduce chemical processing some wool is not fully bleached; slight yellowing is the wool''s natural color and is normal.' WHERE NOT deleted AND attribute='Kind tips' AND value LIKE '%经过碳化、清洗、梳理的天然羊毛%';
-- memory foam pillows/mattresses
UPDATE litemall_goods_attribute SET value='1. Memory foam is made of imported eco-friendly polyurethane; a slight odor when unpacked fades after airing 3-5 days with the cover removed. 2. Comfort varies with height, build and sleep habits - try several to find your best fit.' WHERE NOT deleted AND attribute='Kind tips' AND value LIKE '%记忆绵产品为进口环保化学材质%';
-- memory foam washing care
UPDATE litemall_goods_attribute SET value='1. Remove the cover and wash cold; dry in shade, away from colors that bleed. 2. Slight shrinkage after washing is normal - stretch gently to reshape. 3. The foam core is not washable; keep dry and ventilated. If wet, blot with a towel and air-dry.' WHERE NOT deleted AND attribute='Kind tips' AND value LIKE '%水洗时请将外套取下%';
-- plant-filled pillows
UPDATE litemall_goods_attribute SET value='1. Filled with natural plant material - keep dry in humid seasons; if small insects appear, 3 hours in the sun removes them. 2. Comfort varies with height, build and sleep habits - try several pillows to find your fit. 3. Limit massage use to ~15 minutes.' WHERE NOT deleted AND attribute='Kind tips' AND (value LIKE '%天然植物填充%' OR value LIKE '%植物填充，遇梅雨季节%');
-- knitted cotton
UPDATE litemall_goods_attribute SET value='1. Knitted cotton may pill slightly with use - a fabric shaver restores it. 2. Slight deformation with use is normal for knitwear and does not affect use.' WHERE NOT deleted AND attribute='Kind tips' AND value LIKE '%针织棉面料%';
-- cotton blanket
UPDATE litemall_goods_attribute SET value='1. Pure-cotton knit blanket, traditionally woven - wash once before use to remove loose lint. 2. A slight natural odor fades after airing 2-3 days. 3. May deform slightly after repeated washing; dry cleaning is recommended.' WHERE NOT deleted AND attribute='Kind tips' AND value LIKE '%纯棉毯采取传统针织工艺%';
-- particle filling
UPDATE litemall_goods_attribute SET value='Filled with foam particles by hand, so weight may vary slightly. Compression during shipping can cause temporary dents - avoid storing under heavy objects to prevent the filling from collapsing.' WHERE NOT deleted AND attribute='Kind tips' AND value LIKE '%人工填充粒子%';
-- bedding sizes
UPDATE litemall_goods_attribute SET value='1.5m set: duvet cover 200x230cm, pillowcases 48x74cm x2, fitted sheet 150x200x28cm (flat sheet 245x250cm). 1.8m set: duvet cover 220x240cm, pillowcases 48x74cm x2, fitted sheet 180x200x28cm (flat sheet 245x270cm).' WHERE NOT deleted AND attribute='size' AND value LIKE '%床笠%';
-- delivery special reminder
UPDATE litemall_goods_attribute SET value='1. No dispatch on weekends - weekend orders ship on Monday. 2. Ships to most regions (remote areas by air freight). 3. Delivered unassembled as a whole piece - assemble per the manual; contact support for guidance.' WHERE NOT deleted AND attribute='Special reminder' AND value LIKE '%周六日暂无法发货%';
-- mattress set specification
UPDATE litemall_goods_attribute SET value='Set 1: AB-sided pocket-spring mattress with imported latex 150x200cm x1 + washable anti-mite silk-down pillows x2. Set 2: AB-sided pocket-spring mattress with imported latex 180x200cm x1 + washable anti-mite silk-down pillows x2.' WHERE NOT deleted AND attribute='Specification' AND value LIKE '%独立弹簧床垫%';
-- color value
UPDATE litemall_goods_attribute SET value='Blue stripes / Pink stripes' WHERE NOT deleted AND attribute='color' AND value LIKE '%条纹%';

COMMIT;

-- ---------- verification ----------
SELECT
 (SELECT COUNT(*) FROM litemall_coupon WHERE NOT deleted AND CONCAT_WS(' ',name,`desc`,tag) REGEXP '[\\x{4e00}-\\x{9fff}]') AS coupon,
 (SELECT COUNT(*) FROM litemall_brand WHERE NOT deleted AND CONCAT_WS(' ',name,`desc`) REGEXP '[\\x{4e00}-\\x{9fff}]') AS brand,
 (SELECT COUNT(*) FROM litemall_groupon_rules WHERE NOT deleted AND goods_name REGEXP '[\\x{4e00}-\\x{9fff}]') AS groupon,
 (SELECT COUNT(*) FROM litemall_keyword WHERE NOT deleted AND keyword REGEXP '[\\x{4e00}-\\x{9fff}]') AS keyword,
 (SELECT COUNT(*) FROM litemall_ad WHERE NOT deleted AND CONCAT_WS(' ',name,content) REGEXP '[\\x{4e00}-\\x{9fff}]') AS ad,
 (SELECT COUNT(*) FROM litemall_issue WHERE NOT deleted AND CONCAT_WS(' ',question,answer) REGEXP '[\\x{4e00}-\\x{9fff}]') AS issue,
 (SELECT COUNT(*) FROM litemall_topic WHERE NOT deleted AND CONCAT_WS(' ',title,subtitle) REGEXP '[\\x{4e00}-\\x{9fff}]') AS topic,
 (SELECT COUNT(*) FROM litemall_goods WHERE NOT deleted AND CONCAT_WS(' ',name,brief) REGEXP '[\\x{4e00}-\\x{9fff}]') AS goods,
 (SELECT COUNT(*) FROM litemall_goods_attribute WHERE NOT deleted AND CONCAT_WS(' ',attribute,value) REGEXP '[\\x{4e00}-\\x{9fff}]') AS attr,
 (SELECT COUNT(*) FROM litemall_goods_product WHERE NOT deleted AND specifications REGEXP '[\\x{4e00}-\\x{9fff}]') AS prod_spec,
 (SELECT COUNT(*) FROM litemall_cart WHERE specifications REGEXP '[\\x{4e00}-\\x{9fff}]') AS cart_spec,
 (SELECT COUNT(*) FROM litemall_order_goods WHERE specifications REGEXP '[\\x{4e00}-\\x{9fff}]') AS og_spec,
 (SELECT COUNT(*) FROM litemall_system WHERE CONCAT_WS(' ',key_name,key_value) REGEXP '[\\x{4e00}-\\x{9fff}]') AS sys,
 (SELECT COUNT(*) FROM litemall_comment WHERE NOT deleted AND content REGEXP '[\\x{4e00}-\\x{9fff}]') AS comments_pending;
