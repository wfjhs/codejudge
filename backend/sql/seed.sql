-- ============================================================
-- CodeJudge 种子数据（可重复执行，幂等）
-- 仅用于本地演示 / 压测。生产环境请删除或替换为真实题目。
-- 用户请从前端注册，首个注册用户自动成为管理员。
-- ============================================================

-- 同 init.sql：不显式声明连接字符集，中文题目内容会按 latin1 二次编码后落库，
-- 表现为 "最大子段和" 变成 "æœ€å¤§å­æ®µå’Œ"
SET NAMES utf8mb4;

-- 题目 1：A+B（简单，Java + Python 均可）
INSERT INTO problem (id, title, content, difficulty, time_limit, memory_limit, submit_count, accept_count, create_time)
VALUES (1, 'A + B',
        '# A + B\n\n输入两个正整数 a 和 b（空格分隔），输出它们的和。\n\n## 输入格式\n一行两个整数 a b。\n\n## 输出格式\n一行一个整数 a + b。',
        1, 1000, 256, 0, 0, NOW())
ON DUPLICATE KEY UPDATE title=VALUES(title), content=VALUES(content),
                        difficulty=VALUES(difficulty), time_limit=VALUES(time_limit),
                        memory_limit=VALUES(memory_limit),
                        -- 关键：历史测试可能把题目逻辑删除过，重跑种子时必须复活，
                        -- 否则 selectById 带 deleted=0 条件会查不到，提交直接报"题目不存在"
                        deleted=0;

-- 题目 2：最大子段和（中等）
INSERT INTO problem (id, title, content, difficulty, time_limit, memory_limit, submit_count, accept_count, create_time)
VALUES (2, '最大子段和',
        '# 最大子段和\n\n给定一个整数数组，找出一个具有最大和的连续子数组，返回其最大和。\n\n## 输入格式\n第一行一个整数 n，第二行 n 个整数。\n\n## 输出格式\n一行一个整数，表示最大子段和。',
        2, 2000, 256, 0, 0, NOW())
ON DUPLICATE KEY UPDATE title=VALUES(title), content=VALUES(content),
                        difficulty=VALUES(difficulty), time_limit=VALUES(time_limit),
                        memory_limit=VALUES(memory_limit),
                        -- 关键：历史测试可能把题目逻辑删除过，重跑种子时必须复活，
                        -- 否则 selectById 带 deleted=0 条件会查不到，提交直接报"题目不存在"
                        deleted=0;

-- 题目 3：两数之和（简单）
INSERT INTO problem (id, title, content, difficulty, time_limit, memory_limit, submit_count, accept_count, create_time)
VALUES (3, '两数之和',
        '# 两数之和\n\n给定一个整数数组 nums 和目标值 target，请在数组中找出和为 target 的两个整数，返回它们的下标（从 0 开始，用空格分隔，小的在前）。\n\n## 输入格式\n第一行 n 和 target，第二行 n 个整数。\n\n## 输出格式\n一行两个整数下标。',
        1, 2000, 256, 0, 0, NOW())
ON DUPLICATE KEY UPDATE title=VALUES(title), content=VALUES(content),
                        difficulty=VALUES(difficulty), time_limit=VALUES(time_limit),
                        memory_limit=VALUES(memory_limit),
                        -- 关键：历史测试可能把题目逻辑删除过，重跑种子时必须复活，
                        -- 否则 selectById 带 deleted=0 条件会查不到，提交直接报"题目不存在"
                        deleted=0;

-- ===================== 测试用例 =====================
-- 题目 1：A+B（2 个示例用例 + 2 个隐藏用例；隐藏用例必须是合法可解输入，否则正确代码也会 RE）
DELETE FROM test_case WHERE problem_id = 1;
INSERT INTO test_case (problem_id, input, output, is_sample, score, sort_order) VALUES
(1, '1 2', '3', 1, 50, 0),
(1, '100 200', '300', 1, 50, 1),
(1, '424242 575757', '999999', 0, 0, 2),
(1, '-5 8', '3', 0, 0, 3);

-- 题目 2：最大子段和
DELETE FROM test_case WHERE problem_id = 2;
INSERT INTO test_case (problem_id, input, output, is_sample, score, sort_order) VALUES
(2, '5\n-2 1 -3 4 -1', '4', 1, 50, 0),
(2, '4\n1 2 3 4', '10', 1, 50, 1),
(2, '3\n-1 -2 -3', '-1', 0, 0, 2),
(2, '6\n-2 -1 2 3 -1 4', '8', 0, 0, 3);

-- 题目 3：两数之和
DELETE FROM test_case WHERE problem_id = 3;
INSERT INTO test_case (problem_id, input, output, is_sample, score, sort_order) VALUES
(3, '4 9\n2 7 11 15', '0 1', 1, 50, 0),
(3, '3 6\n3 2 4', '1 2', 1, 50, 1),
(3, '2 10\n3 7', '0 1', 0, 0, 2),
(3, '5 8\n1 2 3 4 5', '2 4', 0, 0, 3);
