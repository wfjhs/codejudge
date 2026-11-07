package com.codejudge.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codejudge.model.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 用户 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 通过题目数 +1（用户首次通过某题时调用）。
     * <p>
     * 这是排行榜的冗余字段：每次看排行榜都去 count 一遍 submission 表会很慢，
     * 所以在这里维护一个计数。代价是要保证只加一次（见 SubmissionServiceImpl 的首次通过判断）。
     */
    @Update("UPDATE `user` SET solved_count = solved_count + 1 WHERE id = #{userId}")
    int increaseSolvedCount(@Param("userId") Long userId);
}
