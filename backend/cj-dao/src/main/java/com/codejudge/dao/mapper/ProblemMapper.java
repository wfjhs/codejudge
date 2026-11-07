package com.codejudge.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codejudge.model.entity.Problem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 题目 Mapper。
 */
@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {

    /**
     * 提交数 +1。
     * <p>
     * 用 SQL 原子自增而不是"先查再 set"：并发提交时后者会丢更新
     * （两个请求都读到 100，都写 101，实际应该是 102）。
     */
    @Update("UPDATE problem SET submit_count = submit_count + 1 WHERE id = #{problemId}")
    int increaseSubmitCount(@Param("problemId") Long problemId);

    /**
     * 通过数 +1。
     */
    @Update("UPDATE problem SET accept_count = accept_count + 1 WHERE id = #{problemId}")
    int increaseAcceptCount(@Param("problemId") Long problemId);
}
