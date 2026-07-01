-- ==============================================================
-- StandMarket 电商数据丰富脚本
-- 生成日期: 2026-06-29
-- 内容:
--   1. 为后续订单添加地址簿数据
--   2. 为全部商品生成丰富的中文描述（UPDATE）
--   3. 生成测试订单及订单明细（INSERT）
-- ==============================================================

-- ==============================================================
-- Part 1: 地址簿数据（用于测试订单）
-- ==============================================================
INSERT INTO `address_book` (`user_id`, `consignee`, `sex`, `phone`, `province_code`, `province_name`, `city_code`, `city_name`, `district_code`, `district_name`, `detail`, `label`, `is_default`)
VALUES
(10, '李三', '男', '13800138001', '110000', '北京市', '110100', '北京市', '110105', '朝阳区', '建国路88号SOHO现代城A座1201', '家', 1),
(11, '李四', '女', '13800138002', '310000', '上海市', '310100', '上海市', '310101', '黄浦区', '南京东路100号外滩中心15楼', '公司', 1),
(12, '王五', '男', '13800138003', '440000', '广东省', '440300', '深圳市', '440305', '南山区', '科技园南区粤海街道18号', '家', 1);

-- ==============================================================
-- Part 2: 商品描述丰富（共74条）
-- ==============================================================

-- === 分类1: 上衣 (13件) ===

UPDATE product SET description = '采用新疆长绒棉，200g高克重面料，手感扎实不易变形。标准微落肩版型，不挑身材，日常通勤、周末出街皆可胜任。搭配牛仔裤或休闲短裤都利落有型。冷水洗涤，建议反洗反晒以保持领口挺括。' WHERE id = 1;

UPDATE product SET description = '60支长绒棉府绸面料，质地细腻微弹，挺括有型不易皱。修身的意式剪裁，肩线利落，适合商务通勤及正式会议场合。可搭配西裤打造精英造型，亦可卷袖搭配卡其裤演绎休闲绅士风。建议干洗或低温熨烫。' WHERE id = 12;

UPDATE product SET description = '甄选天丝棉混纺面料，触感凉滑亲肤，自带垂坠感。宽松落肩版型搭配古巴领设计，清爽利落，夏季通勤、休闲度假一件搞定。搭配亚麻短裤或九分西裤皆可。机洗轻柔模式，不可漂白。' WHERE id = 13;

UPDATE product SET description = '320g加厚抓绒面料，外层棉质毛圈内里拉绒，保暖性出色。OVERSIZE落肩廓形剪裁，街头感十足，春秋单穿或冬季叠穿外套均可。搭配工装裤和板鞋即出潮流造型。建议翻面机洗，避免高温烘干。' WHERE id = 14;

UPDATE product SET description = '美利奴羊毛混纺纱线，手感软糯轻盈，亲肤不扎人。修身微弹版型，贴合身形线条流畅，春秋过渡季的衣橱必备。可内搭衬衫外穿通勤，也可单穿搭配半身裙。建议手洗或羊毛模式机洗，平铺晾干。' WHERE id = 15;

-- === 分类1: 上衣（续） ===

UPDATE product SET description = '超级好看又安全的反光面料制成，夜间出行辨识度极高。胸口内置隐藏式口袋，可放置贵重物品。H型直筒版型不挑身形，休闲与防护兼具。搭配深色裤装更突显反光效果。建议单独洗涤，不可使用柔顺剂。' WHERE id = 99;

UPDATE product SET description = '纯棉精梳纱圆领T恤，手感柔软透气，含棉量100%。基础版型干净利落，领口采用本布包边工艺不易变形。衣柜里的万能打底衫，单穿叠穿皆出色。搭配所有下装无压力。40度以下机洗，不可长时间浸泡。' WHERE id = 101;

UPDATE product SET description = '紧密赛络纺棉质面料，V领深度恰到好处，视觉拉长颈部线条显脸小。微收腰版型修饰腰腹曲线，单穿或内搭西装外套皆高级。搭配高腰裤或A字裙打造日常精致造型。建议套袋机洗防勾丝。' WHERE id = 102;

UPDATE product SET description = '珠地棉质POLO衫，面料立体有肌理感，吸湿排汗性能佳。经典的二粒扣翻领设计，版型挺括不松垮，商务休闲两相宜。搭配休闲裤或牛仔裤参加周末聚会自在得体。冷水手洗最佳，机洗请套袋。' WHERE id = 103;

UPDATE product SET description = '亚麻棉混纺织物，天然褶皱感自带复古调性。亨利领半开襟设计搭配贝壳纽扣，门襟细节考究。宽松直筒版型舒适自在，文艺复古风格轻松驾驭。搭配卡其裤或牛仔裤营造法式慵懒感。亚麻面料建议手洗，微皱为正常特性。' WHERE id = 104;

UPDATE product SET description = '精梳棉双面布面料，厚度适中四季可穿。标准的H型直筒版型，下摆圆弧设计修饰腰臀。春秋单穿、冬季作为打底皆宜，实用百搭不出错。搭配衬衫叠穿露出领子更显层次。多次洗涤后依然保持版型不易松懈。' WHERE id = 105;

UPDATE product SET description = '380g高克重毛圈面料内里抓绒，温暖厚实挡风锁温。圆领设计简洁利落，罗纹下摆与袖口加固耐用。宽松版型可内搭衬衫或亨利衫，冬日叠穿羽绒服也自如。搭配束脚运动裤+老爹鞋打造休闲运动风。建议反洗反晒。' WHERE id = 106;

UPDATE product SET description = '100%山羊绒材质，手感如云朵般轻柔保暖。合身微宽松版型，是春秋冬三季的高级感叠穿利器。单穿配阔腿裤通勤优雅，做外套内搭露出领口更显品位。建议干洗或专用羊毛洗涤剂手洗，平铺阴干不可悬挂。' WHERE id = 138;

-- === 分类2: 外套 (8件) ===

UPDATE product SET description = '经典原色丹宁面料，11.5oz磅数适中四季皆宜。复刻Levi＇s 501版型改良，落肩设计更添休闲感。春秋两季主力外套，搭配T恤和工装靴即美式复古。初次建议脱浆后再穿着。减少洗涤频率以养出自然色落。' WHERE id = 16;

UPDATE product SET description = '锦纶面料经防泼水处理，内层透气网眼衬里轻便有型。简约连帽设计搭配弹力下摆，活动自如不拘束。城市通勤、周末出游、户外徒步均可从容应对。搭配连帽卫衣和束脚裤打造层次穿搭。机洗后自然晾干即可。' WHERE id = 17;

UPDATE product SET description = '高支羊毛混纺面料，手感细腻光泽柔和，里衬为全粘胶亲肤里布。经典的平驳领单排两粒扣西装剪裁，收腰版型勾勒肩背线条。商务会议、面试、晚宴等正式场合的不二之选。建议干洗，悬挂存放使用西装袋。' WHERE id = 18;

UPDATE product SET description = '90%白鹅绒填充，蓬松度700+，锁绒工艺杜绝跑毛。高密防风面料配合可拆卸毛领，-20℃亦能从容应对。中长款H型版型包容各种内搭厚度，是极寒地区的过冬刚需。不可水洗，建议专业干洗后拍打恢复蓬松。' WHERE id = 19;

UPDATE product SET description = '双面斜纹面料，内层复贴防水膜，中长款廓形剪裁气场十足。双排扣设计搭配可调节腰带，系带优雅散开洒脱。春秋雨季单穿、初冬叠穿西装外套皆可。搭配高领毛衣和长靴打造韩剧女主风。专业防水涂层保养可延长寿命。' WHERE id = 107;

UPDATE product SET description = 'PU合成革面料经压纹处理呈现立体荔枝纹质感，硬朗有型。机车款非对称拉链设计搭配金属铆钉细节，摇滚气质拉满。合身短款版型优化身材比例，搭配紧身裤和马丁靴出街回头率爆表。用湿布擦拭保养，避免暴晒导致开裂。' WHERE id = 108;

UPDATE product SET description = '高密防钻绒面料填充中空棉，轻盈且保暖不压身。绗缝格纹线迹定型，水洗后也不易跑棉。立领设计防风护颈，适合南方湿冷天气通勤穿着。搭配高领毛衣或连帽卫衣皆可。局部油渍用湿巾擦拭即可，尽量少洗。' WHERE id = 109;

UPDATE product SET description = '三层压胶防水透湿面料，防水指数10000mm，接缝全压胶处理。可调节风帽兼容头盔，腋下透气拉链设计科学。硬壳冲锋衣结构版型，春夏秋三季户外运动必备。搭配速干T恤和登山裤挑战全天候户外。使用专用防水剂定期做DWR涂层保养。' WHERE id = 110;

-- === 分类3: 裙子 (7件) ===

UPDATE product SET description = '高支棉质混纺面料，手感柔滑垂顺，内衬亲肤舒适。法式收腰A字伞裙版型，勾勒腰线同时修饰腿部线条。约会、下午茶、度假拍照皆出片。搭配尖头平底鞋或玛丽珍鞋更显优雅。建议手洗或套袋机洗，不可拧干。' WHERE id = 20;

UPDATE product SET description = 'TR面料挺括有垂感，抗皱易打理。经典直筒半身裙版型，高腰设计拉长下半身比例。通勤日常两不误，搭配衬衫或雪纺上衣皆高级。搭配细腰带点缀更添精致感。冷水轻柔机洗，悬挂阴干避免暴晒褪色。' WHERE id = 21;

UPDATE product SET description = '雪纺面料印染小碎花图案，花色清新淡雅不易褪色。内衬亲肤防透，外层飘逸轻盈。松紧腰设计穿脱方便，度假出游必备战袍。搭配草编包和平底凉鞋氛围感拉满。30度以下手洗，不可机洗脱水以防勾丝。' WHERE id = 22;

UPDATE product SET description = '高腰A字廓形加挺括梭织面料，对梨形身材极其友好。侧隐拉链整洁利落，裙长过膝端庄得体。职场通勤、见家长、参加婚礼等偏正式场合首选。搭配真丝衬衫或修身针织衫都能穿出气质。建议干洗以保持版型挺括。' WHERE id = 111;

UPDATE product SET description = '涤纶面料经高温压褶定型，褶裥锋利持久不易散。高腰短裙设计搭配可调节腰扣，学院风减龄利器。搭配Polo衫或卫衣穿出青春活力感。搭配乐福鞋和长筒袜打造日系JK风格。翻面套袋机洗，避免与其他衣物缠绕。' WHERE id = 112;

UPDATE product SET description = '高弹力针织面料含莱卡成分，紧身包臀曲线毕露。后中隐形拉链设计简约利落，职场上衣搭配极致优雅。约会或派对场合凸显女性魅力。配高跟鞋和精致手包气场全开。建议手洗，不可烘干以防弹性纤维受损。' WHERE id = 113;

UPDATE product SET description = '双层雪纺面料飘逸轻盈，内层防透衬裙设计贴心。碎褶松紧腰头舒适不勒腰，裙摆及脚踝仙气十足。夏日海边度假、草坪婚礼、文艺拍照必备。搭配吊带背心和开衫叠穿更丰富。手洗轻柔拧干，悬挂晾晒自然垂顺。' WHERE id = 114;

-- === 分类4: 牛仔裤 (6件) ===

UPDATE product SET description = '98%棉+2%氨纶弹力牛仔布，穿着自在不紧绷。中腰修身直筒版型，修饰腿型不挑人，经典蓝色水洗百搭不过时。可搭配所有上衣，从T恤到衬衫随意切换。建议翻面冷水洗涤，初次穿着前先脱浆定色。' WHERE id = 23;

UPDATE product SET description = '9.5oz微弹牛仔布，膝盖破洞及猫须水洗效果自然立体。中低腰小脚版型，裤脚微收显腿长。街头潮流玩家的穿搭利器，搭配短靴或板鞋增加不羁感。注意手撕破洞会随穿着自然扩大，属于设计特性。' WHERE id = 24;

UPDATE product SET description = '100%全棉原色牛仔布，硬挺有型，经典直筒中高腰剪裁。不挑腿型的万能直筒，修饰X型腿和O型腿效果出色。搭配帆布鞋或沙漠靴打造复古工装风。养牛玩家首选，多穿少洗逐步养成个人色落纹路。' WHERE id = 25;

UPDATE product SET description = '棉氨混纺弹力牛仔面料，手感柔软包裹感好。高腰紧身小脚版型，提臀收腹效果明显。秋冬搭配长靴或短靴露出膝盖腿环细节。可搭配oversize卫衣或修身针织衫。建议冷水翻洗，不可用漂白剂以免弹力纤维老化。' WHERE id = 115;

UPDATE product SET description = '16oz重磅原色赤耳丹宁面料，复古韵味十足。宽松直筒阔腿版型，裤脚卷边露出赤耳布边细节。搭配短款上衣和厚底鞋打造复古街头造型。养牛深度玩家之选，建议半年以上再首次洗涤。' WHERE id = 116;

UPDATE product SET description = '弹力棉质牛仔面料含35%天丝成分，手感更柔滑。九分长度恰好露出脚踝，搭配各种短靴和乐福鞋都时髦。中腰修身锥形版型，大腿宽松小腿收窄的万金油裤型。春夏季通勤裤装首选。常规机洗即可，翻面洗涤护色。' WHERE id = 117;

-- === 分类5: 休闲裤 (7件) ===

UPDATE product SET description = '棉麻混纺面料透气性极佳，自带天然褶皱肌理。宽松直筒束脚裤版型，松紧腰抽绳设计穿脱自如。夏日居家、散步、遛弯的最佳伴侣。搭配白T和人字拖就是松弛感本松。可机洗，麻面料微皱为天然质感。' WHERE id = 26;

UPDATE product SET description = '涤纶速干面料搭配网眼内衬，吸湿排汗效果出色。针织束脚运动裤版型，弹力腰围贴合不勒。健身撸铁、户外慢跑、居家休息一裤多用。搭配连帽卫衣和跑鞋完成整套运动风。机洗速干无需熨烫。' WHERE id = 27;

UPDATE product SET description = '高支弹力羊毛混纺面料，手感顺滑垂坠有质感。中腰直筒西裤剪裁，裤中线挺括彰显商务专业度。日常通勤、商务会议、出差见客户的标准配置。搭配衬衫和皮鞋利落干练。建议干洗以保持裤线挺括延长穿着寿命。' WHERE id = 28;

UPDATE product SET description = '斜纹棉卡其布面料，工装风格耐磨耐穿。多口袋设计兼具实用与造型感，裤脚抽绳可收束成灯笼裤型。城市机能风格穿搭利器，搭配马丁靴或老爹鞋更出效果。机洗时拉上所有拉链避免勾扯其他衣物。' WHERE id = 118;

UPDATE product SET description = '高密涤棉面料加多个立体口袋，功能性拉满。宽松工装直筒版型，膝部加厚补丁设计更耐磨。户外露营、战术通勤场景实用性一流。搭配短T和战术靴打造硬核工装风。建议翻洗翻晒以保持口袋立体感。' WHERE id = 119;

UPDATE product SET description = '棉质毛圈布面料，内里拉绒触感温暖。针织束脚裤版型弹力腰围加抽绳，运动感与休闲感兼备。秋冬季节日常出街的舒适之选，搭配卫衣和板鞋轻松出门。机洗时翻面并拉平晾晒可减少起球。' WHERE id = 120;

UPDATE product SET description = '头层牛皮革裁切，皮质细腻光泽低调。3.5cm适中宽度，针扣设计简洁经典。打孔位充足可灵活调节，适配各种裤腰。商务西裤和休闲牛仔裤均可搭配提升质感。避免长时间弯折存放，定期使用皮具护理油保养。' WHERE id = 137;

-- === 分类6: 运动鞋 (7件) ===

UPDATE product SET description = 'EVA中底搭配后掌气垫，每一步都有明显回弹感。针织飞织鞋面透气轻盈包裹好，鞋底橡胶纹理抓地力强。适合5-10公里日常晨跑和健身房训练。搭配运动袜和压缩裤提升运动表现。跑后请取出鞋垫通风阴干。' WHERE id = 45;

UPDATE product SET description = '全掌Air气垫搭配编织飞线鞋面，缓震回弹性能出色。高帮鞋型包裹支撑性极佳，脚踝锁定防崴伤。室内外球场均可驾驭，起跳落地缓冲明显。建议搭配短袜露出脚踝，打球时务必系紧鞋带。赛后清洁用软毛刷蘸清水擦拭即可。' WHERE id = 57;

UPDATE product SET description = 'EVA二次发泡中底轻量化设计，单只仅约250g。MONO纱鞋面透气网孔清爽不闷脚。3M反光细节夜跑更安全。城市慢跑健走和日常通勤皆可。搭配运动束脚裤或短裤都合适。不可暴晒，洗净用纸包裹吸湿阴干。' WHERE id = 96;

UPDATE product SET description = '网面拼接超纤革鞋面，兼顾透气与支撑性。EVA中底加橡胶外底防滑耐磨，轻量化设计日常出街无负担。散步购物、周末遛弯的舒适伴侣。搭配九分裤或短裤露出鞋帮更利落。脏了用湿布擦拭即可，不可长时间浸泡。' WHERE id = 121;

UPDATE product SET description = '二层牛皮革拼接帆布鞋面，经典硫化鞋底工艺。低帮板鞋造型简约百搭，鞋舌刺绣品牌Logo细节。滑板训练、城市出行皆可，搭配牛仔裤或工装短裤一秒街头。注意硫化鞋底遇水较滑，雨天谨慎穿着。鞋面可用软毛刷刷洗。' WHERE id = 122;

UPDATE product SET description = '分区密度EVA中底，前掌侧重灵活性后跟侧重缓震。透气三层网布鞋面，鞋头防踢橡胶包裹保护脚趾。综合体能训练、CrossFit、器械健身的万金油鞋款。搭配速干运动袜保持足部干爽。训练后及时清理鞋底灰尘。' WHERE id = 123;

UPDATE product SET description = '精梳棉面料透气吸汗，舒适贴合不勒脚。加固袜跟防脱落设计，弹力罗纹袜口不紧绷。多色混合装满足日常穿搭需求，跑步健身日常皆适用。搭配运动鞋板鞋均可。40度温水机洗，不可使用漂白剂。' WHERE id = 139;

-- === 分类7: 皮鞋 (6件) ===

UPDATE product SET description = '头层牛皮鞋面皮质光泽自然，布洛克雕花细节精致复古。EVA鞋垫软弹舒适，橡胶大底防滑耐磨。日常通勤、约会聚餐、城市漫步皆可。搭配卡其裤和休闲衬衫提升气质。定期使用鞋乳护理，雨天尽量避免穿着。' WHERE id = 46;

UPDATE product SET description = '磨砂牛皮革靴筒，内里加绒保暖锁温。侧拉链设计穿脱方便，粗跟方头靴型稳重有型。搭配大衣和牛仔裤或毛呢短裙都时髦。冬季出街回头率满分。沾水后用干布擦干放置阴干，不可暴晒，定期喷涂防水喷雾。' WHERE id = 47;

UPDATE product SET description = '头层小牛皮鞋面，镜面抛光工艺光泽感强。经典三孔系带牛津鞋型，固特异贴边工艺耐穿可换底。商务会议、正式宴会、面试等严肃场合必备正装鞋。建议搭配深色正装西裤。使用马毛刷除尘+鞋蜡抛光保养，用鞋楦保持鞋型。' WHERE id = 56;

UPDATE product SET description = '头层牛皮搭配弹力松紧带侧边，一脚蹬设计懒人福音。便士乐福鞋经典造型，鞋面马衔扣金属装饰低调精致。通勤休闲无缝切换，搭配九分西裤或卡其裤展现意式优雅。光面皮质用湿布轻擦即可，注意防止皮质干裂。' WHERE id = 124;

UPDATE product SET description = '小牛皮鞋面经手工雕花冲孔处理，优雅复古气息浓郁。固特异沿条工艺可复底，皮质鞋垫越穿越贴合脚型。英伦绅士风格穿搭核心单品。搭配羊毛西裤和礼帽出席正式场合从容得体。建议交替穿着让皮革休息恢复。' WHERE id = 125;

UPDATE product SET description = '磨砂牛皮革靴面质感高级，侧边弹力拼接设计穿脱便捷。切尔西经典靴型搭配尖头楦型修饰脚型。搭配紧身牛仔裤或毛呢短裙皆可游走于休闲与时尚之间。春秋冬三季均可穿着。清洁时使用生胶刷顺着一个方向刷拭。' WHERE id = 126;

-- === 分类8: 帽子 (8件) ===

UPDATE product SET description = '纯棉斜纹面料挺括有型，六片拼接结构贴合头型。可调节金属搭扣+透气孔设计，运动户外佩戴舒适不闷汗。跑步健身、日常通勤遮阳一帽搞定。搭配T恤和运动装活力十足。冷水手洗按压吸水，不可机洗以防变形。' WHERE id = 48;

UPDATE product SET description = '棉质水洗面料触感柔软自然做旧感，宽檐设计遮阳面积大。帽围内置抽绳可调节松紧，折叠后方便收纳携带。海边度假、户外钓鱼、音乐节的标配配饰。搭配碎花裙或宽大T恤慵懒随性。晾晒时用帽撑保持圆形帽檐。' WHERE id = 49;

UPDATE product SET description = '100%美利奴羊毛材质，竖条纹针织组织弹性佳。翻边设计增加层次感，顶部毛球装饰增添可爱趣味。深冬保暖必备单品，搭配大衣或羽绒服温暖时髦两不误。建议手洗后平铺晾干，不可悬挂以免拉长变形。' WHERE id = 50;

UPDATE product SET description = '羊毛混纺面料挺括有型，法式贝雷帽经典圆形版型。可调节内圈松紧带适配不同头围，侧戴或正戴皆可打造不同风格。秋冬穿搭的点睛配饰，搭配大衣气质文艺。建议干洗或低温手洗，用帽撑定型晾干。' WHERE id = 127;

UPDATE product SET description = '涤纶速干面料UPF50+防紫外线认证，宽檐设计全方位防晒。帽顶透气网眼加速散热，附赠防风绳户外不易被吹落。夏日海边度假、户外徒步、园艺劳作的防晒利器。搭配墨镜和防晒衣全套防护。冷水冲洗即可，避免机洗破坏帽型。' WHERE id = 128;

UPDATE product SET description = '纯棉面料经预缩水处理，弯檐设计修饰脸型。前方刺绣Logo精致立体，后背金属扣可调节围度。街头潮流穿搭的万能配饰，搭配卫衣和板鞋街头感十足。帽檐弯曲弧度可按喜好手动调整。建议用帽子清洁湿巾局部擦拭。' WHERE id = 129;

UPDATE product SET description = '速干涤纶面料加导汗带设计，吸湿排汗效果出色。弹力带贴合头部运动不滑动，轻薄无感佩戴舒适。跑步、瑜伽、健身、篮球等运动时有效防止汗水流入眼睛。搭配运动套装提升专业感。清水冲洗拧干即刻恢复干爽。' WHERE id = 136;

UPDATE product SET description = 'TAC偏光镜片UV400防护，有效削减路面和水面反光。TR90超轻镜框佩戴舒适不压鼻，镜腿弹簧设计包容不同脸型。开车、钓鱼、滑雪、日常出行皆可配备。搭配棒球帽和休闲服整套出街造型完整。附赠镜布擦拭镜片不可干擦。' WHERE id = 140;

-- === 分类9: 包包 (6件) ===

UPDATE product SET description = 'PU面料经过油蜡处理呈现复古质感，金属链条+皮质肩带两用设计。翻盖磁扣开合便捷，内容量刚好装下手机口红等随身小物。约会逛街、周末出行的轻便之选。搭配连衣裙或西装外套都是点睛配饰。用湿布轻擦保养，避免接触酒精等溶剂。' WHERE id = 35;

UPDATE product SET description = '头层牛皮对贴工艺，手掌大小精致小巧。多个卡位+大钞位分区合理，零钱拉链格安全实用。商务场合和日常出行的便携收纳神器。搭配手拿或放入通勤包中皆可。定期使用皮革护理霜滋养，避免暴晒导致干裂变色。' WHERE id = 36;

UPDATE product SET description = '二层牛皮面料柔软耐用，大容量设计可放入13寸笔记本和A4文件。手提+肩背两用设计，金属铆钉加固受力点。通勤上班、出差短旅一包搞定。搭配通勤西装或风衣都显干练。注意不要超重装载以免提手受力变形。' WHERE id = 51;

UPDATE product SET description = '高密度尼龙面料耐磨防泼水，多隔层收纳设计科学合理。S型加厚肩带减压不勒肩，背部透气垫片夏季不闷汗。城市通勤、短期出差、周末出游的实用背包。搭配休闲运动装即可。机洗时建议套入洗衣袋保护。' WHERE id = 130;

UPDATE product SET description = '12oz纯棉帆布厚实耐用，水洗做旧质感文艺复古。大开口设计拿取方便，内置小袋收纳钥匙手机。通勤装电脑、去图书馆、买菜购物一包多用。搭配棉麻衬衫和帆布鞋慵懒随性。整体机洗后可产生自然做旧褶皱效果。' WHERE id = 131;

UPDATE product SET description = '二层牛皮面料手感柔软细腻，可调节肩带斜挎单肩两用。风琴式隔层设计容量惊人却身形纤薄。日常通勤手机包、逛街轻便小包皆可胜任。搭配裙装或西装外套倍显精致。不使用时应填充纸团放入防尘袋保存以防变形。' WHERE id = 132;

-- === 分类10: 手表 (6件) ===

UPDATE product SET description = '1.43英寸AMOLED屏幕显示细腻，AOD常亮显示时间。心率血氧睡眠监测全面，GPS定位精准记录运动轨迹。蓝牙通话+消息提醒解放手机，续航14天省心省力。运动健身和日常通勤皆可佩戴。建议使用专用充电器，避免在桑拿房等高温环境佩戴。' WHERE id = 33;

UPDATE product SET description = '316L精钢表壳搭配矿物强化玻璃表镜，质感通透耐磨。大三针日期显示多功能表盘，皮质表带柔软贴手。商务正装和休闲穿搭皆可搭配，提升整体穿搭品位。佩戴时避免与水接触，皮质表带注意防汗。' WHERE id = 34;

UPDATE product SET description = '兔兔那么可爱，怎么能戴兔兔手表呢？萌趣兔子造型表盘搭配彩色硅胶表带，少女心爆棚。30米生活防水日常洗手无惧。送给小朋友或闺蜜的可爱礼物。不可热水淋浴或蒸桑拿。' WHERE id = 100;

UPDATE product SET description = '进口全自动机械机芯，透底设计可观精密运作之美。316L精钢表壳蓝宝石镜面硬度极高不易刮花。棕色真皮表带搭配蝴蝶扣，复古绅士格调拉满。重要场合彰显品位的实力担当。自动机械表每日约±15秒误差属正常范围。' WHERE id = 133;

UPDATE product SET description = 'TPU亲肤表带搭配轻量树脂表壳，佩戴轻盈无感。100米防水深度游泳潜水无压力，四键操作计时秒表功能齐全。学生党运动达人的性价比之选。搭配运动装出行活力阳光。电池续航约2年，更换电池请找专业维修点。' WHERE id = 134;

UPDATE product SET description = '日本石英机芯走时精准月差±20秒，7mm超薄表壳贴合手腕。银色拉丝表盘搭配极简刻度，蓝宝石镜面通透防刮。通勤穿搭的点睛配饰，简约而不简单。搭配正装衬衫袖口若隐若现方显品位。电池续航约3年，更换电池时请同时检查防水圈。' WHERE id = 135;

-- ==============================================================
-- Part 3: 测试订单数据
-- ==============================================================

-- 用户ID 10: 李三  11: 李四  12: 王五
-- 地址簿ID: 20(李三), 21(李四), 22(王五)

-- 订单1: 用户10 李三 - 待付款
INSERT INTO `orders` (`id`, `number`, `status`, `user_id`, `address_book_id`, `order_time`, `checkout_time`, `pay_method`, `pay_status`, `amount`, `remark`, `phone`, `address`, `user_name`, `consignee`, `cancel_reason`, `rejection_reason`, `cancel_time`, `estimated_delivery_time`, `delivery_status`, `delivery_time`, `shipping_fee`, `seckill_activity_id`, `seckill_coupon_id`, `is_seckill`, `seckill_price`, `original_price`)
VALUES
(1001, '202606291001001', 1, 10, 20, '2026-06-29 10:00:00', NULL, 1, 0, 307.00, '请尽快发货，周末在家', '13800138001', '北京市朝阳区建国路88号SOHO现代城A座1201', '李三', '李三', NULL, NULL, NULL, '2026-07-01 10:00:00', 1, NULL, 0, NULL, NULL, 0, NULL, NULL);

-- 订单2: 用户11 李四 - 待发货
INSERT INTO `orders` (`id`, `number`, `status`, `user_id`, `address_book_id`, `order_time`, `checkout_time`, `pay_method`, `pay_status`, `amount`, `remark`, `phone`, `address`, `user_name`, `consignee`, `cancel_reason`, `rejection_reason`, `cancel_time`, `estimated_delivery_time`, `delivery_status`, `delivery_time`, `shipping_fee`, `seckill_activity_id`, `seckill_coupon_id`, `is_seckill`, `seckill_price`, `original_price`)
VALUES
(1002, '202606291002002', 2, 11, 21, '2026-06-29 11:30:00', '2026-06-29 11:30:15', 2, 1, 398.00, '', '13800138002', '上海市黄浦区南京东路100号外滩中心15楼', '李四', '李四', NULL, NULL, NULL, '2026-07-02 11:30:00', 1, NULL, 0, NULL, NULL, 0, NULL, NULL);

-- 订单3: 用户12 王五 - 已发货
INSERT INTO `orders` (`id`, `number`, `status`, `user_id`, `address_book_id`, `order_time`, `checkout_time`, `pay_method`, `pay_status`, `amount`, `remark`, `phone`, `address`, `user_name`, `consignee`, `cancel_reason`, `rejection_reason`, `cancel_time`, `estimated_delivery_time`, `delivery_status`, `delivery_time`, `shipping_fee`, `seckill_activity_id`, `seckill_coupon_id`, `is_seckill`, `seckill_price`, `original_price`)
VALUES
(1003, '202606291003003', 3, 12, 22, '2026-06-28 09:15:00', '2026-06-28 09:15:30', 1, 1, 777.00, '周末不在家请周一到', '13800138003', '广东省深圳市南山区科技园南区粤海街道18号', '王五', '王五', NULL, NULL, NULL, '2026-06-30 09:15:00', 0, '2026-06-29 08:00:00', 0, NULL, NULL, 0, NULL, NULL);

-- 订单4: 用户10 李三 - 已完成
INSERT INTO `orders` (`id`, `number`, `status`, `user_id`, `address_book_id`, `order_time`, `checkout_time`, `pay_method`, `pay_status`, `amount`, `remark`, `phone`, `address`, `user_name`, `consignee`, `cancel_reason`, `rejection_reason`, `cancel_time`, `estimated_delivery_time`, `delivery_status`, `delivery_time`, `shipping_fee`, `seckill_activity_id`, `seckill_coupon_id`, `is_seckill`, `seckill_price`, `original_price`)
VALUES
(1004, '202606281004004', 4, 10, 20, '2026-06-25 14:00:00', '2026-06-25 14:00:20', 1, 1, 947.00, '', '13800138001', '北京市朝阳区建国路88号SOHO现代城A座1201', '李三', '李三', NULL, NULL, NULL, '2026-06-27 14:00:00', 1, '2026-06-26 16:30:00', 0, NULL, NULL, 0, NULL, NULL);

-- 订单5: 用户11 李四 - 已取消
INSERT INTO `orders` (`id`, `number`, `status`, `user_id`, `address_book_id`, `order_time`, `checkout_time`, `pay_method`, `pay_status`, `amount`, `remark`, `phone`, `address`, `user_name`, `consignee`, `cancel_reason`, `rejection_reason`, `cancel_time`, `estimated_delivery_time`, `delivery_status`, `delivery_time`, `shipping_fee`, `seckill_activity_id`, `seckill_coupon_id`, `is_seckill`, `seckill_price`, `original_price`)
VALUES
(1005, '202606271005005', 5, 11, 21, '2026-06-27 16:45:00', NULL, 1, 0, 478.00, '', '13800138002', '上海市黄浦区南京东路100号外滩中心15楼', '李四', '李四', '不小心下错尺码了，重新下单', NULL, '2026-06-27 17:00:00', NULL, 1, NULL, 0, NULL, NULL, 0, NULL, NULL);

-- 订单6: 用户12 王五 - 已完成
INSERT INTO `orders` (`id`, `number`, `status`, `user_id`, `address_book_id`, `order_time`, `checkout_time`, `pay_method`, `pay_status`, `amount`, `remark`, `phone`, `address`, `user_name`, `consignee`, `cancel_reason`, `rejection_reason`, `cancel_time`, `estimated_delivery_time`, `delivery_status`, `delivery_time`, `shipping_fee`, `seckill_activity_id`, `seckill_coupon_id`, `is_seckill`, `seckill_price`, `original_price`)
VALUES
(1006, '202606241006006', 4, 12, 22, '2026-06-24 20:00:00', '2026-06-24 20:00:10', 2, 1, 956.00, '放快递柜即可', '13800138003', '广东省深圳市南山区科技园南区粤海街道18号', '王五', '王五', NULL, NULL, NULL, '2026-06-26 20:00:00', 1, '2026-06-25 14:20:00', 0, NULL, NULL, 0, NULL, NULL);

-- 订单7: 用户10 李三 - 待发货
INSERT INTO `orders` (`id`, `number`, `status`, `user_id`, `address_book_id`, `order_time`, `checkout_time`, `pay_method`, `pay_status`, `amount`, `remark`, `phone`, `address`, `user_name`, `consignee`, `cancel_reason`, `rejection_reason`, `cancel_time`, `estimated_delivery_time`, `delivery_status`, `delivery_time`, `shipping_fee`, `seckill_activity_id`, `seckill_coupon_id`, `is_seckill`, `seckill_price`, `original_price`)
VALUES
(1007, '202606291007007', 2, 10, 20, '2026-06-29 08:30:00', '2026-06-29 08:30:05', 1, 1, 707.00, '送到前台即可', '13800138001', '北京市朝阳区建国路88号SOHO现代城A座1201', '李三', '李三', NULL, NULL, NULL, '2026-07-01 08:30:00', 1, NULL, 0, NULL, NULL, 0, NULL, NULL);

-- ==============================================================
-- Part 4: 订单明细数据
-- ==============================================================

-- 订单1001 明细（待付款）: 纯棉T恤 x1 + 牛仔裤 x1 + 棒球帽 x1
INSERT INTO `order_detail` (`id`, `name`, `image`, `order_id`, `product_id`, `combination_id`, `sku_info`, `number`, `amount`, `is_seckill`, `seckill_price`, `original_price`)
VALUES
(2001, '纯棉T恤', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=cotton%20t-shirt&image_size=square', 1001, 1, NULL, '颜色:白色,尺码:M', 1, 99.00, 0, NULL, 99.00),
(2002, '牛仔裤', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=jeans&image_size=square', 1001, 23, NULL, '颜色:中蓝,尺码:32', 1, 149.00, 0, NULL, 149.00),
(2003, '棒球帽', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=baseball%20cap&image_size=square', 1001, 48, NULL, '颜色:黑色,尺码:均码', 1, 59.00, 0, NULL, 59.00);

-- 订单1002 明细（待发货）: 连衣裙 x1 + 斜挎包 x1
INSERT INTO `order_detail` (`id`, `name`, `image`, `order_id`, `product_id`, `combination_id`, `sku_info`, `number`, `amount`, `is_seckill`, `seckill_price`, `original_price`)
VALUES
(2004, '连衣裙', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=dress&image_size=square', 1002, 20, NULL, '颜色:米色,尺码:M', 1, 199.00, 0, NULL, 199.00),
(2005, '斜挎包', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=crossbody%20bag&image_size=square', 1002, 35, NULL, '颜色:棕色', 1, 199.00, 0, NULL, 199.00);

-- 订单1003 明细（已发货）: 羽绒服 x1 + 毛线帽 x2
INSERT INTO `order_detail` (`id`, `name`, `image`, `order_id`, `product_id`, `combination_id`, `sku_info`, `number`, `amount`, `is_seckill`, `seckill_price`, `original_price`)
VALUES
(2006, '羽绒服', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=down%20jacket&image_size=square', 1003, 19, NULL, '颜色:黑色,尺码:L', 1, 599.00, 0, NULL, 599.00),
(2007, '毛线帽', 'https://java00001-ai.oss-cn-beijing.aliyuncs.com/99c88f80-40fe-401b-9575-6d20e4dcae74.png', 1003, 50, NULL, '颜色:灰色,尺码:均码', 2, 178.00, 0, NULL, 89.00);

-- 订单1004 明细（已完成）: 长袖衬衫 x1 + 西裤 x1 + 皮鞋 x1
INSERT INTO `order_detail` (`id`, `name`, `image`, `order_id`, `product_id`, `combination_id`, `sku_info`, `number`, `amount`, `is_seckill`, `seckill_price`, `original_price`)
VALUES
(2008, '长袖衬衫', 'https://java00001-ai.oss-cn-beijing.aliyuncs.com/956ea2ec-54b6-4e9a-9406-0ef51bd41116.png', 1004, 12, NULL, '颜色:白色,尺码:40', 1, 199.00, 0, NULL, 199.00),
(2009, '西裤', 'https://java00001-ai.oss-cn-beijing.aliyuncs.com/489f37d7-0c84-4545-8db5-77c5cf8d6fa1.png', 1004, 28, NULL, '颜色:深灰,尺码:32', 1, 249.00, 0, NULL, 249.00),
(2010, '皮鞋', 'https://java00001-ai.oss-cn-beijing.aliyuncs.com/1aa36c16-5366-4920-a8bb-2d7f83d4f7e9.png', 1004, 56, NULL, '颜色:黑色,尺码:42', 1, 499.00, 0, NULL, 499.00);

-- 订单1005 明细（已取消）: 运动鞋 x1 + 腰带 x1
INSERT INTO `order_detail` (`id`, `name`, `image`, `order_id`, `product_id`, `combination_id`, `sku_info`, `number`, `amount`, `is_seckill`, `seckill_price`, `original_price`)
VALUES
(2011, '运动鞋', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=sports%20shoes&image_size=square', 1005, 96, NULL, '颜色:白色,尺码:43', 1, 399.00, 0, NULL, 399.00),
(2012, '腰带', 'https://picsum.photos/200/300?random=37', 1005, 137, NULL, '颜色:棕色,尺码:110cm', 1, 79.00, 0, NULL, 79.00);

-- 订单1006 明细（已完成）: 智能手表 x1 + 袜子 x3
INSERT INTO `order_detail` (`id`, `name`, `image`, `order_id`, `product_id`, `combination_id`, `sku_info`, `number`, `amount`, `is_seckill`, `seckill_price`, `original_price`)
VALUES
(2013, '智能手表', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=smart%20watch&image_size=square', 1006, 33, NULL, '颜色:黑色,尺码:42mm', 1, 899.00, 0, NULL, 899.00),
(2014, '袜子', 'https://picsum.photos/200/300?random=39', 1006, 139, NULL, '颜色:黑色,尺码:均码', 3, 57.00, 0, NULL, 19.00);

-- 订单1007 明细（待发货）: 碎花裙 x1 + 手提包 x1 + 太阳镜 x1
INSERT INTO `order_detail` (`id`, `name`, `image`, `order_id`, `product_id`, `combination_id`, `sku_info`, `number`, `amount`, `is_seckill`, `seckill_price`, `original_price`)
VALUES
(2015, '碎花裙', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=floral%20dress&image_size=square', 1007, 22, NULL, '颜色:蓝色碎花,尺码:M', 1, 249.00, 0, NULL, 249.00),
(2016, '手提包', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=handbag&image_size=square', 1007, 51, NULL, '颜色:米色', 1, 299.00, 0, NULL, 299.00),
(2017, '太阳镜', 'https://picsum.photos/200/300?random=40', 1007, 140, NULL, '颜色:黑色,尺码:均码', 1, 159.00, 0, NULL, 159.00);
