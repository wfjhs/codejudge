package com.codejudge.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codejudge.model.entity.Submission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提交记录 Mapper。
 */
@Mapper
public interface SubmissionMapper extends BaseMapper<Submission> {
}
