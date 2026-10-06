SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;

-- ---------------------------------------------------------------------
-- 博物馆 · 藏品 / 展览 / 展厅 / 导览 / 会员
-- 库名 museum_collection；application.yml 里 ddl-auto: none，表结构全部由本文件在 MySQL 镜像构建期导入。
-- 注意：mysql 容器是 UTC，时间戳一律写 UTC_TIMESTAMP() + INTERVAL 8 HOUR（北京时间）。

CREATE DATABASE IF NOT EXISTS museum_collection
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE museum_collection;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS handover;
DROP TABLE IF EXISTS artifact_attrs;
DROP TABLE IF EXISTS exhibition_highlights;
DROP TABLE IF EXISTS artifact;
DROP TABLE IF EXISTS exhibition;
DROP TABLE IF EXISTS tour;
DROP TABLE IF EXISTS gallery;
DROP TABLE IF EXISTS point_adjustment;
DROP TABLE IF EXISTS member;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------- 藏品 ----------------
CREATE TABLE artifact (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    artifact_no  VARCHAR(32) NOT NULL UNIQUE COMMENT '藏品编号 WB-0001',
    name         VARCHAR(128) NOT NULL COMMENT '名称',
    dynasty      VARCHAR(32)  COMMENT '朝代 / 年代大类',
    acquire_year INT          COMMENT '入藏年份',
    status       VARCHAR(16)  COMMENT '在展 / 库房 / 借展',
    created_at   DATETIME     COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 藏品特征明细：@ElementCollection + @CollectionTable 承载数组语义
CREATE TABLE artifact_attrs (
    artifact_id  BIGINT NOT NULL,
    attr_idx     INT    NOT NULL COMMENT '顺序',
    era          VARCHAR(32) COMMENT '年代细分',
    material     VARCHAR(32) COMMENT '材质',
    CONSTRAINT fk_attr_artifact FOREIGN KEY (artifact_id) REFERENCES artifact (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 展览 ----------------
CREATE TABLE exhibition (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    ex_code     VARCHAR(32) NOT NULL UNIQUE COMMENT '展览编号 EX-2026-01',
    title       VARCHAR(128) NOT NULL COMMENT '标题',
    start_date  DATE COMMENT '开始日期',
    end_date    DATE COMMENT '结束日期',
    status      VARCHAR(16) COMMENT '筹展 / 展出中 / 已闭幕',
    created_at  DATETIME COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 展览亮点：@ElementCollection 字符串数组语义
CREATE TABLE exhibition_highlights (
    ex_id   BIGINT NOT NULL,
    hl_idx  INT    NOT NULL COMMENT '顺序',
    highlight VARCHAR(128) COMMENT '亮点',
    CONSTRAINT fk_hl_exhibition FOREIGN KEY (ex_id) REFERENCES exhibition (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 藏品出展交接 ----------------
-- 状态机：草稿 → 待接收 → 已接收 → 已收口；待接收可 → 已拒收；
-- 展览提前闭幕时，尚未接收的草稿 / 待接收单据 → 已终止。
CREATE TABLE handover (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    handover_no     VARCHAR(32) NOT NULL UNIQUE COMMENT '交接单号 JJ-2026-001',
    artifact_id     BIGINT NOT NULL COMMENT '藏品 id',
    artifact_no     VARCHAR(32)  COMMENT '藏品编号（冗余）',
    artifact_name   VARCHAR(128) COMMENT '藏品名称（冗余）',
    exhibition_id   BIGINT NOT NULL COMMENT '展览 id',
    ex_code         VARCHAR(32)  COMMENT '展览编号（冗余）',
    ex_title        VARCHAR(128) COMMENT '展览标题（冗余）',
    deliverer       VARCHAR(32)  COMMENT '交出人',
    receiver        VARCHAR(32)  COMMENT '接收人',
    handover_time   DATETIME COMMENT '交接时间',
    pre_condition   VARCHAR(512) COMMENT '出展前状况',
    note            VARCHAR(512) COMMENT '随附说明',
    status          VARCHAR(16)  COMMENT '草稿 / 待接收 / 已接收 / 已拒收 / 已收口 / 已终止',
    received_at     DATETIME COMMENT '接收时间',
    reject_reason   VARCHAR(512) COMMENT '拒收差异说明（留痕）',
    rejected_at     DATETIME COMMENT '拒收时间',
    return_handler  VARCHAR(32)  COMMENT '返库经办人',
    return_condition VARCHAR(512) COMMENT '返库状况',
    returned_at     DATETIME COMMENT '返库时间',
    terminate_reason VARCHAR(256) COMMENT '终止原因（展览提前闭幕）',
    -- 占用中的藏品 id（仅 待接收 / 已接收 取值，其余为 NULL）：
    -- 上面的唯一索引保证「同一藏品同一时间只有一张占用中的交接单」，并发确认交出时第二张直接撞约束
    active_artifact_id BIGINT GENERATED ALWAYS AS (IF(status IN ('待接收','已接收'), artifact_id, NULL)) STORED COMMENT '占用中的藏品（并发唯一约束用）',
    created_at      DATETIME COMMENT '创建时间',
    CONSTRAINT fk_handover_artifact FOREIGN KEY (artifact_id) REFERENCES artifact (id),
    CONSTRAINT fk_handover_exhibition FOREIGN KEY (exhibition_id) REFERENCES exhibition (id),
    UNIQUE KEY uk_handover_active_artifact (active_artifact_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 展厅 ----------------
CREATE TABLE gallery (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    gallery_code VARCHAR(32) NOT NULL UNIQUE COMMENT '展厅编号 GL-01',
    name         VARCHAR(64) NOT NULL COMMENT '名称',
    capacity     INT         NOT NULL COMMENT '可承载导览场次上限',
    status       VARCHAR(16) COMMENT '开放 / 维护 / 关闭',
    created_at   DATETIME COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 导览 ----------------
CREATE TABLE tour (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    tour_no      VARCHAR(32) NOT NULL UNIQUE COMMENT '导览编号 TO-2026-001',
    gallery_id   BIGINT COMMENT '所属展厅 id',
    gallery_code VARCHAR(32) COMMENT '所属展厅编号（冗余）',
    gallery_name VARCHAR(64) COMMENT '所属展厅名称（冗余）',
    title        VARCHAR(128) COMMENT '导览主题',
    guide_name   VARCHAR(32) COMMENT '讲解员',
    scheduled_at DATETIME COMMENT '安排时间',
    status       VARCHAR(16) COMMENT '待开始 / 进行中 / 已完成',
    created_at   DATETIME COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 会员 ----------------
CREATE TABLE member (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_no   VARCHAR(32) NOT NULL UNIQUE COMMENT '会员编号 MB-0001',
    name        VARCHAR(32) NOT NULL COMMENT '姓名',
    phone       VARCHAR(24) COMMENT '手机号',
    level       VARCHAR(16) COMMENT '普通 / 银卡 / 金卡',
    joined_at   DATE COMMENT '入会日期',
    points      INT         NOT NULL DEFAULT 0 COMMENT '累计积分',
    created_at  DATETIME COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 会员积分调整申请 ----------------
-- 状态机：待复核 → 已通过 / 已驳回 / 已撤回（后三者为终态，不可再处理）；
-- 创建 / 撤回 / 驳回均不动会员积分，仅「复核通过」在同一事务内更新积分并按阈值重算等级，
-- 同时落 points_before / points_after，保证审批记录与会员余额始终一致。
CREATE TABLE point_adjustment (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    apply_no        VARCHAR(32) NOT NULL UNIQUE COMMENT '申请单号 PA-2026-001',
    member_id       BIGINT NOT NULL COMMENT '会员 id',
    member_no       VARCHAR(32)  COMMENT '会员编号（冗余）',
    member_name     VARCHAR(32)  COMMENT '会员姓名（冗余）',
    delta           INT NOT NULL COMMENT '调整值（正加负减）',
    reason          VARCHAR(512) NOT NULL COMMENT '业务原因',
    voucher         VARCHAR(128) NOT NULL COMMENT '关联凭据（活动单号 / 兑换单号等）',
    applicant       VARCHAR(32)  NOT NULL COMMENT '申请人',
    status          VARCHAR(16)  NOT NULL COMMENT '待复核 / 已通过 / 已驳回 / 已撤回',
    points_before   INT COMMENT '通过前积分（仅已通过时落）',
    points_after    INT COMMENT '通过后积分（仅已通过时落）',
    reviewer        VARCHAR(32)  COMMENT '复核人',
    review_comment  VARCHAR(512) COMMENT '复核意见（驳回必填）',
    reviewed_at     DATETIME COMMENT '复核时间',
    withdrawn_at    DATETIME COMMENT '撤回时间',
    created_at      DATETIME COMMENT '创建时间',
    CONSTRAINT fk_pa_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- 种子数据
-- =====================================================================

INSERT INTO gallery (gallery_code, name, capacity, status, created_at) VALUES
('GL-01', '青铜馆', 3,  '开放', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('GL-02', '书画馆', 2,  '开放', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('GL-03', '陶瓷馆', 2,  '维护', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY);

INSERT INTO artifact (artifact_no, name, dynasty, acquire_year, status, created_at) VALUES
('WB-0001', '四羊方尊',       '商', 2021, '在展', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 55 DAY),
('WB-0002', '千里江山图摹本', '宋', 2022, '在展', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 55 DAY),
('WB-0003', '汝窑天青釉洗',   '宋', 2020, '出展中', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 DAY),
('WB-0004', '唐三彩马',       '唐', 2019, '在展', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 DAY),
('WB-0005', '金缕玉衣',       '汉', 2018, '借展', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 45 DAY),
('WB-0006', '越王勾践剑',     '春秋', 2017, '库房', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 45 DAY),
('WB-0007', '青花缠枝纹梅瓶', '元', 2023, '在展', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 40 DAY),
('WB-0008', '大克鼎',         '西周', 2024, '出展中', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 40 DAY);

-- 藏品特征明细（order 列必须显式给出）
INSERT INTO artifact_attrs (artifact_id, attr_idx, era, material) VALUES
(1, 0, '商晚期', '青铜'),
(2, 0, '北宋',   '绢本'),
(3, 0, '北宋',   '瓷'),
(4, 0, '唐',     '陶'),
(5, 0, '西汉',   '玉'),
(6, 0, '春秋',   '青铜'),
(7, 0, '元',     '瓷'),
(8, 0, '西周',   '青铜');

INSERT INTO exhibition (ex_code, title, start_date, end_date, status, created_at) VALUES
('EX-2026-01', '盛世唐风',     '2026-05-01', '2026-10-31', '展出中', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 30 DAY),
('EX-2026-02', '青铜文明',     '2026-03-01', '2026-09-15', '展出中', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 30 DAY),
('EX-2025-01', '宋元书画',     '2025-04-01', '2025-12-20', '已闭幕', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 300 DAY);

INSERT INTO exhibition_highlights (ex_id, hl_idx, highlight) VALUES
(1, 0, '唐代金银器特展'),
(1, 1, '百件珍品首聚'),
(1, 2, '沉浸式光影'),
(2, 0, '三星堆对话'),
(2, 1, '青铜铸造工坊'),
(3, 0, '名家真迹'),
(3, 1, '数字长卷');

-- 出展交接：覆盖 已收口 / 待接收 / 已接收 / 草稿 / 已拒收 五种状态
-- 注意：WB-0003（待接收）、WB-0008（已接收）占用中，对应藏品状态为「出展中」
INSERT INTO handover (handover_no, artifact_id, artifact_no, artifact_name, exhibition_id, ex_code, ex_title,
    deliverer, receiver, handover_time, pre_condition, note, status,
    received_at, reject_reason, rejected_at, return_handler, return_condition, returned_at, terminate_reason, created_at) VALUES
('JJ-2025-001', 2, 'WB-0002', '千里江山图摹本', 3, 'EX-2025-01', '宋元书画',
 '赵敏', '孙倩', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 280 DAY, '绢本完好，画轴无裂痕', '附防潮箱一只', '已收口',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 279 DAY, NULL, NULL,
 '赵敏', '返库检查无新增损伤', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 200 DAY, NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 281 DAY),
('JJ-2026-001', 3, 'WB-0003', '汝窑天青釉洗', 1, 'EX-2026-01', '盛世唐风',
 '赵敏', '孙倩', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 DAY, '釉面开片自然，底足完整', NULL, '待接收',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 DAY),
('JJ-2026-002', 8, 'WB-0008', '大克鼎', 2, 'EX-2026-02', '青铜文明',
 '钱峰', '孙倩', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 5 DAY, '鼎身铭文清晰，三足稳固', '随附拓片两份', '已接收',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 4 DAY, NULL, NULL, NULL, NULL, NULL, NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 6 DAY),
('JJ-2026-003', 6, 'WB-0006', '越王勾践剑', 2, 'EX-2026-02', '青铜文明',
 '赵敏', '孙倩', NULL, '剑身无锈蚀，保存完好', NULL, '草稿',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY),
('JJ-2026-004', 4, 'WB-0004', '唐三彩马', 1, 'EX-2026-01', '盛世唐风',
 '钱峰', '孙倩', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 10 DAY, '马身釉色完好', NULL, '已拒收',
 NULL, '实物马尾有磕碰痕迹，与记录的「釉色完好」不符', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 9 DAY,
 NULL, NULL, NULL, NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 11 DAY);

-- 导览：展厅 1 容量 3（2 场）、展厅 2 容量 2（1 场）、展厅 3 容量 2（1 场），均未超容量
INSERT INTO tour (tour_no, gallery_id, gallery_code, gallery_name, title, guide_name, scheduled_at, status, created_at) VALUES
('TO-2026-001', 1, 'GL-01', '青铜馆', '青铜铸造技艺解读',   '周明', UTC_TIMESTAMP() + INTERVAL 8 HOUR + INTERVAL 1 DAY,  '待开始', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 10 DAY),
('TO-2026-002', 1, 'GL-01', '青铜馆', '四羊方尊的前世今生', '李华', UTC_TIMESTAMP() + INTERVAL 8 HOUR,                 '进行中', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 10 DAY),
('TO-2026-003', 2, 'GL-02', '书画馆', '千里江山图赏析',     '王芳', UTC_TIMESTAMP() + INTERVAL 8 HOUR + INTERVAL 2 DAY,  '待开始', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 8 DAY),
('TO-2026-004', 3, 'GL-03', '陶瓷馆', '唐三彩的釉色之美',   '陈静', UTC_TIMESTAMP() + INTERVAL 8 HOUR + INTERVAL 3 DAY,  '待开始', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 6 DAY);

INSERT INTO member (member_no, name, phone, level, joined_at, points, created_at) VALUES
('MB-0001', '张伟', '13800001111', '普通', '2024-03-01', 1200, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 20 DAY),
('MB-0002', '李娜', '13800002222', '银卡', '2023-06-15', 2500, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 20 DAY),
('MB-0003', '王芳', '13800003333', '金卡', '2022-09-20', 7200, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 20 DAY),
('MB-0004', '刘洋', '13800004444', '普通', '2025-01-10', 800,  UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 20 DAY),
('MB-0005', '陈静', '13800005555', '金卡', '2021-11-05', 6500, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 20 DAY);

-- 积分调整申请：覆盖 已通过 / 已驳回 / 已撤回 / 待复核 四种状态
-- 已通过的两条与会员当前积分自洽（张伟 1000+200=1200、陈静 6000+500=6500）；
-- 待复核的 PA-2026-005 若直接通过会使李娜积分变负（2500-3000），用于演示复核拒绝口径
INSERT INTO point_adjustment (apply_no, member_id, member_no, member_name, delta, reason, voucher, applicant, status,
    points_before, points_after, reviewer, review_comment, reviewed_at, withdrawn_at, created_at) VALUES
('PA-2026-001', 1, 'MB-0001', '张伟', 200, '国庆活动签到奖励', 'ACT-2026-101', '周明', '已通过',
 1000, 1200, '孙倩', '活动名单核对无误', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 15 DAY, NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 16 DAY),
('PA-2026-002', 5, 'MB-0005', '陈静', 500, '年度志愿者服务奖励', 'VOL-2026-033', '周明', '已通过',
 6000, 6500, '孙倩', '服务时长核实通过', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 10 DAY, NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 11 DAY),
('PA-2026-003', 3, 'MB-0003', '王芳', 1000, '推荐新会员奖励', 'REF-2026-018', '李华', '已驳回',
 NULL, NULL, '孙倩', '推荐记录查无对应新会员，凭据不足', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 5 DAY, NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 6 DAY),
('PA-2026-004', 4, 'MB-0004', '刘洋', 100, '问卷调研参与奖励', 'QS-2026-077', '李华', '已撤回',
 NULL, NULL, NULL, NULL, NULL, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 DAY,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 DAY),
('PA-2026-005', 2, 'MB-0002', '李娜', -3000, '积分兑换文创商品', 'EXG-2026-210', '周明', '待复核',
 NULL, NULL, NULL, NULL, NULL, NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY);
