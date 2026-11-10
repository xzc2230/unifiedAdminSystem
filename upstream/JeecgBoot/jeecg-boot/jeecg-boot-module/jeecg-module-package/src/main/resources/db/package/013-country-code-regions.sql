-- Source dev 0f83f812: whitelistAreaLinkage.json (75 aliases, 49 regions).
-- Back up database first. Apply with application stopped; clear host dictionary cache after commit.
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS pkg_country_area_backup (resource_type VARCHAR(32) NOT NULL, resource_id BIGINT NOT NULL, original_areas TEXT, backed_up_at DATETIME NOT NULL, PRIMARY KEY(resource_type, resource_id));
CREATE TABLE IF NOT EXISTS pkg_country_dict_item_backup LIKE sys_dict_item;
SET @pkg_old_group_concat_max_len=@@SESSION.group_concat_max_len;
SET SESSION group_concat_max_len=65535;
START TRANSACTION;
INSERT IGNORE INTO pkg_country_dict_item_backup SELECT i.* FROM sys_dict_item i JOIN sys_dict d ON d.id=i.dict_id WHERE d.dict_code='app_area';
INSERT IGNORE INTO pkg_country_area_backup SELECT 'apps',id,areas,NOW() FROM pkg_app_manager;
INSERT IGNORE INTO pkg_country_area_backup SELECT 'statistics',id,sub_areas,NOW() FROM pkg_app_statistics;
CREATE TEMPORARY TABLE pkg_country_alias_stage (alias VARCHAR(100) COLLATE utf8mb4_bin PRIMARY KEY, code VARCHAR(2) NOT NULL);
INSERT INTO pkg_country_alias_stage (alias,code) VALUES ('中国','cn'),('香港','hk'),('澳门','mo'),('台湾','tw'),('美国','us'),('日本','jp'),('韩国','kr'),('新加坡','sg'),('英国','gb'),('德国','de'),('法国','fr'),('澳大利亚','au'),('加拿大','ca'),('印度','in'),('俄罗斯','ru'),('巴西','br'),('墨西哥','mx'),('西班牙','es'),('意大利','it'),('荷兰','nl'),('安道尔','ad'),('阿联酋','ae'),('阿富汗','af'),('安提瓜和巴布达','ag'),('安圭拉','ai'),('阿尔巴尼亚','al'),('亚美尼亚','am'),('安哥拉','ao'),('阿根廷','ar'),('奥地利','at'),('阿塞拜疆','az'),('波黑','ba'),('巴巴多斯','bb'),('孟加拉国','bd'),('比利时','be'),('布基纳法索','bf'),('保加利亚','bg'),('巴林','bh'),('贝宁','bj'),('百慕大','bm'),('文莱','bn'),('玻利维亚','bo'),('巴哈马','bs'),('不丹','bt'),('博茨瓦纳','bw'),('白俄罗斯','by'),('伯利兹','bz'),('刚果（金）','cd'),('中非','cf'),('刚果（布）','cg'),('瑞士','ch'),('科特迪瓦','ci'),('智利','cl'),('喀麦隆','cm'),('哥伦比亚','co'),('哥斯达黎加','cr'),('佛得角','cv'),('塞浦路斯','cy'),('捷克','cz'),('丹麦','dk'),('多米尼克','dm'),('多米尼加','do'),('阿尔及利亚','dz'),('厄瓜多尔','ec'),('爱沙尼亚','ee'),('埃及','eg'),('埃塞俄比亚','et'),('芬兰','fi'),('斐济','fj'),('密克罗尼西亚','fm'),('加蓬','ga'),('格林纳达','gd'),('格鲁吉亚','ge'),('加纳','gh'),('冈比亚','gm'),('几内亚','gn'),('希腊','gr'),('危地马拉','gt'),('几内亚比绍','gw'),('圭亚那','gy'),('洪都拉斯','hn'),('克罗地亚','hr'),('匈牙利','hu'),('印度尼西亚','id'),('爱尔兰','ie'),('以色列','il'),('伊拉克','iq'),('冰岛','is'),('牙买加','jm'),('约旦','jo'),('肯尼亚','ke'),('吉尔吉斯斯坦','kg'),('柬埔寨','kh'),('圣基茨和尼维斯','kn'),('科威特','kw'),('开曼群岛','ky'),('哈萨克斯坦','kz'),('老挝','la'),('黎巴嫩','lb'),('圣卢西亚','lc'),('列支敦士登','li'),('斯里兰卡','lk'),('利比里亚','lr'),('立陶宛','lt'),('卢森堡','lu'),('拉脱维亚','lv'),('利比亚','ly'),('摩洛哥','ma'),('摩纳哥','mc'),('摩尔多瓦','md'),('黑山','me'),('马达加斯加','mg'),('北马其顿','mk'),('马里','ml'),('缅甸','mm'),('蒙古','mn'),('毛里塔尼亚','mr'),('蒙特塞拉特','ms'),('马耳他','mt'),('毛里求斯','mu'),('马尔代夫','mv'),('马拉维','mw'),('马来西亚','my'),('莫桑比克','mz'),('纳米比亚','na'),('尼日尔','ne'),('尼日利亚','ng'),('尼加拉瓜','ni'),('挪威','no'),('尼泊尔','np'),('瑙鲁','nr'),('新西兰','nz'),('阿曼','om'),('巴拿马','pa'),('秘鲁','pe'),('巴布亚新几内亚','pg'),('菲律宾','ph'),('巴基斯坦','pk'),('波兰','pl'),('巴勒斯坦','ps'),('葡萄牙','pt'),('帕劳','pw'),('巴拉圭','py'),('卡塔尔','qa'),('罗马尼亚','ro'),('塞尔维亚','rs'),('卢旺达','rw'),('沙特阿拉伯','sa'),('所罗门群岛','sb'),('塞舌尔','sc'),('瑞典','se'),('斯洛文尼亚','si'),('斯洛伐克','sk'),('塞拉利昂','sl'),('塞内加尔','sn'),('苏里南','sr'),('圣多美和普林西比','st'),('萨尔瓦多','sv'),('斯威士兰','sz'),('特克斯和凯科斯群岛','tc'),('乍得','td'),('泰国','th'),('塔吉克斯坦','tj'),('土库曼斯坦','tm'),('突尼斯','tn'),('汤加','to'),('土耳其','tr'),('特立尼达和多巴哥','tt'),('坦桑尼亚','tz'),('乌克兰','ua'),('乌干达','ug'),('乌拉圭','uy'),('乌兹别克斯坦','uz'),('圣文森特和格林纳丁斯','vc'),('委内瑞拉','ve'),('英属维尔京群岛','vg'),('越南','vn'),('瓦努阿图','vu'),('萨摩亚','ws'),('科索沃','xk'),('也门','ye'),('南非','za'),('赞比亚','zm'),('津巴布韦','zw'),('朝鲜','kp'),('中國','cn'),('香港特别行政区','hk'),('澳門','mo'),('台湾省','tw'),('台灣','tw'),('馬來西亞','my'),('泰國','th'),('印尼西亞','id'),('菲律賓','ph'),('朝鮮','kp'),('阿联酌','ae'),('阿拉伯联合酌长国','ae'),('俄羅斯','ru'),('法國','fr'),('德國','de'),('荷蘭','nl'),('比利時','be'),('波蘭','pl'),('芬蘭','fi'),('愛爾蘭','ie'),('美國','us'),('秘魯','pe'),('澳洲利亞','au'),('新西蘭','nz'),('尼日利亞','ng'),('肯尼亞','ke'),('英國','gb'),('阿拉伯联合酋长国','ae');
CREATE TEMPORARY TABLE pkg_country_region_stage (code VARCHAR(2) PRIMARY KEY, label VARCHAR(100), sort_order INT);
INSERT INTO pkg_country_region_stage VALUES ('cn','中国',1),('hk','香港',2),('mo','澳门',3),('tw','台湾',4),('sg','新加坡',5),('my','马来西亚',6),('th','泰国',7),('vn','越南',8),('id','印度尼西亚',9),('ph','菲律宾',10),('jp','日本',11),('kr','韩国',12),('kp','朝鲜',13),('in','印度',14),('bd','孟加拉国',15),('pk','巴基斯坦',16),('ae','阿联酋',17),('sa','沙特阿拉伯',18),('il','以色列',19),('tr','土耳其',20),('ru','俄罗斯',21),('gb','英国',22),('fr','法国',23),('de','德国',24),('it','意大利',25),('es','西班牙',26),('pt','葡萄牙',27),('nl','荷兰',28),('be','比利时',29),('ch','瑞士',30),('pl','波兰',31),('se','瑞典',32),('no','挪威',33),('fi','芬兰',34),('ie','爱尔兰',35),('us','美国',36),('ca','加拿大',37),('mx','墨西哥',38),('br','巴西',39),('ar','阿根廷',40),('cl','智利',41),('co','哥伦比亚',42),('pe','秘鲁',43),('au','澳大利亚',44),('nz','新西兰',45),('za','南非',46),('eg','埃及',47),('ng','尼日利亚',48),('ke','肯尼亚',49);
INSERT INTO sys_dict(id,dict_name,dict_code,description,del_flag,type,tenant_id,create_by,create_time) SELECT 'pkg_dict_app_area','应用白名单地区','app_area','小写国家码，中文展示；源 dev 地区配置',0,0,0,'admin',NOW() FROM DUAL WHERE NOT EXISTS(SELECT 1 FROM sys_dict WHERE dict_code='app_area');
UPDATE sys_dict SET del_flag=0 WHERE dict_code='app_area';
SET @pkg_area_dict=(SELECT id FROM sys_dict WHERE dict_code='app_area' LIMIT 1);
UPDATE sys_dict_item i JOIN pkg_country_alias_stage a ON BINARY i.item_value=BINARY a.alias SET i.item_value=a.code WHERE i.dict_id=@pkg_area_dict;
UPDATE sys_dict_item SET item_value=LOWER(TRIM(item_value)) WHERE dict_id=@pkg_area_dict AND TRIM(item_value) REGEXP '^[A-Za-z]{2}$';
UPDATE sys_dict_item i JOIN pkg_country_region_stage r ON i.item_value=r.code SET i.item_text=r.label,i.sort_order=r.sort_order WHERE i.dict_id=@pkg_area_dict;
INSERT INTO sys_dict_item(id,dict_id,item_text,item_value,sort_order,status,create_by,create_time) SELECT MD5(CONCAT(@pkg_area_dict,':',r.code)),@pkg_area_dict,r.label,r.code,r.sort_order,1,'admin',NOW() FROM pkg_country_region_stage r WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item i WHERE i.dict_id=@pkg_area_dict AND i.item_value=r.code);
CREATE TEMPORARY TABLE pkg_country_item_keep AS SELECT i.item_value,MIN(i.id) AS keep_id FROM sys_dict_item i JOIN pkg_country_region_stage r ON r.code=i.item_value WHERE i.dict_id=@pkg_area_dict GROUP BY i.item_value;
UPDATE sys_dict_item i JOIN pkg_country_item_keep k ON i.item_value=k.item_value SET i.status=IF(i.id=k.keep_id,1,0) WHERE i.dict_id=@pkg_area_dict;
-- Normalize tokens separately: whitespace, aliases, uppercase, duplicates and empty entries.
CREATE TEMPORARY TABLE pkg_country_ordinals (n INT PRIMARY KEY);
INSERT INTO pkg_country_ordinals VALUES (1),(2),(3),(4),(5),(6),(7),(8),(9),(10),(11),(12),(13),(14),(15),(16),(17),(18),(19),(20),(21),(22),(23),(24),(25),(26),(27),(28),(29),(30),(31),(32),(33),(34),(35),(36),(37),(38),(39),(40),(41),(42),(43),(44),(45),(46),(47),(48),(49),(50),(51),(52),(53),(54),(55),(56),(57),(58),(59),(60),(61),(62),(63),(64),(65),(66),(67),(68),(69),(70),(71),(72),(73),(74),(75),(76),(77),(78),(79),(80),(81),(82),(83),(84),(85),(86),(87),(88),(89),(90),(91),(92),(93),(94),(95),(96),(97),(98),(99),(100),(101),(102),(103),(104),(105),(106),(107),(108),(109),(110),(111),(112),(113),(114),(115),(116),(117),(118),(119),(120),(121),(122),(123),(124),(125),(126),(127),(128),(129),(130),(131),(132),(133),(134),(135),(136),(137),(138),(139),(140),(141),(142),(143),(144),(145),(146),(147),(148),(149),(150),(151),(152),(153),(154),(155),(156),(157),(158),(159),(160),(161),(162),(163),(164),(165),(166),(167),(168),(169),(170),(171),(172),(173),(174),(175),(176),(177),(178),(179),(180),(181),(182),(183),(184),(185),(186),(187),(188),(189),(190),(191),(192),(193),(194),(195),(196),(197),(198),(199),(200),(201),(202),(203),(204),(205),(206),(207),(208),(209),(210),(211),(212),(213),(214),(215),(216),(217),(218),(219),(220),(221),(222),(223),(224),(225),(226),(227),(228),(229),(230),(231),(232),(233),(234),(235),(236),(237),(238),(239),(240),(241),(242),(243),(244),(245),(246),(247),(248),(249),(250),(251),(252),(253),(254),(255),(256);
CREATE TEMPORARY TABLE pkg_country_area_tokens (resource_type VARCHAR(32), resource_id BIGINT, ordinal INT, token TEXT);
INSERT INTO pkg_country_area_tokens
SELECT 'apps',a.id,n.n,TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(a.areas,',',n.n),',',-1))
FROM pkg_app_manager a JOIN pkg_country_ordinals n ON n.n<=1+LENGTH(a.areas)-LENGTH(REPLACE(a.areas,',','')) WHERE a.areas IS NOT NULL AND 1+LENGTH(a.areas)-LENGTH(REPLACE(a.areas,',',''))<=256;
INSERT INTO pkg_country_area_tokens
SELECT 'statistics',a.id,n.n,TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(a.sub_areas,',',n.n),',',-1))
FROM pkg_app_statistics a JOIN pkg_country_ordinals n ON n.n<=1+LENGTH(a.sub_areas)-LENGTH(REPLACE(a.sub_areas,',','')) WHERE a.sub_areas IS NOT NULL AND 1+LENGTH(a.sub_areas)-LENGTH(REPLACE(a.sub_areas,',',''))<=256;
UPDATE pkg_country_area_tokens t JOIN pkg_country_alias_stage a ON BINARY t.token=BINARY a.alias SET t.token=a.code;
UPDATE pkg_country_area_tokens SET token=LOWER(token) WHERE token REGEXP '^[A-Za-z]{2}$';
CREATE TEMPORARY TABLE pkg_country_area_normalized AS
SELECT resource_type,resource_id,COALESCE(GROUP_CONCAT(DISTINCT NULLIF(token,'') ORDER BY ordinal SEPARATOR ','),'') AS areas
FROM pkg_country_area_tokens GROUP BY resource_type,resource_id;
UPDATE pkg_app_manager a JOIN pkg_country_area_normalized n ON n.resource_type='apps' AND a.id=n.resource_id SET a.areas=n.areas;
UPDATE pkg_app_statistics a JOIN pkg_country_area_normalized n ON n.resource_type='statistics' AND a.id=n.resource_id SET a.sub_areas=n.areas;
DROP TEMPORARY TABLE pkg_country_area_normalized;
DROP TEMPORARY TABLE pkg_country_area_tokens;
DROP TEMPORARY TABLE pkg_country_ordinals;
DROP TEMPORARY TABLE pkg_country_item_keep;
DROP TEMPORARY TABLE pkg_country_region_stage;
DROP TEMPORARY TABLE pkg_country_alias_stage;
COMMIT;
SET SESSION group_concat_max_len=@pkg_old_group_concat_max_len;
SELECT item_text,item_value,status FROM sys_dict_item WHERE dict_id=@pkg_area_dict ORDER BY sort_order;
-- Unknown historical entries are preserved for review; matching unknown entries fails closed.
