package com.aicp.module.canvas.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.canvas.entity.CanvasShotUnit;
import com.aicp.module.canvas.entity.ShotAdoption;
import com.aicp.module.canvas.mapper.CanvasShotUnitMapper;
import com.aicp.module.canvas.mapper.ShotAdoptionMapper;
import com.aicp.module.generation.entity.GenerationCandidate;
import com.aicp.module.generation.mapper.GenerationCandidateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 镜头正式采用服务。
 * ShotAdoption 是正式采用的唯一事实源；节点 API 不可改变正式采用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShotAdoptionService {

    private final ShotAdoptionMapper adoptionMapper;
    private final CanvasShotUnitMapper shotUnitMapper;
    private final GenerationCandidateMapper candidateMapper;

    /**
     * 创建正式采用 revision。
     * 每次采用创建新 revision，不覆盖旧记录。
     */
    @Transactional
    public ShotAdoption adopt(Long shotUnitId, Long candidateId, Long actorId,
                               String reason, String overrideReason) {
        CanvasShotUnit unit = shotUnitMapper.selectById(shotUnitId);
        if (unit == null) {
            throw new BizException(ErrorCode.CANVAS_NOT_FOUND);
        }

        GenerationCandidate candidate = candidateMapper.selectById(candidateId);
        if (candidate == null) {
            throw new BizException(ErrorCode.CANVAS_NODE_NOT_FOUND);
        }

        // 计算下一个 revision
        Long count = adoptionMapper.selectCount(
                new LambdaQueryWrapper<ShotAdoption>().eq(ShotAdoption::getShotUnitId, shotUnitId));
        int nextRevision = count.intValue() + 1;

        ShotAdoption adoption = new ShotAdoption();
        adoption.setUuid(UUID.randomUUID().toString());
        adoption.setShotUnitId(shotUnitId);
        adoption.setRevision(nextRevision);
        adoption.setCandidateId(candidateId);
        adoption.setAdoptedBy(actorId);
        adoption.setReason(reason);
        adoption.setOverrideReason(overrideReason);

        adoptionMapper.insert(adoption);
        log.info("ShotAdoption created: unit={}, revision={}, candidate={}, by={}",
                shotUnitId, nextRevision, candidateId, actorId);
        return adoption;
    }

    public ShotAdoptionView getCurrentAdoption(Long shotUnitId) {
        var list = adoptionMapper.selectList(
                new LambdaQueryWrapper<ShotAdoption>()
                        .eq(ShotAdoption::getShotUnitId, shotUnitId)
                        .orderByDesc(ShotAdoption::getRevision)
                        .last("LIMIT 1"));
        if (list.isEmpty()) return null;
        ShotAdoption a = list.get(0);
        return new ShotAdoptionView(a.getId(), a.getUuid(), a.getShotUnitId(), a.getRevision(),
                a.getCandidateId(), a.getAdoptedBy(), a.getReason(), a.getOverrideReason(), a.getCreatedAt());
    }

    public record ShotAdoptionView(Long id, String uuid, Long shotUnitId, int revision,
                                    Long candidateId, Long adoptedBy, String reason,
                                    String overrideReason, java.time.LocalDateTime createdAt) {}
}
