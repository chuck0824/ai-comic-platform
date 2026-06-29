package com.aicp.module.contentproject.service;

import com.aicp.common.ai.AiRouter;
import com.aicp.module.contentproject.entity.*;
import com.aicp.module.contentproject.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * M4: TVC commercial script creation pipeline.
 * Brief → Brand Facts → Creative Strategy → Concept Script → Timecoded Script.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TvcService {

    private final TvcBriefMapper briefMapper;
    private final BrandFactMapper factMapper;
    private final CreativeStrategyMapper strategyMapper;
    private final TvcScriptMapper scriptMapper;
    private final AiRouter aiRouter;
    private final ObjectMapper objectMapper;

    // ===== Brief =====

    @Transactional
    public TvcBrief createBrief(Long projectId, Map<String, Object> input) {
        TvcBrief b = new TvcBrief();
        b.setProjectId(projectId);
        b.setBrandName(str(input.get("brand_name")));
        b.setProductName(str(input.get("product_name")));
        b.setTargetAudience(str(input.get("target_audience")));
        b.setBudget(str(input.get("budget")));
        b.setPlatforms(str(input.get("platforms")));
        b.setDuration(str(input.get("duration")));
        b.setAdditionalNotes(str(input.get("additional_notes")));
        b.setStatus("draft");
        briefMapper.insert(b);
        return b;
    }

    public TvcBrief getBrief(Long projectId) {
        TvcBrief b = briefMapper.selectOne(new LambdaQueryWrapper<TvcBrief>().eq(TvcBrief::getProjectId, projectId));
        if (b == null) throw new com.aicp.common.exception.BizException(com.aicp.common.exception.ErrorCode.NOT_FOUND);
        return b;
    }

    // ===== Brand Facts =====

    @Transactional
    public int aiExtractBrandFacts(Long projectId) {
        TvcBrief brief = getBrief(projectId);
        String prompt = "请为以下品牌/产品提取事实信息，输出JSON：{\"facts\":[{\"type\":\"\",\"content\":\"\",\"must_express\":true,\"must_not_express\":false,\"evidence_status\":\"verified|claimed|unverified\"}]}";
        Map<String, Object> r = aiRouter.chatCompletion(Map.of(
                "system_prompt", prompt, "prompt", "品牌：" + brief.getBrandName() + "\n产品：" + brief.getProductName() + "\n受众：" + brief.getTargetAudience(),
                "temperature", 0.3, "max_tokens", 2048));
        Map<String, Object> parsed = parseJson(extractText(r));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> facts = (List<Map<String, Object>>) parsed.getOrDefault("facts", List.of());
        int count = 0;
        for (Map<String, Object> f : facts) {
            BrandFact bf = new BrandFact();
            bf.setProjectId(projectId);
            bf.setFactType(str(f.get("type")));
            bf.setContent(str(f.get("content")));
            bf.setEvidenceStatus(str(f.get("evidence_status")));
            bf.setIsMustExpress(Boolean.TRUE.equals(f.get("must_express")) ? "yes" : "no");
            bf.setIsMustNotExpress(Boolean.TRUE.equals(f.get("must_not_express")) ? "yes" : "no");
            factMapper.insert(bf);
            count++;
        }
        return count;
    }

    public List<BrandFact> listFacts(Long projectId) {
        return factMapper.selectList(new LambdaQueryWrapper<BrandFact>().eq(BrandFact::getProjectId, projectId));
    }

    // ===== Creative Strategy =====

    @Transactional
    public int aiGenerateStrategies(Long projectId, int count) {
        TvcBrief brief = getBrief(projectId);
        String prompt = "请为以下品牌生成" + count + "个创意策略角度。输出JSON：{\"strategies\":[{\"angle_name\":\"\",\"opening_hook\":\"\",\"value_proposition\":\"\",\"brand_memory_point\":\"\",\"platform\":\"\"}]}";
        Map<String, Object> r = aiRouter.chatCompletion(Map.of(
                "system_prompt", "你是资深广告创意总监。", "prompt", prompt + "\n品牌：" + brief.getBrandName() + "\n产品：" + brief.getProductName(),
                "temperature", 0.8, "max_tokens", 3000));
        Map<String, Object> parsed = parseJson(extractText(r));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> strategies = (List<Map<String, Object>>) parsed.getOrDefault("strategies", List.of());
        int created = 0;
        for (Map<String, Object> s : strategies) {
            CreativeStrategy cs = new CreativeStrategy();
            cs.setProjectId(projectId);
            cs.setAngleNo(created + 1);
            cs.setAngleName(str(s.get("angle_name")));
            cs.setOpeningHook(str(s.get("opening_hook")));
            cs.setValueProposition(str(s.get("value_proposition")));
            cs.setBrandMemoryPoint(str(s.get("brand_memory_point")));
            cs.setPlatform(str(s.get("platform")));
            cs.setStatus("draft");
            strategyMapper.insert(cs);
            created++;
        }
        return created;
    }

    public List<CreativeStrategy> listStrategies(Long projectId) {
        return strategyMapper.selectList(new LambdaQueryWrapper<CreativeStrategy>().eq(CreativeStrategy::getProjectId, projectId));
    }

    // ===== TVC Script =====

    @Transactional
    public TvcScript generateScript(Long projectId, Long strategyId, int durationSec) {
        TvcBrief brief = getBrief(projectId);
        List<BrandFact> facts = listFacts(projectId);
        CreativeStrategy strategy = strategyMapper.selectById(strategyId);

        String mustFacts = facts.stream().filter(f -> "yes".equals(f.getIsMustExpress())).map(BrandFact::getContent).reduce((a,b)->a+"; "+b).orElse("");
        String forbiddenFacts = facts.stream().filter(f -> "yes".equals(f.getIsMustNotExpress())).map(BrandFact::getContent).reduce((a,b)->a+"; "+b).orElse("");

        String prompt = """
            你是一位资深TVC编剧。请根据以下信息生成时间码脚本。输出JSON：
            {"version_name":"","script":{"timecode":"00:00","visual":"","action":"","narration":"","subtitle":"","music_sfx":"","product_exposure":"","cta":""},"duration_sec":%d}
            品牌：%s 产品：%s 必须表达：%s 禁止表达：%s 创意：%s
            """.formatted(durationSec, brief.getBrandName(), brief.getProductName(), mustFacts, forbiddenFacts, strategy.getAngleName());

        Map<String, Object> r = aiRouter.chatCompletion(Map.of(
                "system_prompt", "你是资深TVC编剧，输出精确时间码脚本。", "prompt", prompt, "temperature", 0.7, "max_tokens", 4096));
        Map<String, Object> parsed = parseJson(extractText(r));

        TvcScript ts = new TvcScript();
        ts.setProjectId(projectId);
        ts.setVersionName(str(parsed.get("version_name")));
        ts.setContentJson(toJson(parsed.get("script")));
        ts.setPlainText(toJson(parsed));
        ts.setDurationSec(durationSec);
        ts.setPlatforms(brief.getPlatforms());
        ts.setStatus("draft");
        ts.setContentHash(sha256(toJson(parsed)));
        scriptMapper.insert(ts);
        return ts;
    }

    @Transactional
    public List<TvcScript> generateMultiPlatform(Long projectId, Long strategyId, List<String> platforms, List<Integer> durations) {
        List<TvcScript> scripts = new ArrayList<>();
        for (int i = 0; i < platforms.size(); i++) {
            TvcScript ts = generateScript(projectId, strategyId, i < durations.size() ? durations.get(i) : 30);
            ts.setPlatforms(platforms.get(i));
            scriptMapper.updateById(ts);
            scripts.add(ts);
        }
        return scripts;
    }

    public List<TvcScript> listScripts(Long projectId) {
        return scriptMapper.selectList(new LambdaQueryWrapper<TvcScript>().eq(TvcScript::getProjectId, projectId));
    }

    // ===== Helpers =====
    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> r) {
        Object choices = r.get("choices");
        if (choices instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Map m) {
            Object msg = m.get("message");
            if (msg instanceof Map mm) { Object c = mm.get("content"); if (c != null) return String.valueOf(c); }
        }
        return r.toString();
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String text) {
        try { String j = text; if (text.contains("```json")) { int s=text.indexOf("```json")+7,e=text.indexOf("```",s); if(e>s)j=text.substring(s,e).trim(); }
            return objectMapper.readValue(j, new TypeReference<Map<String,Object>>() {}); } catch(Exception e) { return Map.of(); }
    }
    private String str(Object v) { return v!=null?String.valueOf(v):""; }
    private String toJson(Object v) { try{return objectMapper.writeValueAsString(v);}catch(Exception e){return"{}";} }
    private String sha256(String input) {
        try { MessageDigest md = MessageDigest.getInstance("SHA-256"); byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(); for(byte b:hash) hex.append(String.format("%02x",b)); return hex.toString(); }
        catch(Exception e) { return ""+input.hashCode(); }
    }
}
