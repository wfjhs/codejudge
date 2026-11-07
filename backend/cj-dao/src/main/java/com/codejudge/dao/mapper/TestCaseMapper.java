package com.codejudge.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codejudge.model.entity.TestCase;
import org.apache.ibatis.annotations.Mapper;

/**
 * 测试用例 Mapper。
 */
@Mapper
public interface TestCaseMapper extends BaseMapper<TestCase> {
}
