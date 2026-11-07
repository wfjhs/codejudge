package com.codejudge.service;

import com.codejudge.common.result.PageResult;
import com.codejudge.model.dto.SubmitDTO;
import com.codejudge.model.dto.SubmissionQueryDTO;
import com.codejudge.model.judge.JudgeTask;
import com.codejudge.model.vo.SubmissionDetailVO;
import com.codejudge.model.vo.SubmissionVO;

/**
 * 提交与判题服务。
 */
public interface SubmissionService {

    /**
     * 提交代码。落库为 PENDING 后投递判题队列，立即返回。
     *
     * @return 提交记录 ID
     */
    Long submit(SubmitDTO dto);

    /**
     * 我的提交记录（分页）。
     */
    PageResult<SubmissionVO> mySubmissions(SubmissionQueryDTO query);

    /**
     * 提交详情。只能看自己的。
     */
    SubmissionDetailVO detail(Long id);

    /**
     * 执行判题。由队列消费者调用。
     * <p>
     * 这个方法必须做到幂等：同一条消息重复投递时，第二次要能自己识别并跳过。
     */
    void judge(JudgeTask task);
}
