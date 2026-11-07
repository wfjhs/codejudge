package com.codejudge.web.controller;

import com.codejudge.common.result.Result;
import com.codejudge.dao.mapper.UserMapper;
import com.codejudge.model.entity.User;
import com.codejudge.model.vo.LeaderboardVO;
import com.codejudge.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 排行榜接口。
 */
@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final UserMapper userMapper;

    /**
     * 取解题排行榜前 N 名，并补上昵称。
     * <p>
     * 这里特意不在 ZSet 里存昵称：昵称会变（用户改资料），放进 ZSet 就要同步更新，
     * 纯冗余还容易不一致。只在读的时候用 userId 批量查一次，名字变更零成本。
     */
    @GetMapping
    public Result<List<LeaderboardVO>> top(@RequestParam(defaultValue = "20") int top) {
        if (top <= 0 || top > 100) {
            top = 20;
        }
        List<LeaderboardVO> list = leaderboardService.topN(top);
        if (list.isEmpty()) {
            return Result.ok(list);
        }

        List<Long> userIds = list.stream().map(LeaderboardVO::getUserId).toList();
        Map<Long, String> nicknameMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname, (a, b) -> a));

        list.forEach(vo -> vo.setNickname(nicknameMap.getOrDefault(vo.getUserId(), "匿名用户")));
        return Result.ok(list);
    }
}
