package com.codejudge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codejudge.dao.mapper.UserMapper;
import com.codejudge.model.entity.User;
import com.codejudge.model.vo.LeaderboardVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 排行榜服务（基于 Redis Sorted Set）。
 * <p>
 * 为什么用 ZSet 而不是每次查库 {@code SELECT ... ORDER BY solved_count LIMIT 20}：
 * 排行榜是<b>高频读、低频写</b>。题目多、用户多之后，每次都去聚合提交表 / 扫用户表排序，
 * 是稳稳的全表扫描。ZSet 把分数（解题数）存成有序结构，取前 20 名是 {@code ZREVRANGE} 的 O(log N)，
 * 排名查询更是 O(1) 的 {@code ZREVRANK}。
 * <p>
 * 写的时候用 {@code ZADD} / {@code ZINCRBY} 增量维护，而不是"每次都重算全量"——
 * 只有真正解出新题的用户才更新自己的分数，写入成本极低。
 */
@Slf4j
@Service
public class LeaderboardService {

    /** 解题排行榜的 ZSet key */
    public static final String KEY = "lb:solved";

    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;

    public LeaderboardService(StringRedisTemplate redisTemplate, UserMapper userMapper) {
        this.redisTemplate = redisTemplate;
        this.userMapper = userMapper;
    }

    /**
     * 同步某个用户的分数（覆盖写，用于首次解题 / 数据修复）。
     */
    public void sync(Long userId, int solved) {
        redisTemplate.opsForZSet().add(KEY, String.valueOf(userId), solved);
    }

    /**
     * 取前 N 名（按解题数降序）。
     */
    public List<LeaderboardVO> topN(int n) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(KEY, 0, n - 1);
        if (tuples == null || tuples.isEmpty()) {
            return new ArrayList<>();
        }

        List<LeaderboardVO> list = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            LeaderboardVO vo = new LeaderboardVO();
            vo.setRank(rank++);
            vo.setUserId(Long.valueOf(t.getValue()));
            vo.setSolved(t.getScore() == null ? 0 : t.getScore().intValue());
            list.add(vo);
        }
        return list;
    }

    /**
     * 某用户的排名（从 1 开始），不在榜上返回 null。
     */
    public Long rankOf(Long userId) {
        Long rank = redisTemplate.opsForZSet().reverseRank(KEY, String.valueOf(userId));
        return rank == null ? null : rank + 1;
    }

    /**
     * 从数据库全量重建排行榜。
     * <p>
     * <b>为什么必须有这个方法：</b>排行榜是<b>派生数据</b>，唯一可信源是
     * {@code user.solved_count}。而 ZSet 只在「用户首次通过某题」时做一次增量写入——
     * 一旦 Redis 被清空、换了实例或者数据丢失，那条增量路径再也不会被触发，
     * 榜单就会永久空着，只能靠人工修数据。
     * <p>
     * 启动时对齐一次，缓存就具备了自愈能力。这也是把「缓存」和「存储」分开设计的意义：
     * 缓存随时可以丢，因为丢了能重建。
     * <p>
     * 用户量大时应该改成异步分页重建，避免拖慢启动；当前规模一次性重建足够。
     */
    public void rebuild() {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .select(User::getId, User::getSolvedCount)
                .gt(User::getSolvedCount, 0));

        if (users == null || users.isEmpty()) {
            log.info("排行榜重建：没有需要上榜的用户");
            return;
        }

        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>(users.size());
        for (User u : users) {
            tuples.add(ZSetOperations.TypedTuple.of(
                    String.valueOf(u.getId()), u.getSolvedCount().doubleValue()));
        }
        redisTemplate.opsForZSet().add(KEY, tuples);
        log.info("排行榜重建完成：{} 位用户上榜", tuples.size());
    }
}
