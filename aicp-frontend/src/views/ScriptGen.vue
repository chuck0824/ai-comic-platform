<template>
  <div>
    <h2 class="text-xl font-bold mb-lg">剧本创作</h2>

    <!-- 创作路径选择 -->
    <div class="path-tabs mb-lg">
      <div :class="['path-tab', { active: creationPath === 'ai' }]" @click="creationPath = 'ai'">
        <el-icon><Cpu /></el-icon> AI写作
      </div>
      <div :class="['path-tab', { active: creationPath === 'upload' }]" @click="creationPath = 'upload'">
        <el-icon><Upload /></el-icon> 用户上传
      </div>
    </div>

    <!-- ==================== AI 写作 ==================== -->
    <div v-if="creationPath === 'ai'">
      <!-- 模式选择 -->
      <div class="mode-cards grid2 gap-md mb-lg">
        <!-- 快速模式 -->
        <div :class="['mode-card', { active: genMode === 'quick' }]" @click="genMode = 'quick'">
          <div class="mode-icon"><el-icon><Lightning /></el-icon></div>
          <strong>快速模式</strong>
          <p class="text-xs text-muted mt-sm">输入一句话创意，默认生成可继续修改的小说/故事源头文本资产</p>
          <span class="badge badge-accent mt-sm">适合验证脑洞</span>
        </div>
        <!-- 精细模式 -->
        <div :class="['mode-card', { active: genMode === 'fine' }]" @click="genMode = 'fine'">
          <div class="mode-icon"><el-icon><Aim /></el-icon></div>
          <strong>精细模式 · 8步引导</strong>
          <p class="text-xs text-muted mt-sm">每步确认、编辑、重生成或回退，适合对内容和结构要求更高的场景</p>
          <span class="badge badge-warning mt-sm">适合专业创作</span>
        </div>
      </div>

      <!-- ===== 快速模式 ===== -->
      <div v-if="genMode === 'quick'" class="card">
        <h3 class="font-bold mb-md"><el-icon><Lightning /></el-icon> 快速模式 — 生成源头文本资产</h3>
        <el-input v-model="quickIdea" type="textarea" :rows="4"
                  placeholder="描述你的故事创意，比如：一个外卖小哥其实是隐藏的豪门继承人…" />

        <div class="grid3 gap-md mt-md">
          <el-select v-model="quickSourceType" size="small">
            <el-option label="小说/故事文本" value="novel" />
            <el-option label="产品故事" value="product_story" />
            <el-option label="品牌叙事" value="brand_story" />
          </el-select>
          <el-select v-model="quickPlatform" size="small">
            <el-option label="抖音" value="douyin" /><el-option label="快手" value="kuaishou" />
            <el-option label="视频号" value="wechat" /><el-option label="TikTok" value="tiktok" />
          </el-select>
          <el-select v-model="quickAudience" size="small">
            <el-option label="女频" value="female" /><el-option label="男频" value="male" />
            <el-option label="全年龄" value="all" />
          </el-select>
          <el-select v-model="quickEpisodes" size="small">
            <el-option label="20集" :value="20" /><el-option label="40集" :value="40" />
            <el-option label="60集" :value="60" /><el-option label="80集" :value="80" />
          </el-select>
        </div>

        <div class="decision-strip mt-md">
          <el-checkbox v-model="quickWithAdaptation">同时生成改编脚本</el-checkbox>
          <el-select v-model="quickTargetType" size="small" style="width:160px" :disabled="!quickWithAdaptation">
            <el-option label="AI漫剧" value="ai_comic" />
            <el-option label="短剧" value="short_drama" />
            <el-option label="网剧" value="web_drama" />
            <el-option label="TVC" value="tvc" />
          </el-select>
          <el-checkbox v-model="quickWithStoryboard">同时生成A档分镜</el-checkbox>
        </div>

        <div class="mt-md">
          <label class="text-sm font-bold mb-sm" style="display:block">4轴标签（控制题材、情节、情绪、时空）</label>
          <FourAxisTags v-model="quickTags" />
        </div>

        <el-button type="primary" size="large" class="w-full mt-lg" @click="doQuickGen" :loading="loading">
          {{ loading ? 'AI正在创作中...' : '生成源头文本资产' }}
        </el-button>
        <el-progress v-if="loading" :percentage="progress" :stroke-width="6" class="mt-md" />

        <div v-if="genResult" class="gen-result mt-lg">
          <el-alert type="success" title="生成完成！" :closable="false" show-icon />
          <div class="mt-md text-sm"><strong>剧本标题：</strong>{{ genResult.title || '未命名' }}</div>
          <div class="mt-sm text-sm text-muted">{{ genResult.synopsis || '' }}</div>
          <div class="decision-note mt-md">
            源头文本已生成。你可以先保存到仓库继续单章修改，也可以在精细模式中进入改编脚本和分镜生产。
          </div>
          <div class="flex gap-sm mt-lg">
            <el-button size="small" type="primary" @click="saveAndGoWarehouse" :loading="saving"><el-icon><FolderAdd /></el-icon> 保存并查看仓库</el-button>
            <el-button size="small" @click="genMode = 'fine'; currentStep = 0"><el-icon><Aim /></el-icon> 进入精细编辑</el-button>
            <el-button size="small" @click="saveAndGoCanvas" :disabled="!quickWithStoryboard"><el-icon><Brush /></el-icon> 送入画布工作台</el-button>
          </div>
        </div>
      </div>

      <!-- ===== 精细模式 ===== -->
      <div v-else>
        <!-- 步骤指示器 -->
        <div class="step-indicator mb-lg">
          <div v-for="(s, i) in fineSteps" :key="i"
               :class="['step-dot', { done: i < currentStep, active: i === currentStep }]"
               @click="goToStep(i)">
            <span class="step-num"><el-icon v-if="i < currentStep" :size="12"><CircleCheck /></el-icon><template v-else>{{ i + 1 }}</template></span>
            <span class="step-name">{{ s }}</span>
          </div>
        </div>

        <!-- ===== STEP 0: 输入创意与参数 ===== -->
        <div v-show="currentStep === 0" class="card">
          <h3 class="font-bold mb-md">Step 1: 源头文本与钩子策略</h3>
          <el-input v-model="fineIdea" type="textarea" :rows="4"
                    placeholder="描述你的故事创意、核心冲突、人物关系…&#10;比如：被家族抛弃的私生女，意外成为商业帝王唯一在意的女人…" />
          <div class="grid3 gap-md mt-md">
            <el-select v-model="fineSourceType" size="small">
              <el-option label="小说/故事文本" value="novel" />
              <el-option label="产品故事" value="product_story" />
              <el-option label="品牌叙事" value="brand_story" />
            </el-select>
            <el-select v-model="finePlatform" size="small"><el-option label="抖音" value="douyin" /><el-option label="快手" value="kuaishou" /><el-option label="视频号" value="wechat" /></el-select>
            <el-select v-model="fineAudience" size="small"><el-option label="女频" value="female" /><el-option label="男频" value="male" /><el-option label="全年龄" value="all" /></el-select>
            <el-select v-model="fineEpisodes" size="small"><el-option label="20集" :value="20" /><el-option label="40集" :value="40" /><el-option label="80集" :value="80" /></el-select>
          </div>
          <div class="hook-strategy-card mt-md">
            <strong>钩子策略前置</strong>
            <span>系统将在选题、大纲、章节正文和章尾留白阶段持续检查钩子，不再等到分镜前才审核。</span>
          </div>
          <div class="mt-md"><label class="text-sm font-bold mb-sm" style="display:block">4轴标签（精确控制创作方向）</label><FourAxisTags v-model="fineTags" /></div>
          <el-button type="primary" size="large" class="w-full mt-lg" @click="doGenTopic" :loading="stepLoading">
            {{ stepLoading ? 'AI分析中...' : '生成爆款选题 →' }}
          </el-button>
          <el-progress v-if="stepLoading" :percentage="stepProgress" :stroke-width="6" class="mt-md" />
        </div>

        <!-- ===== STEP 1: 选题 ===== -->
        <div v-show="currentStep === 1" class="card">
          <h3 class="font-bold mb-md">Step 2: 选择爆款选题方向</h3>
          <p class="text-sm text-muted mb-md">AI 基于你的创意和标签，生成了以下选题方向：</p>
          <div v-if="stepLoading" class="text-center py-xl"><el-icon :size="40"><Loading /></el-icon><p class="mt-md">AI正在生成选题...</p></div>
          <div v-else-if="fineTopics.length" class="topic-grid">
            <div v-for="(t, i) in fineTopics" :key="i"
                 :class="['topic-card', { picked: selectedTopic === i }]" @click="selectTopic(i)">
              <div class="topic-header">
                <strong>{{ t.title }}</strong>
                <span class="badge badge-accent">匹配 {{ t.matchRate || t.match_rate || 85 }}%</span>
              </div>
              <p class="text-sm text-muted mt-sm">{{ t.description || t.desc || '' }}</p>
              <div v-if="t.highlights" class="topic-tags mt-sm">
                <span v-for="h in (t.highlights || [])" :key="h" class="tag selected">{{ h }}</span>
              </div>
              <div v-if="t.platformSuggestion" class="text-xs text-muted mt-sm">{{ t.platformSuggestion }}</div>
            </div>
          </div>
          <div class="flex gap-sm mt-lg">
            <el-button size="large" @click="currentStep = 0">← 上一步</el-button>
            <el-button type="primary" size="large" @click="doGenSynopsis" :loading="stepLoading" :disabled="selectedTopic < 0 && !fineTopics.length">
              下一步：生成故事梗概 →
            </el-button>
          </div>
        </div>

        <!-- ===== STEP 2: 梗概 ===== -->
        <div v-show="currentStep === 2" class="card">
          <h3 class="font-bold mb-md">Step 3: 故事梗概</h3>
          <div v-if="stepLoading" class="text-center py-xl"><el-icon :size="40"><Loading /></el-icon><p class="mt-md">AI正在生成梗概...</p><el-progress :percentage="stepProgress" class="mt-md" style="max-width:300px;margin:0 auto" /></div>
          <template v-else-if="synopsisData">
            <div class="section"><h4 class="sec-title"><el-icon><MapLocation /></el-icon> 世界观</h4><el-input v-model="synopsisData.worldBuilding" type="textarea" :rows="2" /></div>
            <div class="section"><h4 class="sec-title"><el-icon><Reading /></el-icon> 故事梗概</h4><el-input v-model="synopsisData.synopsis" type="textarea" :rows="6" /></div>
            <div class="section"><h4 class="sec-title"><el-icon><TrendCharts /></el-icon> 主线剧情（起承转合）</h4>
              <div class="grid2 gap-sm"><div v-for="(p, i) in (synopsisData.plotPhases || ['','','',''])" :key="i"><span class="text-xs text-muted">{{ ['起·开端','承·发展','转·高潮','合·结局'][i] }}</span><el-input v-model="synopsisData.plotPhases[i]" size="small" /></div></div>
            </div>
            <div class="section"><h4 class="sec-title"><el-icon><Warning /></el-icon> 核心冲突</h4><el-input v-model="synopsisData.coreConflict" type="textarea" :rows="2" /></div>
            <div class="section"><h4 class="sec-title"><el-icon><Star /></el-icon> 故事亮点</h4><div class="flex gap-sm flex-wrap"><el-tag v-for="(h,i) in (synopsisData.highlights||[])" :key="i" closable @close="synopsisData.highlights.splice(i,1)">{{ h }}</el-tag><el-button size="small" @click="synopsisData.highlights.push('新亮点')">+ 添加</el-button></div></div>
          </template>
          <div class="flex gap-sm mt-lg">
            <el-button size="large" @click="currentStep = 1">← 上一步</el-button>
            <el-button size="large" @click="doGenSynopsis" :loading="stepLoading"><el-icon><Refresh /></el-icon> 重新生成</el-button>
            <el-button type="primary" size="large" @click="doGenOutline" :loading="stepLoading" :disabled="!synopsisData">下一步：生成分集大纲 →</el-button>
          </div>
        </div>

        <!-- ===== STEP 3: 大纲 ===== -->
        <div v-show="currentStep === 3" class="card">
          <div class="flex items-center justify-between mb-md">
            <h3 class="font-bold">Step 4: 分集大纲</h3>
            <el-select v-model="fineEpisodes" size="small" style="width:100px"><el-option label="20集" :value="20" /><el-option label="40集" :value="40" /><el-option label="80集" :value="80" /></el-select>
          </div>
          <div v-if="stepLoading" class="text-center py-xl"><el-icon :size="40"><Loading /></el-icon><p class="mt-md">AI正在生成大纲...</p></div>
          <div v-else-if="outlineEpisodes.length" class="ep-list">
            <div v-for="(ep, i) in outlineEpisodes" :key="i" class="ep-card" @click="ep._open = !ep._open">
              <div class="ep-header">
                <span class="ep-num">第{{ ep.number || i+1 }}集</span>
                <span class="ep-title">{{ ep.title || '未命名' }}</span>
                <HookBar :opening="ep.openingHook" :closing="ep.closingHook" :openingScore="ep.openingHookStrength" :closingScore="ep.closingHookStrength" />
                <span style="color:#666">{{ ep._open ? '▾' : '▸' }}</span>
              </div>
              <div v-if="ep._open" class="ep-body" @click.stop>
                <el-input v-model="ep.title" size="small" placeholder="本集标题" class="mb-sm" />
                <el-input v-model="ep.coreEvent" type="textarea" :rows="2" size="small" placeholder="核心事件" class="mb-sm" />
                <div class="grid2 gap-sm"><div><label class="text-xs text-muted"><el-icon><VideoCamera /></el-icon> 开场钩子</label><el-input v-model="ep.openingHook" size="small" /></div><div><label class="text-xs text-muted"><el-icon><Link /></el-icon> 结尾悬念</label><el-input v-model="ep.closingHook" size="small" /></div></div>
              </div>
            </div>
          </div>
          <div v-else class="text-center py-xl text-muted">点击"生成大纲"</div>
          <div class="flex gap-sm mt-lg">
            <el-button size="large" @click="currentStep = 2">← 上一步</el-button>
            <el-button size="large" @click="doGenOutline" :loading="stepLoading">🔄 重新生成</el-button>
            <el-button size="small" @click="optimizeHooks"><el-icon><MagicStick /></el-icon> 优化钩子</el-button>
            <el-button type="primary" size="large" @click="doGenEpisode" :loading="stepLoading" :disabled="!outlineEpisodes.length">下一步：生成剧本 →</el-button>
          </div>
        </div>

        <!-- ===== STEP 4: 剧本 ===== -->
        <div v-show="currentStep === 4" class="card">
          <h3 class="font-bold mb-md">Step 5: 剧本编辑器</h3>
          <div v-if="stepLoading" class="text-center py-xl"><el-icon :size="40"><Loading /></el-icon><p class="mt-md">AI正在生成剧本...</p></div>
          <div v-else-if="scriptText" class="script-layout">
            <div class="ep-nav-col">
              <div v-for="(ep, i) in outlineEpisodes" :key="i" :class="['ep-nav-row', { active: scriptEpIdx === i }]" @click="scriptEpIdx = i">第{{ i+1 }}集 · {{ ep.title }}</div>
            </div>
            <div>
              <div class="script-toolbar">
                <span class="text-xs text-muted">第{{ scriptEpIdx+1 }}集 · {{ scriptText.length }}字</span>
                <div class="flex gap-sm">
                  <el-button size="small" @click="reviewCurrentEpisode" :loading="reviewLoading"><el-icon><DataAnalysis /></el-icon> 联合审核本集</el-button>
                  <el-button size="small" @click="doGenEpisode"><el-icon><Cpu /></el-icon> AI续写</el-button>
                </div>
              </div>
              <el-input v-model="scriptText" type="textarea" :rows="16" class="script-input" placeholder="[场景1]&#10;△ 动作描述&#10;角色名：对白&#10;【旁白】：内容" />
              <div v-if="episodeReview" class="episode-review-panel mt-md">
                <div class="review-head">
                  <div>
                    <strong>第{{ episodeReview.episode_number || scriptEpIdx + 1 }}集联合审核</strong>
                    <span :class="['review-status', episodeReview.overall_status === 'pass' ? 'pass' : 'warn']">
                      {{ episodeReview.overall_status === 'pass' ? '可进入分镜' : '建议优化' }}
                    </span>
                  </div>
                  <span class="review-score">总分 {{ Math.round((episodeReview.overall_score || 0) * 100) }}%</span>
                </div>
                <div class="review-score-grid">
                  <div class="review-score-card"><span>钩子 Agent</span><strong>{{ Math.round((episodeReview.hook_score || 0) * 100) }}%</strong></div>
                  <div class="review-score-card"><span>编导 Agent</span><strong>{{ Math.round((episodeReview.showrunner_score || 0) * 100) }}%</strong></div>
                  <div class="review-score-card"><span>导演 Agent</span><strong>{{ Math.round((episodeReview.director_score || 0) * 100) }}%</strong></div>
                </div>
                <div class="review-agent-list">
                  <div v-for="agent in (episodeReview.agent_reviews || [])" :key="agent.agent_type" class="review-agent-card">
                    <div class="review-agent-title">
                      <strong>{{ agent.agent_name }}</strong>
                      <span>{{ agent.score_text || Math.round((agent.score || 0) * 100) + '%' }}</span>
                    </div>
                    <p>{{ agent.summary }}</p>
                    <div v-if="agent.issues?.length" class="review-issues">
                      <div v-for="(issue, idx) in agent.issues" :key="idx" :class="['review-issue', issue.severity]">
                        {{ issue.message }}
                      </div>
                    </div>
                    <div v-if="agent.suggestions?.length" class="review-suggestions">
                      <span v-for="(s, idx) in agent.suggestions" :key="idx">{{ s }}</span>
                    </div>
                  </div>
                </div>
                <div class="review-actions">
                  <el-button size="small" @click="reviewCurrentEpisode" :loading="reviewLoading">重新审核</el-button>
                  <el-button size="small" type="primary" @click="currentStep = 5">保存正文并选择下一步</el-button>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="text-center py-xl text-muted">点击"生成剧本"</div>
          <div class="flex gap-sm mt-lg">
            <el-button size="large" @click="currentStep = 3">← 上一步</el-button>
            <el-button size="large" @click="doGenEpisode" :loading="stepLoading">🔄 重新生成</el-button>
            <el-button size="large" type="success" @click="saveChapterDraft" :loading="saving">保存单章正文版本</el-button>
            <el-button type="primary" size="large" @click="currentStep = 5">正文完成：选择下一步 →</el-button>
          </div>
        </div>

        <!-- ===== STEP 5: 正文完成页 ===== -->
        <div v-show="currentStep === 5" class="card">
          <h3 class="font-bold mb-md">Step 6: 正文完成页</h3>
          <p class="text-sm text-muted mb-md">源头文本已生成。这里不再强制进入分镜，用户可以继续修改正文、保存仓库、改编脚本、生成分镜或生成投流素材。</p>
          <div class="decision-grid">
            <div class="decision-card" @click="currentStep = 4">
              <strong>继续修改正文</strong>
              <span>回到单章正文编辑器，继续润色、扩写、增强钩子。</span>
            </div>
            <div class="decision-card" @click="saveToWarehouse">
              <strong>保存到仓库</strong>
              <span>只保存源头文本资产，分镜和投流素材允许为空。</span>
            </div>
            <div class="decision-card primary" @click="currentStep = 6">
              <strong>改编为脚本</strong>
              <span>将小说/故事文本改编为 AI漫剧、短剧、网剧或 TVC。</span>
            </div>
            <div class="decision-card" @click="doGenStoryboard">
              <strong>选择章节生成分镜</strong>
              <span>用户主动进入 A/B/C 档分镜生产，不覆盖正文。</span>
            </div>
            <div class="decision-card" @click="doGenPromotion">
              <strong>生成投流素材</strong>
              <span>基于源头文本或改编脚本生成标题、封面和 3 秒钩子。</span>
            </div>
          </div>
          <div class="flex gap-sm mt-lg">
            <el-button size="large" @click="currentStep = 4">← 返回正文</el-button>
          </div>
        </div>

        <!-- ===== STEP 6: 改编脚本 ===== -->
        <div v-show="currentStep === 6" class="card">
          <h3 class="font-bold mb-md">Step 7: 改编脚本</h3>
          <div class="grid3 gap-md mb-md">
            <el-select v-model="adaptationTargetType" size="small">
              <el-option label="AI漫剧" value="ai_comic" />
              <el-option label="短剧" value="short_drama" />
              <el-option label="网剧" value="web_drama" />
              <el-option label="TVC" value="tvc" />
            </el-select>
            <el-button type="primary" @click="doGenAdaptation" :loading="stepLoading">生成改编脚本</el-button>
            <el-button @click="saveAdaptationVersion" :loading="saving" :disabled="!adaptationText">保存改编版本</el-button>
          </div>
          <div v-if="stepLoading" class="text-center py-xl"><el-icon :size="40"><Loading /></el-icon><p class="mt-md">AI正在生成改编脚本...</p></div>
          <template v-else>
            <div class="hook-strategy-card mb-md">
              <strong>继承源头钩子</strong>
              <span>改编脚本会继承小说/故事文本的开场钩子、章尾留白和阶段性大钩子。</span>
            </div>
            <el-input v-model="adaptationText" type="textarea" :rows="16" class="script-input" placeholder="这里展示 AI漫剧/短剧/网剧/TVC 改编脚本，可继续人工修改。" />
          </template>
          <div class="flex gap-sm mt-lg">
            <el-button size="large" @click="currentStep = 5">← 返回正文完成页</el-button>
            <el-button type="primary" size="large" @click="doGenStoryboard" :loading="stepLoading" :disabled="!adaptationText">基于改编脚本生成分镜 →</el-button>
          </div>
        </div>

        <!-- ===== STEP 7: 分镜 ===== -->
        <div v-show="currentStep === 7" class="card">
          <div class="flex items-center gap-md mb-md">
            <h3 class="font-bold">Step 8: 分镜脚本</h3>
            <div class="tier-tabs"><span :class="['tier-tab',{active:tier==='A'}]" @click="tier='A'">A档·编导速看</span><span :class="['tier-tab',{active:tier==='B'}]" @click="tier='B'">B档·导演确认</span><span :class="['tier-tab',{active:tier==='C'}]" @click="tier='C'">C档·生产交付</span></div>
          </div>
          <div v-if="stepLoading" class="text-center py-xl"><el-icon :size="40"><Loading /></el-icon><p class="mt-md">AI正在生成分镜...</p></div>
          <template v-else-if="tier === 'A' && storyboardShots.length">
            <div class="grid2 gap-md mb-md">
              <div class="card" style="background:var(--bg-surface-hover)"><h4 class="sec-title"><el-icon><VideoCamera /></el-icon> 场景戏剧目标卡</h4><table class="info-tbl"><tr><td>剧情任务</td><td>林默对苏小晚产生好奇</td></tr><tr><td>人物目标</td><td>试探vs隐藏</td></tr><tr><td>核心冲突</td><td>信息博弈</td></tr><tr><td>关系变化</td><td>无视→追问</td></tr><tr><td>情绪走向</td><td>平淡→悬疑</td></tr></table></div>
              <div class="card" style="background:var(--bg-surface-hover)"><h4 class="sec-title"><el-icon><DataAnalysis /></el-icon> Beat拆解</h4><table class="info-tbl"><thead><tr><th>Beat</th><th>内容</th><th>策略</th><th>镜头</th></tr></thead><tbody><tr><td>B1 进入</td><td>苏小晚端咖啡</td><td>跟拍</td><td>2-3镜</td></tr><tr><td>B2 试探</td><td>林默问问题</td><td>正反打</td><td>4-5镜</td></tr><tr><td>B3 升级</td><td>注意伤疤</td><td>特写+反应</td><td>3-4镜</td></tr><tr><td>B4 反转</td><td>巧妙回避</td><td>关系镜</td><td>3-4镜</td></tr><tr><td>B5 钩子</td><td>若有所思</td><td>固定留白</td><td>1-2镜</td></tr></tbody></table></div>
            </div>
            <h4 class="sec-title"><el-icon><List /></el-icon> A档 · 主分镜表</h4>
            <div class="table-wrap"><table class="shot-tbl"><thead><tr><th>镜号</th><th>时长</th><th>景别/运镜</th><th>画面内容</th><th>对白</th><th>功能</th></tr></thead><tbody>
              <tr v-for="(s,i) in storyboardShots" :key="i"><td><code>{{ s.shotNo || ('SH'+String(i+1).padStart(3,'0')) }}</code></td><td>{{ s.duration||'3s' }}</td><td>{{ s.shotSize||'MS' }} {{ s.cameraMove||'' }}</td><td class="text-sm">{{ s.visual||s.visualDescription||'—' }}</td><td class="text-sm">{{ s.dialogue||'—' }}</td><td>{{ s.function||'—' }}</td></tr>
            </tbody></table></div>
          </template>
          <div v-else-if="tier !== 'A'" class="text-center py-xl"><p class="font-bold">{{tier==='B'?'B档·导演确认版':'C档·生产交付版'}}</p><p class="text-sm text-muted mt-sm">{{tier==='B'?'导演意图标注·人物调度·信息差控制·声画设计':'AI抽卡表·AI视频表·配音字幕交付表'}}</p><p class="text-xs text-muted mt-lg">将在后续版本上线</p></div>
          <div v-else class="text-center py-xl text-muted">点击"生成分镜"</div>
          <div class="flex gap-sm mt-lg">
            <el-button size="large" @click="currentStep = adaptationText ? 6 : 5">← 上一步</el-button>
            <el-button size="large" @click="doGenStoryboard" :loading="stepLoading">🔄 重新生成</el-button>
            <el-button type="primary" size="large" @click="doGenPromotion" :loading="stepLoading">下一步：生成投流素材 →</el-button>
          </div>
        </div>

        <!-- ===== STEP 8: 投流 ===== -->
        <div v-show="currentStep === 8" class="card">
          <h3 class="font-bold mb-md">Step 9: 投流素材</h3>
          <div v-if="stepLoading" class="text-center py-xl"><el-icon :size="40"><Loading /></el-icon><p class="mt-md">AI正在生成投流素材...</p></div>
          <template v-else-if="promoData">
            <div class="section"><h4 class="sec-title"><el-icon><TrendCharts /></el-icon> 爆款标题 (5选1)</h4><div class="grid2 gap-sm"><div v-for="(t,i) in (promoData.titles||[])" :key="i" class="title-card" :class="{picked:promoPicks.title===i}" @click="promoPicks.title=i">{{ ['悬念式','反转式','痛点式','数据式','对比式'][i] }}<p class="text-sm mt-sm">{{ t }}</p></div></div></div>
            <div class="section"><h4 class="sec-title"><el-icon><Link /></el-icon> 3秒钩子 (5条高注意力开场)</h4><div v-for="(h,i) in (promoData.threeSecHooks||[])" :key="i" class="hook-line">{{i+1}}. {{ h }}</div></div>
            <div class="section"><h4 class="sec-title"><el-icon><Picture /></el-icon> 封面文案 (3套)</h4><div v-for="(c,i) in (promoData.coverCopy||[])" :key="i" class="cover-card">方案{{i+1}}: {{ c }}</div></div>
            <div class="section"><h4 class="sec-title"><el-icon><Scissor /></el-icon> 短视频切片脚本</h4><div v-for="(s,i) in (promoData.clipScripts||[])" :key="i" class="cover-card">{{ s }}</div></div>
          </template>
          <div v-else class="text-center py-xl text-muted">点击"生成投流素材"</div>
          <div class="flex gap-sm mt-lg">
            <el-button size="large" @click="currentStep = storyboardShots.length ? 7 : 5">← 上一步</el-button>
            <el-button size="large" @click="doGenPromotion" :loading="stepLoading">🔄 重新生成</el-button>
            <el-button type="success" size="large" @click="saveToWarehouse" :loading="saving"><el-icon><FolderAdd /></el-icon> 保存到仓库</el-button>
          </div>
        </div>

        <!-- ===== STEP 9: 入库 ===== -->
        <div v-show="currentStep === 9" class="card">
          <h3 class="font-bold mb-md"><el-icon><FolderAdd /></el-icon> Step 10: 保存与入库</h3>
          <div class="text-center py-lg">
            <el-icon :size="48" color="var(--success)"><CircleCheck /></el-icon>
            <p class="font-bold mt-md text-lg">🎉 创作流程已完成！</p>
            <p class="text-sm text-muted mt-sm">剧本已保存到仓库，你可以随时查看、编辑或送入画布工作台继续制作</p>
          </div>
          <div class="section mt-lg">
            <h4 class="sec-title"><el-icon><Document /></el-icon> 创作摘要</h4>
            <table class="info-tbl">
              <tr><td style="width:80px">选题</td><td>{{ fineTopics[selectedTopic]?.title || '—' }}</td></tr>
              <tr><td>集数</td><td>{{ fineEpisodes }} 集</td></tr>
              <tr><td>大纲</td><td>{{ outlineEpisodes.length }} 集已生成</td></tr>
              <tr><td>剧本</td><td>{{ scriptText ? scriptText.length + ' 字' : '未生成' }}</td></tr>
              <tr><td>分镜</td><td>{{ storyboardShots.length ? storyboardShots.length + ' 镜' : '未生成' }}</td></tr>
              <tr><td>投流素材</td><td>{{ promoData ? promoData.titles?.length + ' 条标题' : '未生成' }}</td></tr>
            </table>
          </div>
          <div class="flex gap-sm mt-lg">
            <el-button size="large" @click="currentStep = 8">← 上一步</el-button>
            <el-button type="primary" size="large" @click="$router.push('/warehouse')"><el-icon><FolderOpened /></el-icon> 前往剧本仓库</el-button>
            <el-button type="success" size="large" @click="$router.push('/canvas')"><el-icon><Brush /></el-icon> 送入画布工作台</el-button>
          </div>
        </div>

      </div><!-- fine mode end -->
    </div><!-- AI writing end -->

    <!-- ==================== 用户上传 ==================== -->
    <div v-else class="card">
      <div class="text-center" style="padding:40px">
        <el-icon :size="48" color="var(--text-tertiary)"><UploadFilled /></el-icon>
        <h3 class="font-bold">上传剧本文件</h3>
        <p class="text-sm text-muted mt-sm mb-lg">支持 .txt 和 .docx，自动解析分集并入库</p>
        <el-upload drag :auto-upload="false" :on-change="onUploadFile" :limit="1" accept=".txt,.docx">
          <el-icon :size="40"><UploadFilled /></el-icon>
          <div class="el-upload__text mt-sm">拖到此处或<em>点击上传</em></div>
        </el-upload>
          <div v-if="uploadFile" class="mt-md">
            <el-tag>{{ uploadFile.name }}</el-tag>
            <el-input v-model="uploadTitle" size="small" placeholder="剧本标题（可选）" class="mt-md" style="max-width:300px" />
            <el-button type="primary" size="large" class="mt-lg" @click="doUpload" :loading="uploadLoading">开始上传并解析</el-button>
            <el-progress v-if="uploadLoading" :percentage="uploadProgress" class="mt-md" />
          </div>
          <el-alert v-if="uploadResult" type="success" :closable="false" class="mt-md">解析完成！《{{ uploadResult.title }}》— {{ uploadResult.episode_count || '?' }}集
            <div class="mt-sm"><el-button size="small" @click="$router.push('/warehouse')">查看仓库</el-button><el-button size="small" @click="$router.push('/tag-editor/' + uploadResult.id)">编辑标签</el-button></div>
          </el-alert>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading, UploadFilled, Cpu, Upload, Lightning, Aim, FolderAdd, FolderOpened, Document, Brush, Refresh, MagicStick, CircleCheck, MapLocation, Reading, TrendCharts, Warning, Star, VideoCamera, Link, DataAnalysis, List, Picture, Scissor } from '@element-plus/icons-vue'
import { scriptApi } from '@/api/script'
import FourAxisTags from './script-gen/components/FourAxisTags.vue'
import HookBar from './script-gen/components/HookStrengthBar.vue'

const $router = useRouter()

// ===== 路径选择 =====
const creationPath = ref('ai')
const genMode = ref('fine')

// ===== 通用 =====
const loading = ref(false); const progress = ref(0)
const saving = ref(false)
const savedScriptId = ref(null)  // 跟踪已保存的剧本 ID，避免重复创建

// ===== 快速模式 =====
const quickIdea = ref('')
const quickSourceType = ref('novel')
const quickTargetType = ref('ai_comic')
const quickWithAdaptation = ref(false)
const quickWithStoryboard = ref(false)
const quickPlatform = ref('douyin'); const quickAudience = ref('female'); const quickEpisodes = ref(40)
const quickTags = reactive({ genre: '言情', plots: ['重生', '先婚后爱'], tones: ['甜宠', '打脸'], setting: '现代' })
const genResult = ref(null)

async function doQuickGen() {
  if (!quickIdea.value.trim()) { ElMessage.warning('请输入创意描述'); return }
  loading.value = true; progress.value = 0; genResult.value = null
  const t = setInterval(() => { if (progress.value < 90) progress.value += Math.random() * 10 }, 500)
  try {
    const res = await scriptApi.genQuick({
      idea: quickIdea.value,
      source_type: quickSourceType.value,
      target_type: quickTargetType.value,
      with_adaptation: quickWithAdaptation.value,
      with_storyboard: quickWithStoryboard.value,
      genre_tag: quickTags.genre,
      plot_tags: quickTags.plots,
      tone_tags: quickTags.tones,
      setting_tag: quickTags.setting,
      platform: quickPlatform.value,
      audience: quickAudience.value,
      episode_count: quickEpisodes.value
    })
    const taskId = res.data?.task_id
    if (taskId) {
      for (let i = 0; i < 30; i++) {
        await sleep(2000)
        const tr = await scriptApi.getTaskStatus(taskId)
        if (tr.data?.status === 'completed') {
          genResult.value = tr.data.result || tr.data.output_data || tr.data
          break
        }
        if (tr.data?.status === 'failed') throw new Error(tr.data.error_msg || '任务执行失败')
      }
      // 轮询超时：尝试用已有数据显示，提示用户
      if (!genResult.value) {
        ElMessage.warning('任务处理时间较长，请稍后刷新查看结果')
        genResult.value = { title: '剧本生成中...', synopsis: '任务仍在处理，请稍后查看' }
      }
    } else {
      genResult.value = res.data || { title: '剧本生成完成', synopsis: '' }
    }
  } catch (e) { ElMessage.error('生成失败: ' + (e?.message || '')) }
  finally { clearInterval(t); progress.value = 100; loading.value = false }
}
async function saveAndGoWarehouse() {
  saving.value = true
  try {
    const r = await scriptApi.createScript({ title: genResult.value?.title || '未命名', synopsis: genResult.value?.synopsis || '', episode_count: quickEpisodes.value, source_type: quickSourceType.value, genre_tag: quickTags.genre, plot_tags: quickTags.plots, tone_tags: quickTags.tones, setting_tag: quickTags.setting, source: 'ai_generated', status: 'draft' })
    savedScriptId.value = r.data?.id || r.data?.script_id
    ElMessage.success('已保存'); $router.push('/warehouse')
  } catch { ElMessage.error('保存失败') }
  finally { saving.value = false }
}
async function saveAndGoCanvas() {
  saving.value = true
  try {
    const res = await scriptApi.createScript({
      title: genResult.value?.title || '未命名',
      synopsis: genResult.value?.synopsis || '',
      episode_count: quickEpisodes.value,
      source_type: quickSourceType.value,
      genre_tag: quickTags.genre, plot_tags: quickTags.plots,
      tone_tags: quickTags.tones, setting_tag: quickTags.setting,
      source: 'ai_generated', status: 'draft'
    })
    ElMessage.success('已保存，正在跳转画布')
    savedScriptId.value = res.data?.id || res.data?.script_id
    $router.push(savedScriptId.value ? `/canvas?scriptId=${savedScriptId.value}` : '/canvas')
  } catch { ElMessage.error('保存失败，请重试') }
  finally { saving.value = false }
}

// ===== 精细模式 =====
const fineSteps = ['源头设置', '选题', '梗概', '大纲', '正文', '去向', '改编', '分镜', '投流', '入库']
const currentStep = ref(0)
const fineIdea = ref(''); const fineSourceType = ref('novel'); const finePlatform = ref('douyin'); const fineAudience = ref('female'); const fineEpisodes = ref(40)
const fineTags = reactive({ genre: '', plots: [], tones: [], setting: '' })
const stepLoading = ref(false); const stepProgress = ref(0)
const selectedTopic = ref(-1)

// 每步数据
const fineTopics = ref([])
const synopsisData = ref(null)
const outlineEpisodes = ref([])
const scriptText = ref(''); const scriptEpIdx = ref(0)
const adaptationTargetType = ref('ai_comic')
const adaptationText = ref('')
const adaptationId = ref(null)
const tier = ref('A'); const storyboardShots = ref([])
const promoData = ref(null)
const promoPicks = reactive({ title: 0 })
const episodeReview = ref(null)
const reviewLoading = ref(false)

function goToStep(n) { if (n >= 0 && n < fineSteps.length) currentStep.value = n }
function selectTopic(i) { selectedTopic.value = i }

// 通用步骤 API 调用 + 轮询
async function runStep(apiFn, params, onDone) {
  stepLoading.value = true; stepProgress.value = 0
  const t = setInterval(() => { if (stepProgress.value < 90) stepProgress.value += Math.random() * 10 }, 500)
  try {
    const res = await apiFn(params); const taskId = res.data?.task_id
    if (taskId) {
      for (let i = 0; i < 25; i++) {
        await sleep(2000); const tr = await scriptApi.getTaskStatus(taskId)
        if (tr.data?.status === 'completed') { onDone(tr.data.result || tr.data.output_data || tr.data); return }
        if (tr.data?.status === 'failed') throw new Error(tr.data.error_msg || '任务执行失败')
      }
      // 轮询超时：尝试用已有数据回调，并提示用户
      ElMessage.warning('任务处理时间较长，请稍后刷新查看结果')
      onDone(res.data || {})
      return
    }
    onDone(res.data || {})
  } catch (e) { ElMessage.error('生成失败: ' + (e?.message || '')) }
  finally { clearInterval(t); stepProgress.value = 100; stepLoading.value = false }
}

const baseParams = () => ({
  idea: fineIdea.value,
  source_type: fineSourceType.value,
  target_type: adaptationTargetType.value,
  genre_tag: fineTags.genre,
  plot_tags: fineTags.plots,
  tone_tags: fineTags.tones,
  setting_tag: fineTags.setting,
  platform: finePlatform.value,
  audience: fineAudience.value,
  episode_count: fineEpisodes.value
})

async function doGenTopic() {
  if (!fineIdea.value.trim()) { ElMessage.warning('请输入创意'); return }
  await runStep(scriptApi.genTopic, baseParams(), (data) => {
    const suggestions = data.suggestions || data.output_data?.suggestions || []
    fineTopics.value = suggestions.map(s => typeof s === 'string' ? { title: s, description: '', matchRate: 85 } : { title: s.title || s, description: s.description || s.desc || '', matchRate: s.match_rate || 85, highlights: s.highlights || [], platformSuggestion: s.platformSuggestion || '' })
    if (!fineTopics.value.length) fineTopics.value = [{ title: '方案A', description: 'AI推荐', matchRate: 92 }, { title: '方案B', description: '备选', matchRate: 85 }]
    currentStep.value = 1
  })
}

async function doGenSynopsis() {
  await runStep(scriptApi.genSynopsis, { ...baseParams(), topic: fineTopics.value[selectedTopic.value]?.title }, (data) => {
    synopsisData.value = { worldBuilding: data.worldBuilding || data.world_building || '', synopsis: data.synopsis || data.raw || '', plotPhases: data.plotPhases || data.plot_phases || ['', '', '', ''], coreConflict: data.coreConflict || data.core_conflict || '', highlights: data.highlights || [] }
    if (currentStep.value === 1) currentStep.value = 2
  })
}

async function doGenOutline() {
  await runStep(scriptApi.genOutline, baseParams(), (data) => {
    const eps = data.episodes || data.output_data?.episodes || []
    outlineEpisodes.value = eps.map((ep, i) => ({ number: ep.number || i + 1, title: ep.title || '', coreEvent: ep.coreEvent || ep.core_event || '', openingHook: ep.openingHook || ep.opening_hook || '', closingHook: ep.closingHook || ep.closing_hook || '', openingHookStrength: ep.openingHookStrength || ep.opening_hook_strength || null, closingHookStrength: ep.closingHookStrength || ep.closing_hook_strength || null, _open: false }))
    if (!outlineEpisodes.value.length) outlineEpisodes.value = Array.from({ length: fineEpisodes.value }, (_, i) => ({ number: i + 1, title: '第' + (i + 1) + '集', coreEvent: '', openingHook: '', closingHook: '', _open: false }))
    if (currentStep.value === 2) currentStep.value = 3
  })
}

async function doGenEpisode() {
  await runStep(scriptApi.genEpisode, baseParams(), (data) => {
    scriptText.value = data.raw || data.script || data.content || data.output_data?.raw || ''
    episodeReview.value = null
    if (currentStep.value === 3) currentStep.value = 4
  })
}

async function saveChapterDraft() {
  if (!scriptText.value.trim()) {
    ElMessage.warning('请先生成或填写正文')
    return
  }
  saving.value = true
  try {
    if (!savedScriptId.value) await saveToWarehouse(false)
    const ep = outlineEpisodes.value[scriptEpIdx.value] || {}
    await scriptApi.createChapterVersion(0, {
      script_id: savedScriptId.value,
      chapter_number: ep.number || scriptEpIdx.value + 1,
      title: ep.title || '第' + (scriptEpIdx.value + 1) + '章',
      content: scriptText.value,
      content_format: fineSourceType.value === 'novel' ? 'novel' : 'screenplay',
      change_summary: '保存源头文本单章版本',
      source: 'manual_edit'
    })
    ElMessage.success('已保存单章正文版本')
  } catch (e) {
    ElMessage.error('保存正文版本失败: ' + (e?.message || ''))
  } finally {
    saving.value = false
  }
}

async function reviewCurrentEpisode() {
  if (!scriptText.value.trim()) {
    ElMessage.warning('请先生成或填写本集剧本')
    return
  }
  const ep = outlineEpisodes.value[scriptEpIdx.value] || {}
  reviewLoading.value = true
  try {
    const res = await scriptApi.reviewEpisodePreview({
      script_id: savedScriptId.value,
      episode_number: ep.number || scriptEpIdx.value + 1,
      title: ep.title || '第' + (scriptEpIdx.value + 1) + '集',
      content: scriptText.value,
      opening_hook: ep.openingHook || '',
      closing_hook: ep.closingHook || '',
      core_event: ep.coreEvent || '',
      next_episode_promise: ep.nextEpisodePromise || ep.next_episode_promise || '',
      genre_tag: fineTags.genre,
      audience_mode: fineAudience.value,
      platform: finePlatform.value
    })
    episodeReview.value = res.data || null
    if (episodeReview.value?.overall_status === 'pass') ElMessage.success('本集联合审核通过')
    else ElMessage.warning('本集仍有优化建议')
  } catch (e) {
    ElMessage.error('联合审核失败: ' + (e?.message || ''))
  } finally {
    reviewLoading.value = false
  }
}

async function doGenStoryboard() {
  await runStep(scriptApi.genStoryboard, {
    ...baseParams(),
    source_text: adaptationText.value || scriptText.value,
    adaptation_version_id: adaptationId.value,
    tier: tier.value
  }, (data) => {
    storyboardShots.value = data.shots || data.storyboard || data.output_data?.shots || []
    if (!storyboardShots.value.length) storyboardShots.value = defaultShots()
    currentStep.value = 7
  })
}

async function doGenPromotion() {
  await runStep(scriptApi.genPromotion, { ...baseParams(), source_text: adaptationText.value || scriptText.value }, (data) => {
    promoData.value = { titles: data.titles || [], coverCopy: data.coverCopy || data.cover_copy || [], threeSecHooks: data.threeSecHooks || data.three_sec_hooks || [], clipScripts: data.clipScripts || data.clip_scripts || [], commentGuides: data.commentGuides || data.comment_guides || [] }
    if (!promoData.value.titles.length) promoData.value = { titles: ['悬念式标题A', '反转式标题B', '痛点式标题C'], coverCopy: ['封面方案1', '封面方案2'], threeSecHooks: ['开场3秒抓住注意力', '悬疑开场'], clipScripts: ['切片脚本示例'], commentGuides: ['评论区引导'] }
    currentStep.value = 8
  })
}

async function doGenAdaptation() {
  await runStep(scriptApi.genAdaptation, {
    ...baseParams(),
    target_type: adaptationTargetType.value,
    source_text: scriptText.value,
    opening_hook: outlineEpisodes.value[scriptEpIdx.value]?.openingHook || '',
    closing_hook: outlineEpisodes.value[scriptEpIdx.value]?.closingHook || ''
  }, (data) => {
    adaptationText.value = data.content || data.raw || data.output_data?.content || ''
    if (!adaptationText.value) adaptationText.value = '# 改编脚本\n\n请在这里继续编辑 AI 生成的改编脚本。'
  })
}

async function saveAdaptationVersion() {
  if (!adaptationText.value.trim()) {
    ElMessage.warning('请先生成或填写改编脚本')
    return
  }
  saving.value = true
  try {
    if (!savedScriptId.value) await saveToWarehouse(false)
    const res = await scriptApi.createAdaptation({
      script_id: savedScriptId.value,
      target_type: adaptationTargetType.value,
      title: adaptationTitle(adaptationTargetType.value),
      content: adaptationText.value,
      source_text: scriptText.value,
      hook_strategy: {
        inherit_source_hook: true,
        opening_hook: outlineEpisodes.value[scriptEpIdx.value]?.openingHook || '',
        closing_hook: outlineEpisodes.value[scriptEpIdx.value]?.closingHook || ''
      }
    })
    adaptationId.value = res.data?.id
    ElMessage.success('已保存改编脚本版本')
  } catch (e) {
    ElMessage.error('保存改编脚本失败: ' + (e?.message || ''))
  } finally {
    saving.value = false
  }
}

async function saveToWarehouse(moveToDone = true) {
  saving.value = true
  try {
    // 构建完整的分集数据
    const episodes = outlineEpisodes.value.map((ep, i) => ({
      number: ep.number || i + 1,
      title: ep.title || '第' + (i + 1) + '集',
      core_event: ep.coreEvent || '',
      opening_hook: ep.openingHook || '',
      closing_hook: ep.closingHook || '',
      opening_hook_strength: ep.openingHookStrength || null,
      closing_hook_strength: ep.closingHookStrength || null,
      // 将当前编辑的剧本正文关联到对应集
      content: i === scriptEpIdx.value ? scriptText.value : ''
    }))
    // 如果已保存过则更新，否则创建
    let res
    if (savedScriptId.value) {
      res = await scriptApi.updateScript(savedScriptId.value, {
        title: fineTopics.value[selectedTopic.value]?.title || synopsisData.value?.synopsis?.slice(0, 20) || '未命名剧本',
        synopsis: synopsisData.value?.synopsis || '',
        episode_count: fineEpisodes.value,
        source_type: fineSourceType.value,
        genre_tag: fineTags.genre, plot_tags: fineTags.plots,
        tone_tags: fineTags.tones, setting_tag: fineTags.setting,
        status: 'draft'
      })
    } else {
      res = await scriptApi.createScript({
        title: fineTopics.value[selectedTopic.value]?.title || synopsisData.value?.synopsis?.slice(0, 20) || '未命名剧本',
        synopsis: synopsisData.value?.synopsis || '',
        episode_count: fineEpisodes.value,
        source_type: fineSourceType.value,
        episodes: episodes,
        content: scriptText.value,
        genre_tag: fineTags.genre, plot_tags: fineTags.plots,
        tone_tags: fineTags.tones, setting_tag: fineTags.setting,
        source: 'ai_generated', status: 'draft'
      })
      savedScriptId.value = res.data?.id || res.data?.script_id
    }
    ElMessage.success('已保存到仓库！')
    if (moveToDone) currentStep.value = 9
  } catch { ElMessage.error('保存失败') }
  finally { saving.value = false }
}

async function optimizeHooks() {
  if (!outlineEpisodes.value.length) {
    ElMessage.warning('请先生成大纲')
    return
  }
  try {
    // 如果尚未保存，先保存以获取 scriptId
    if (!savedScriptId.value) {
      const res = await scriptApi.createScript({
        title: fineTopics.value[selectedTopic.value]?.title || synopsisData.value?.synopsis?.slice(0, 20) || '未命名剧本',
        synopsis: synopsisData.value?.synopsis || '',
        episode_count: fineEpisodes.value,
        genre_tag: fineTags.genre, plot_tags: fineTags.plots,
        tone_tags: fineTags.tones, setting_tag: fineTags.setting,
        source: 'ai_generated', status: 'draft'
      })
      savedScriptId.value = res.data?.id || res.data?.script_id
    }
    if (savedScriptId.value) {
      await scriptApi.generateAllHooks(savedScriptId.value)
      ElMessage.success('钩子优化已启动')
    } else {
      ElMessage.warning('无法获取剧本ID，请先保存剧本到仓库')
    }
  } catch { ElMessage.info('钩子优化功能需先保存剧本到仓库') }
}

function defaultShots() { return [{ shotNo: 'SH001', duration: '3s', shotSize: 'MS', cameraMove: '跟拍', visual: '苏小晚端咖啡进办公室', dialogue: '—', function: '进入' }, { shotNo: 'SH002', duration: '5s', shotSize: 'MCU', cameraMove: '固定', visual: '林默抬头看', dialogue: '林默：之前在哪工作？', function: '试探' }, { shotNo: 'SH003', duration: '4s', shotSize: 'CU', cameraMove: '特写', visual: '手指紧握咖啡杯', dialogue: '苏小晚：一家小公司', function: '隐藏' }, { shotNo: 'SH004', duration: '6s', shotSize: 'MS', cameraMove: '缓推', visual: '视线落在手腕伤疤', dialogue: '林默：手腕上的伤…', function: '发现' }, { shotNo: 'SH005', duration: '5s', shotSize: 'MS', cameraMove: '固定', visual: '苏小晚退出，林默看门口', dialogue: '—', function: '钩子' }] }

function adaptationTitle(type) {
  if (type === 'short_drama') return '短剧改编脚本'
  if (type === 'web_drama') return '网剧改编脚本'
  if (type === 'tvc') return 'TVC改编脚本'
  return 'AI漫剧改编脚本'
}

// ===== 用户上传 =====
const uploadFile = ref(null); const uploadTitle = ref(''); const uploadLoading = ref(false); const uploadProgress = ref(0); const uploadResult = ref(null)
function onUploadFile(file) { uploadFile.value = file.raw; uploadTitle.value = file.name.replace(/\.(txt|docx)$/i, '') }
async function doUpload() {
  if (!uploadFile.value) { ElMessage.warning('请选择文件'); return }
  uploadLoading.value = true; uploadProgress.value = 0
  try {
    const fd = new FormData(); fd.append('file', uploadFile.value); fd.append('title', uploadTitle.value || uploadFile.value.name)
    const res = await scriptApi.uploadScript(fd)
    uploadProgress.value = 100; uploadResult.value = res.data || { title: uploadTitle.value, episode_count: 1, id: res.data?.script_id }
    ElMessage.success('上传解析完成')
  } catch { ElMessage.error('上传失败') }
  finally { uploadLoading.value = false }
}

const sleep = (ms) => new Promise(r => setTimeout(r, ms))
</script>

<style scoped>
/* ===== Path Tabs ===== */
.path-tabs { display:flex; gap:12px; }
.path-tab { flex:1; padding:14px; text-align:center; border-radius:10px; cursor:pointer; font-weight:600; font-size:15px;
  background:var(--bg-surface); border:2px solid var(--border); color:var(--text-primary); transition:all .2s; }
.path-tab:hover { border-color:var(--accent-border); background:var(--accent-bg); }
.path-tab.active { border-color:var(--accent); background:var(--accent-bg); color:var(--accent); }

/* ===== Mode Cards ===== */
.mode-cards { }
.mode-card { padding:20px; border-radius:12px; cursor:pointer;
  background:var(--bg-surface); border:2px solid var(--border-light); color:var(--text-primary); transition:all .2s; text-align:center; }
.mode-card:hover { border-color:var(--accent-border); box-shadow:var(--shadow-md); }
.mode-card.active { border-color:var(--accent); background:var(--accent-bg); }
.mode-icon { font-size:32px; color:var(--accent); margin-bottom:8px; }
.mode-icon .el-icon { font-size:inherit; }

/* ===== Step Indicator ===== */
.step-indicator { display:flex; gap:6px; flex-wrap:wrap; margin-bottom:24px; }
.step-dot { display:flex; align-items:center; gap:6px; padding:6px 12px; border-radius:8px; cursor:pointer; font-size:12px;
  background:var(--bg-surface); border:1px solid var(--border); color:var(--text-secondary); transition:all .15s; }
.step-dot:hover { border-color:var(--accent-border); color:var(--accent); }
.step-dot.active { border-color:var(--accent); color:var(--accent); background:var(--accent-bg); font-weight:600; }
.step-dot.done { border-color:var(--success); color:var(--success); background:var(--success-bg); }
.step-num { font-weight:700; min-width:16px; text-align:center; }

/* ===== Topic Cards ===== */
.topic-grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(250px, 1fr)); gap:12px; }
.topic-card { padding:16px; background:var(--bg-surface); border:2px solid var(--border-light); border-radius:10px; cursor:pointer; color:var(--text-primary); transition:all .15s; }
.topic-card:hover { border-color:var(--accent-border); box-shadow:var(--shadow-sm); }
.topic-card.picked { border-color:var(--accent); background:var(--accent-bg); }
.topic-header { display:flex; justify-content:space-between; align-items:center; }
.topic-tags { display:flex; gap:4px; flex-wrap:wrap; }

/* ===== Sections ===== */
.section { margin-bottom:12px; }
.sec-title { font-size:13px; font-weight:600; color:var(--text-primary); margin-bottom:6px; display:flex; align-items:center; gap:4px; }
.sec-title .el-icon { color:var(--accent); }

/* ===== Episode Cards ===== */
.ep-list { max-height:500px; overflow-y:auto; }
.ep-card { background:var(--bg-surface-hover); border:1px solid var(--border-light); border-radius:8px; margin-bottom:6px; overflow:hidden; cursor:pointer; color:var(--text-primary); }
.ep-card:hover { border-color:var(--accent-border); }
.ep-header { display:flex; align-items:center; gap:10px; padding:10px 14px; }
.ep-header:hover { background:var(--accent-bg); }
.ep-num { font-weight:700; color:var(--accent); font-size:12px; min-width:40px; }
.ep-title { flex:1; font-size:13px; }
.ep-body { padding:0 14px 14px; border-top:1px solid var(--border-light); padding-top:10px; }

/* ===== Script Layout ===== */
.script-layout { display:grid; grid-template-columns:140px 1fr; gap:16px; }
.ep-nav-col { max-height:400px; overflow-y:auto; }
.ep-nav-row { padding:8px; border-radius:6px; cursor:pointer; font-size:11px; margin-bottom:4px; color:var(--text-secondary); }
.ep-nav-row:hover { background:var(--accent-bg); color:var(--text-primary); }
.ep-nav-row.active { background:var(--accent-bg); color:var(--accent); font-weight:600; }
.script-toolbar { display:flex; justify-content:space-between; align-items:center; margin-bottom:8px; color:var(--text-secondary); }
.script-input :deep(textarea) { font-family:'SF Mono',monospace; font-size:12px; line-height:1.8; }
.episode-review-panel { border:1px solid var(--border-light); background:var(--bg-surface-hover); border-radius:8px; padding:12px; color:var(--text-primary); }
.review-head { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-bottom:10px; }
.review-status { display:inline-flex; margin-left:8px; padding:2px 8px; border-radius:999px; font-size:11px; font-weight:600; }
.review-status.pass { color:var(--success); background:var(--success-bg); }
.review-status.warn { color:#b45309; background:#fef3c7; }
.review-score { font-weight:700; color:var(--accent); }
.review-score-grid { display:grid; grid-template-columns:repeat(3, minmax(0, 1fr)); gap:8px; margin-bottom:10px; }
.review-score-card { padding:8px; border-radius:6px; background:var(--bg-surface); border:1px solid var(--border-light); display:flex; align-items:center; justify-content:space-between; font-size:12px; }
.review-score-card span { color:var(--text-secondary); }
.review-agent-list { display:grid; gap:8px; }
.review-agent-card { border:1px solid var(--border-light); background:var(--bg-surface); border-radius:6px; padding:10px; }
.review-agent-title { display:flex; align-items:center; justify-content:space-between; margin-bottom:4px; font-size:13px; }
.review-agent-title span { color:var(--accent); font-weight:700; }
.review-agent-card p { margin:0 0 6px; color:var(--text-secondary); font-size:12px; }
.review-issues { display:grid; gap:4px; margin-bottom:6px; }
.review-issue { padding:6px 8px; border-radius:5px; font-size:12px; background:var(--warning-bg); color:#92400e; }
.review-issue.high { background:#fee2e2; color:#991b1b; }
.review-issue.medium { background:#fef3c7; color:#92400e; }
.review-suggestions { display:flex; gap:6px; flex-wrap:wrap; }
.review-suggestions span { font-size:11px; color:var(--text-secondary); background:var(--bg-app); border-radius:999px; padding:4px 8px; }
.review-actions { display:flex; justify-content:flex-end; gap:8px; margin-top:10px; }

/* ===== Tier Tabs ===== */
.tier-tabs { display:flex; gap:4px; font-size:11px; }
.tier-tab { padding:4px 10px; cursor:pointer; border-radius:4px; color:var(--text-secondary); background:var(--bg-surface-hover); }
.tier-tab:hover { color:var(--text-primary); }
.tier-tab.active { background:var(--accent-bg); color:var(--accent); font-weight:600; }

/* ===== Tables ===== */
.shot-tbl { width:100%; font-size:11px; color:var(--text-primary); }
.shot-tbl th { font-size:10px; padding:6px; background:var(--bg-app); color:var(--text-secondary); font-weight:600; }
.shot-tbl td { padding:4px 6px; border-bottom:1px solid var(--border-light); color:var(--text-primary); }
.info-tbl { width:100%; font-size:11px; color:var(--text-primary); }
.info-tbl td { padding:4px 8px; border-bottom:1px solid var(--border-light); color:var(--text-primary); }

/* ===== Result Cards ===== */
.title-card { padding:12px; background:var(--bg-surface); border:2px solid var(--border-light); border-radius:8px; cursor:pointer; font-size:12px; color:var(--text-primary); }
.title-card:hover { border-color:var(--accent-border); box-shadow:var(--shadow-sm); }
.title-card.picked { border-color:var(--accent); background:var(--accent-bg); }
.hook-line { padding:8px 12px; background:var(--bg-surface-hover); border-radius:6px; margin-bottom:4px; font-size:13px; color:var(--text-primary); }
.cover-card { padding:10px; background:var(--bg-surface-hover); border-radius:6px; margin-bottom:4px; font-size:12px; color:var(--text-primary); }
.gen-result { padding:16px; background:var(--success-bg); border-radius:8px; border:1px solid #a7f3d0; color:var(--text-primary); }
.decision-strip { display:flex; align-items:center; gap:12px; flex-wrap:wrap; padding:10px 12px; border:1px solid var(--border-light); border-radius:8px; background:var(--bg-surface-hover); }
.decision-note { padding:10px 12px; border:1px solid var(--border-light); border-radius:8px; background:var(--bg-surface); color:var(--text-secondary); font-size:12px; }
.decision-grid { display:grid; grid-template-columns:repeat(auto-fit, minmax(180px, 1fr)); gap:12px; }
.decision-card { padding:14px; border:1px solid var(--border-light); border-radius:8px; background:var(--bg-surface); cursor:pointer; transition:all .15s; display:flex; flex-direction:column; gap:6px; min-height:110px; }
.decision-card:hover { border-color:var(--accent-border); box-shadow:var(--shadow-sm); transform:translateY(-1px); }
.decision-card.primary { border-color:var(--accent); background:var(--accent-bg); }
.decision-card strong { color:var(--text-primary); }
.decision-card span { color:var(--text-secondary); font-size:12px; line-height:1.5; }
.hook-strategy-card { display:flex; align-items:flex-start; gap:10px; padding:10px 12px; border:1px solid var(--accent-border); border-radius:8px; background:var(--accent-bg); font-size:12px; }
.hook-strategy-card strong { color:var(--accent); white-space:nowrap; }
.hook-strategy-card span { color:var(--text-secondary); line-height:1.5; }

/* ===== Utilities ===== */
.table-wrap { overflow-x:auto; }
.w-full { width:100%; }
</style>
