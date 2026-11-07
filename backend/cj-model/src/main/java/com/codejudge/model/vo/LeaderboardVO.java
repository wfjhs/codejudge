package com.codejudge.model.vo;

import lombok.Data;

/**
 * 排行榜条目。
 */
@Data
public class LeaderboardVO {

    /** 名次，从 1 开始 */
    private int rank;

    private Long userId;

    private String nickname;

    /** 解题数 */
    private int solved;
}
