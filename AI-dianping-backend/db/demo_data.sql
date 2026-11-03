-- 仅用于本地演示：所有店铺、笔记、优惠和用户均带“演示”标识，不代表真实商户。
-- 先执行 reservation_init.sql。脚本可重复执行，不覆盖现有业务记录。
SET NAMES utf8mb4;
USE hmdp;
START TRANSACTION;

INSERT INTO tb_user (phone, nick_name, icon)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n+1 FROM seq WHERE n<60)
SELECT CONCAT('DEMO', LPAD(n,7,'0')), CONCAT('演示生活家', LPAD(n,2,'0')), '/assets/avatar.svg'
FROM seq WHERE NOT EXISTS (SELECT 1 FROM tb_user u WHERE u.phone=CONCAT('DEMO', LPAD(seq.n,7,'0')));

INSERT INTO tb_user_info (user_id, city, introduce, fans, followee)
SELECT u.id, '杭州', '这是用于系统展示的虚构账号。', 0, 0 FROM tb_user u
WHERE u.phone LIKE 'DEMO%' AND u.nick_name LIKE '演示生活家%'
  AND NOT EXISTS (SELECT 1 FROM tb_user_info i WHERE i.user_id=u.id);

INSERT INTO tb_shop (name,type_id,images,area,address,x,y,avg_price,sold,comments,score,open_hours)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n+1 FROM seq WHERE n<200)
SELECT CONCAT('演示·', t.name, '·', LPAD(seq.n,3,'0')), t.id, '',
       ELT(1+MOD(seq.n,4),'西湖区','拱墅区','滨江区','上城区'),
       CONCAT('演示地址：杭州生活街区 ', LPAD(seq.n,3,'0'), ' 号'),
       120.08+MOD(seq.n,20)*0.004, 30.19+FLOOR((seq.n-1)/20)*0.005,
       35+MOD(seq.n*13,180), 0, 0, 0, '10:00-22:00'
FROM seq JOIN tb_shop_type t ON t.id=1+MOD(seq.n-1,10)
WHERE NOT EXISTS (SELECT 1 FROM tb_shop s
    WHERE s.name=CONCAT('演示·', t.name, '·', LPAD(seq.n,3,'0')));

INSERT INTO tb_blog (shop_id,user_id,title,images,content,liked,comments)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n+1 FROM seq WHERE n<400)
SELECT s.id,u.id,CONCAT('演示·城市生活笔记 #', LPAD(seq.n,3,'0')), '',
       CONCAT('【演示内容】这是一篇用于展示的虚构探店笔记，分享一次关于', t.name,
              '的城市生活灵感。店铺、地点和体验均为示例，请勿作为实际消费依据。'),
       0,2
FROM seq
JOIN tb_user u ON u.phone=CONCAT('DEMO',LPAD(1+MOD(seq.n-1,60),7,'0')) AND u.nick_name LIKE '演示生活家%'
JOIN tb_shop_type t ON t.id=1+MOD(seq.n-1,10)
JOIN tb_shop s ON s.name=CONCAT('演示·',t.name,'·',LPAD(1+MOD(seq.n-1,200),3,'0'))
WHERE NOT EXISTS (SELECT 1 FROM tb_blog b WHERE b.title=CONCAT('演示·城市生活笔记 #',LPAD(seq.n,3,'0')));

INSERT INTO tb_blog_comments (user_id,blog_id,parent_id,answer_id,content,liked,status)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n+1 FROM seq WHERE n<800)
SELECT u.id,b.id,0,0,CONCAT('演示评论 #',LPAD(seq.n,3,'0'),'：这是一条用于展示的虚构互动。'),0,0
FROM seq
JOIN tb_user u ON u.phone=CONCAT('DEMO',LPAD(1+MOD(seq.n+5,60),7,'0')) AND u.nick_name LIKE '演示生活家%'
JOIN tb_blog b ON b.title=CONCAT('演示·城市生活笔记 #',LPAD(1+FLOOR((seq.n-1)/2),3,'0'))
WHERE NOT EXISTS (SELECT 1 FROM tb_blog_comments c
    WHERE c.blog_id=b.id AND c.content=CONCAT('演示评论 #',LPAD(seq.n,3,'0'),'：这是一条用于展示的虚构互动。'));

INSERT INTO tb_voucher (shop_id,title,sub_title,rules,pay_value,actual_value,type,status)
SELECT s.id, CONCAT('演示·店铺优惠 #',RIGHT(s.name,3)), '仅供系统演示，不能用于实际消费',
       '虚构优惠，仅用于测试页面展示；不支持真实支付或核销。', 3800, 5000, 0, 1
FROM tb_shop s
WHERE s.name LIKE '演示·%·___'
  AND NOT EXISTS (SELECT 1 FROM tb_voucher v WHERE v.shop_id=s.id AND v.title LIKE '演示·店铺优惠 #%');

INSERT INTO tb_reservation (user_id,user_phone,shop_id,reservation_time,status,num_people,remark)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n+1 FROM seq WHERE n<60)
SELECT u.id,u.phone,s.id,DATE_ADD(DATE_ADD(CURRENT_DATE, INTERVAL (1+MOD(seq.n,20)) DAY), INTERVAL 12 HOUR),
       IF(MOD(seq.n,3)=0,2,0),1+MOD(seq.n,4),CONCAT('演示预约 #',LPAD(seq.n,2,'0'),'，无需实际处理')
FROM seq
JOIN tb_user u ON u.phone=CONCAT('DEMO',LPAD(seq.n,7,'0')) AND u.nick_name LIKE '演示生活家%'
JOIN tb_shop_type t ON t.id=1+MOD(seq.n-1,10)
JOIN tb_shop s ON s.name=CONCAT('演示·',t.name,'·',LPAD(seq.n,3,'0'))
WHERE NOT EXISTS (SELECT 1 FROM tb_reservation r WHERE r.user_id=u.id
    AND r.remark=CONCAT('演示预约 #',LPAD(seq.n,2,'0'),'，无需实际处理'));

COMMIT;
