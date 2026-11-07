package com.codejudge.service;

import com.codejudge.common.result.PageResult;
import com.codejudge.model.dto.ProblemQueryDTO;
import com.codejudge.model.dto.ProblemSaveDTO;
import com.codejudge.model.vo.AdminProblemDetailVO;
import com.codejudge.model.vo.ProblemDetailVO;
import com.codejudge.model.vo.ProblemListVO;

/**
 * 题目服务。
 */
public interface ProblemService {

    /**
     * 分页查询题目列表。不返回题面内容。
     */
    PageResult<ProblemListVO> page(ProblemQueryDTO query);

    /**
     * 题目详情（公开），只带示例用例。
     */
    ProblemDetailVO detail(Long id);

    /**
     * 题目详情（管理端），带全部用例。
     */
    AdminProblemDetailVO adminDetail(Long id);

    /**
     * 创建题目，同时写入测试用例。
     *
     * @return 新题目 ID
     */
    Long create(ProblemSaveDTO dto);

    /**
     * 更新题目。用例做全量替换。
     */
    void update(Long id, ProblemSaveDTO dto);

    /**
     * 逻辑删除题目。
     */
    void delete(Long id);
}
