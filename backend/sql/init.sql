-- ============================================================
-- CodeJudge 初始化脚本
-- 用法：mysql -u root -p < sql/init.sql
-- ============================================================

-- 显式声明连接字符集。这条不能省：容器初始化时（/docker-entrypoint-initdb.d）
-- mysql 客户端的默认字符集不保证是 utf8mb4，缺了它，脚本里的中文会被按 latin1
-- 二次编码，落库后变成 "æœ€å¤§" 这样的乱码。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `codejudge`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `codejudge`;

-- ------------------------------------------------------------
-- 用户表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`     VARCHAR(32)  NOT NULL COMMENT '用户名',
    `password`     VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密后的密码',
    `nickname`     VARCHAR(32)           DEFAULT NULL COMMENT '昵称',
    `role`         TINYINT      NOT NULL DEFAULT 0 COMMENT '0-普通用户 1-管理员',
    `solved_count` INT          NOT NULL DEFAULT 0 COMMENT '通过题目数',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-正常 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';

-- ------------------------------------------------------------
-- 题目表（M2 使用，先建好避免反复改库）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `problem`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `title`        VARCHAR(100) NOT NULL COMMENT '题目标题',
    `content`      MEDIUMTEXT   NOT NULL COMMENT '题目描述（Markdown）',
    `difficulty`   TINYINT      NOT NULL DEFAULT 1 COMMENT '1-简单 2-中等 3-困难',
    `time_limit`   INT          NOT NULL DEFAULT 1000 COMMENT '时间限制（毫秒）',
    `memory_limit` INT          NOT NULL DEFAULT 256 COMMENT '内存限制（MB）',
    `submit_count` INT          NOT NULL DEFAULT 0 COMMENT '提交次数',
    `accept_count` INT          NOT NULL DEFAULT 0 COMMENT '通过次数',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_difficulty` (`difficulty`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='题目表';

-- ------------------------------------------------------------
-- 测试用例表
-- 注意：input/output 不对外暴露，只有判题服务读；is_sample=1 的示例用例可以返回给前端
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `test_case`
(
    `id`          BIGINT     NOT NULL AUTO_INCREMENT,
    `problem_id`  BIGINT     NOT NULL COMMENT '所属题目',
    `input`       MEDIUMTEXT NOT NULL COMMENT '输入数据',
    `output`      MEDIUMTEXT NOT NULL COMMENT '期望输出',
    `is_sample`   TINYINT    NOT NULL DEFAULT 0 COMMENT '1-示例用例(前端可见) 0-隐藏用例',
    `score`       INT        NOT NULL DEFAULT 0 COMMENT '该用例分值',
    `sort_order`  INT        NOT NULL DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_problem` (`problem_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='测试用例表';

-- ------------------------------------------------------------
-- 提交记录表
-- 数据量最大的一张表，索引设计见 KEY 部分注释
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `submission`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `request_id`  VARCHAR(64) NOT NULL COMMENT '前端生成的幂等键，重试时复用',
    `user_id`     BIGINT      NOT NULL,
    `problem_id`  BIGINT      NOT NULL,
    `language`    VARCHAR(16) NOT NULL COMMENT 'JAVA / PYTHON',
    `code`        MEDIUMTEXT  NOT NULL COMMENT '用户提交的源码',
    `status`      VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/JUDGING/AC/WA/TLE/MLE/RE/CE/SE',
    `score`       INT         NOT NULL DEFAULT 0,
    `time_cost`   INT                  DEFAULT NULL COMMENT '最大用例耗时(ms)',
    `memory_cost` INT                  DEFAULT NULL COMMENT '峰值内存(KB)',
    `error_msg`   TEXT                 DEFAULT NULL COMMENT '编译/运行错误信息',
    `fail_case`   INT                  DEFAULT NULL COMMENT '第一个失败用例的序号',
    `submit_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `judge_time`  DATETIME             DEFAULT NULL COMMENT '判题完成时间',
    PRIMARY KEY (`id`),
    -- 幂等的最后一道防线：并发重复提交时数据库直接拒绝，不依赖应用层判断
    UNIQUE KEY `uk_request_id` (`request_id`),
    -- 「我的提交记录」：WHERE user_id=? ORDER BY submit_time DESC，消除 filesort
    KEY `idx_user_time` (`user_id`, `submit_time`),
    -- 题目维度的通过率统计、AC 提交列表
    KEY `idx_problem_status` (`problem_id`, `status`),
    -- 故障恢复定时任务：扫卡在 JUDGING 的提交
    KEY `idx_status_submit` (`status`, `submit_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='提交记录表';
