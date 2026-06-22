<template>
  <div class="canvas-page">
    <!-- Toolbar -->
    <div class="canvas-toolbar">
      <div class="flex items-center gap-md">
        <el-dropdown trigger="click">
          <button class="project-menu-btn"><el-icon style="vertical-align:-2px"><Brush /></el-icon> {{ state.projectName.value }} ▾</button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="ElMessage.info('全部项目入口待接入项目列表')">全部项目</el-dropdown-item>
              <el-dropdown-item @click="resetLocalCanvas">新建空白画布</el-dropdown-item>
              <el-dropdown-item @click="renameCanvas">重命名画布</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <span :class="['badge', statusBadge(state.projectStatus.value)]">{{ state.projectStatus.value }}</span>
        <span v-if="canvas.localMode.value" class="badge badge-warning">本地可操作模式</span>
        <span class="text-sm text-muted">自动保存于 {{ state.lastSaved.value }}</span>
      </div>
      <div class="flex gap-sm">
        <el-button size="small" @click="ElMessage.info('分享当前画布入口待接入')">发布&分享</el-button>
        <el-button size="small" @click="ElMessage.info('通知中心入口待接入')">通知</el-button>
        <el-button size="small" @click="ElMessage.info('会员中心入口待接入')">会员中心</el-button>
        <span class="zoom-label">缩放: {{ state.zoomLevel.value }}%</span>
        <el-button size="small" circle @click="state.zoomIn()">+</el-button>
        <el-button size="small" circle @click="state.zoomOut()">−</el-button>
        <el-button size="small" @click="state.resetZoom()">1:1</el-button>
        <el-button type="primary" size="small" @click="openTimeline">
          <el-icon><VideoPlay /></el-icon> 合成导出
        </el-button>
      </div>
    </div>

    <div class="canvas-body">
      <!-- LEFT Panel -->
      <div class="canvas-left-panel">
        <div class="canvas-tabs">
          <div v-for="tab in leftTabs" :key="tab.key"
               :class="['canvas-tab', { active: state.activeLeftTab.value === tab.key }]"
               @click="state.activeLeftTab.value = tab.key"><el-icon :size="14"><component :is="tab.icon" /></el-icon> {{ tab.label }}</div>
        </div>

        <!-- Add Node -->
        <div v-if="state.activeLeftTab.value === 'add'" class="tab-content">
          <div class="text-xs font-bold mb-sm text-muted">生产节点（双击画布或点击）</div>
          <div v-for="group in nodeTypeGroups" :key="group.name" class="node-group">
            <div class="node-group-title">{{ group.name }}</div>
            <div v-for="nt in group.items" :key="nt.type"
                 class="card card-hover node-add-card"
                 @click="handleAddNode(nt.type)">
              <span class="node-icon"><el-icon><component :is="nt.icon" /></el-icon></span>
              <div><strong>{{ nt.label }}</strong><div class="text-xs text-muted">{{ nt.desc }}</div></div>
            </div>
          </div>
          <div class="text-xs font-bold mb-sm mt-lg text-muted">添加资源</div>
          <div class="upload-drop" @click="ElMessage.info('可将图片/视频/音频直接拖入画布，或接入上传组件')">
            拖入图片 / 视频 / 音频，或点击上传
          </div>
          <div class="text-xs font-bold mb-sm mt-lg text-muted">Slash 快捷命令</div>
          <el-button v-for="cmd in canvas.SLASH_COMMANDS" :key="cmd" size="small"
                     class="slash-btn" @click="handleSlash(cmd)">{{ cmd }}</el-button>
        </div>

        <!-- Workflows -->
        <div v-if="state.activeLeftTab.value === 'workflow'" class="tab-content">
          <div class="text-xs font-bold mb-sm text-muted">已保存工作流</div>
          <div v-for="wf in canvas.workflows.value" :key="wf.id || wf.uuid"
               class="card card-hover" style="padding:10px;margin-bottom:8px"
               @click="handleApplyWorkflow(wf)">
            <strong style="font-size:12px">{{ wf.name }}</strong>
            <div class="text-xs text-muted">{{ wf.description }}</div>
          </div>
        </div>

        <!-- Assets -->
        <div v-if="state.activeLeftTab.value === 'assets'" class="tab-content">
          <div class="text-xs font-bold mb-sm text-muted">资产库</div>
          <div class="reference-actions">
            <el-button size="small" type="primary" @click="quickCreateProductionChain">创建脚本生产链</el-button>
            <el-button size="small" @click="ElMessage.info('将选中节点保存为资产，待接入资产接口')">保存为资产</el-button>
            <el-button size="small" @click="ElMessage.info('批量使用资产到当前画布')">批量使用</el-button>
          </div>
          <div v-for="asset in projectAssets" :key="asset.asset_id"
               class="card card-hover" style="padding:8px;margin-bottom:4px;display:flex;align-items:center">
            <span>{{ assetIcon(asset.asset_type) }}</span> {{ asset.name }}
            <span :class="['badge', maturityBadge(asset.maturity_level)]" style="margin-left:auto">
              {{ asset.maturity_level || 'L0' }}
            </span>
          </div>
        </div>

        <!-- History -->
        <div v-if="state.activeLeftTab.value === 'history'" class="tab-content">
          <div class="text-xs font-bold mb-sm text-muted">生成历史</div>
          <div class="reference-actions">
            <el-button size="small" type="primary" @click="$router.push('/asset-history')">打开历史记录</el-button>
            <el-button size="small" @click="ElMessage.info('批量下载生成历史，待接入后端')">批量下载</el-button>
            <el-button size="small" @click="ElMessage.info('批量删除生成历史，待接入后端')">批量删除</el-button>
            <el-button size="small" @click="ElMessage.info('批量使用历史资源到当前画布')">批量使用</el-button>
          </div>
        </div>

        <div v-if="state.activeLeftTab.value === 'tutorial'" class="tab-content">
          <div class="text-xs font-bold mb-sm text-muted">教程</div>
          <div class="tutorial-list">
            <button @click="ElMessage.info('教程：双击空白画布新建节点')">新建节点</button>
            <button @click="ElMessage.info('教程：从节点右侧端口拖线到输入点建立连接')">节点连线</button>
            <button @click="ElMessage.info('教程：脚本节点按确认镜头、整理资产、合成提示词、批量生成执行')">脚本节点</button>
            <button @click="ElMessage.info('教程：视频合成需要连接两个以上视频/音频节点')">视频合成</button>
          </div>
        </div>
      </div>

      <!-- CANVAS AREA -->
      <div class="canvas-area"
           ref="canvasAreaRef"
           @dblclick="onCanvasDoubleClick"
           @drop="onCanvasDrop"
           @dragover.prevent
           @mousedown="onCanvasMouseDown"
           @mousemove="onCanvasMouseMove"
           @mouseup="onCanvasMouseUp"
           @mouseleave="onCanvasMouseLeave"
           @click.self="deselectCanvas()">
        <!-- Minimap -->
        <div class="minimap">
          <strong>{{ canvas.nodes.value.length }}</strong>
          <span>节点</span>
          <small>{{ state.zoomLevel.value }}%</small>
        </div>

        <div class="canvas-stage" :style="stageStyle" @click.self="deselectCanvas()">
          <!-- SVG Connections -->
          <svg class="connection-layer" :width="state.canvasSize.value.width" :height="state.canvasSize.value.height">
            <g v-for="conn in connectionPaths" :key="conn.id">
              <path :d="conn.path"
                    class="connection-hit"
                    @click.stop="selectConnection(conn.id)" />
              <path :d="conn.path"
                    :class="['connection-path', { selected: selectedConnectionId === conn.id }]" />
            </g>
            <path v-if="tempConnectionLine" :d="tempConnectionLine" class="connection-path temp-connection" />
          </svg>

          <button v-if="selectedConnectionForPanel"
                  class="connection-delete"
                  :style="{ left: selectedConnectionForPanel.midX + 'px', top: selectedConnectionForPanel.midY + 'px' }"
                  @mousedown.stop
                  @click.stop="deleteSelectedConnection">
            删除连线
          </button>

          <!-- Nodes -->
          <div v-for="group in presetGroupFrames" :key="group.id"
               :class="['preset-group-frame', { dragging: groupDrag.active && groupDrag.groupId === group.id }]"
               :style="{ left: group.x + 'px', top: group.y + 'px', width: group.width + 'px', height: group.height + 'px' }"
               @mousedown.stop="(e) => onPresetGroupMouseDown(e, group)">
            <div class="preset-group-title">{{ group.title }}</div>
          </div>

          <div v-for="node in canvas.nodes.value" :key="nodeKey(node)"
               :class="['canvas-node', 'node-' + node.type, { selected: state.selectedNodeId.value === nodeKey(node), 'node-dragging': nodeDrag.active && nodeDrag.nodeId === nodeKey(node) }]"
               :style="{ left: (node.x || 0) + 'px', top: (node.y || 0) + 'px', width: nodeW(node) + 'px' }"
               @mousedown="(e) => onNodeMouseDown(e, node)"
               @click.stop="selectNode(node)"
               @contextmenu.prevent="state.openContextMenu($event, nodeKey(node))">
            <div class="node-header">
              <span><el-icon :size="14"><component :is="nodeIcon(node.type)" /></el-icon> {{ node.name || node.label || '节点' }}</span>
              <div v-if="node.type !== 'text'" class="flex gap-sm">
                <span :class="['node-status', nodeStatusClass(node.status)]">{{ nodeStatusText(node.status) }}</span>
                <span class="node-cost">预计 {{ estimatedCostForNode(node.type) }} 积分</span>
                <el-button link size="small" v-if="node.type === 'script'" @click="openShotEditor(node)"></el-button>
              </div>
            </div>
            <div class="node-body">
              <div v-if="node.type === 'script'" class="node-script-table">
                <div class="script-progress">
                  <span>确认镜头信息</span>
                  <span>整理资产</span>
                  <span>合成提示词</span>
                  <span>批量生成</span>
                </div>
                <table><thead><tr><th>镜号</th><th>景别</th><th>画面</th><th>对白</th></tr></thead>
                  <tbody><tr v-for="s in getNodeShots(node).slice(0,4)" :key="s.id || s.uuid || s.shot_no">
                    <td><code>{{ s.shot_no || s.id }}</code></td><td>{{ s.shot_size || s.shotType }}</td>
                    <td>{{ s.visual_description || s.content }}</td><td>{{ getDialogueText(s) }}</td>
                  </tr></tbody></table>
                <div v-if="!getNodeShots(node).length" class="empty-node-tip">未导入分镜，点击“分镜表”维护镜头字段</div>
              </div>
              <div v-else-if="node.type === 'image'" class="canvas-mock" style="min-height:100px">
                <img v-if="getNodePreviewUrl(node)" :src="getNodePreviewUrl(node)" class="node-preview-image" alt="图片预览" />
                <div v-else class="media-placeholder image-placeholder"><el-icon><Picture /></el-icon> {{ getNodePrompt(node) || '图片预览' }}</div>
              </div>
              <div v-else-if="node.type === 'video'" class="canvas-mock" style="min-height:100px">
                <div class="media-placeholder video-placeholder"><span>▶</span></div>
              </div>
              <div v-else-if="node.type === 'director'" class="director-node-preview">
                <div class="director-mini-stage">
                  <span class="director-mini-person"></span>
                  <span class="director-mini-box"></span>
                  <span class="director-mini-camera"></span>
                </div>
                <div class="director-node-meta">
                  <span>元素 {{ getDirectorData(node).elements.length }}</span>
                  <span>截图 {{ getDirectorData(node).shots.length }}</span>
                  <span>{{ getDirectorData(node).aspect }}</span>
                </div>
              </div>
              <div v-else-if="node.type === 'text'" class="text-node-body">
                <div v-if="getTextNodeMode(node) === 'manual'" class="text-manual-editor">
                  <div class="text-editor-toolbar">
                    <button title="清除格式">⊘</button>
                    <span></span>
                    <button>H1</button><button>H2</button><button>H3</button>
                    <button>¶</button><button>B</button><button><i>I</i></button>
                    <button>☷</button><button>↕</button><button>—</button>
                    <span></span>
                    <button></button><button></button>
                  </div>
                  <textarea :value="getNodePrompt(node)"
                            placeholder="输入内容..."
                            @input="setTextNodeDraft(node, $event.target.value)"
                            @change="persistTextNodeDraft(node, $event.target.value)"></textarea>
                </div>
                <div v-else-if="getTextNodeMode(node) === 'prompt'" class="text-prompt-card">
                  {{ getNodePrompt(node) || '输入文本提示词...' }}
                </div>
                <div v-else class="text-choice-panel">
                  <div class="text-choice-empty">
                    <span></span><span></span><span></span><small></small>
                  </div>
                  <div class="text-choice-label">尝试:</div>
                  <button @click.stop="handleTextPreset(node, 'manual')"><span>▤</span>自己编写内容</button>
                  <button @click.stop="handleTextPreset(node, 'text_to_video')"><span>▶</span>文生视频</button>
                  <button @click.stop="handleTextPreset(node, 'image_to_prompt')"><span>▧</span>图片反推提示词</button>
                  <button @click.stop="handleTextPreset(node, 'text_to_music')"><span>♩</span>文字生音乐</button>
                </div>
              </div>
              <div v-else-if="node.type === 'audio'" style="padding:8px">
                <div class="audio-placeholder">
                  <span></span><span></span><span></span><span></span><span></span>
                </div>
              </div>
              <div v-else style="padding:8px;font-size:11px;color:#888">
                {{ getNodePrompt(node) || node.type + ' 节点' }}
              </div>
            </div>
            <!-- Node Actions -->
            <div class="node-actions compact" v-if="!['script', 'director', 'text'].includes(node.type)">
              <el-button v-for="act in nodeActions(node.type)" :key="act.label"
                         size="small" @click.stop="handleNodeAction(node, act)">
                {{ act.label }}
              </el-button>
            </div>
            <div v-if="node.type === 'script'" class="node-actions">
              <el-button size="small" @click="synthesizeScriptPrompts(node)">合成提示词</el-button>
              <el-button type="primary" size="small" @click="handleBatchGenerate(node, 'image')"><el-icon><Picture /></el-icon> 批量生图</el-button>
              <el-button size="small" @click="handleBatchGenerate(node, 'video')"><el-icon><VideoCamera /></el-icon> 批量视频</el-button>
              <el-button size="small" @click="openShotEditor(node)"><el-icon><List /></el-icon> 分镜表</el-button>
            </div>
            <div v-if="node.type === 'director'" class="node-actions">
              <el-button type="primary" size="small" @click.stop="openDirectorDesk(node)">打开导演台</el-button>
              <el-button size="small" @click.stop="captureDirectorShot(node)">机位截图</el-button>
              <el-button size="small" @click.stop="sendDirectorShotToCanvas(node)">发送到画布</el-button>
            </div>
            <div class="node-out-port" title="拖出连线" @mousedown.stop="startConnectionDrag(node)"></div>
            <div :class="['node-in-port', { connectable: isConnectableTarget(node) }]"
                 title="输入连接"
                 @mouseup.stop="completeConnectionDrag(node)"></div>
          </div>

          <CanvasNodeAgentBox
            v-if="selectedNodeForPanel?.type === 'text'"
            :style="textNodeAgentStyle"
            :project-id="state.projectId.value"
            :node="selectedNodeForPanel"
            :local-mode="canvas.localMode.value"
            @applied="handleTextAgentApplied"
            @close="selectedNodeForPanel = null" />
        </div>

        <!-- Node Create Menu (on double click) -->
        <NodeCreateMenu :visible="createMenuVisible" :x="createMenuPos.x" :y="createMenuPos.y"
                        :node-types="canvas.NODE_TYPES" @select="onNodeTypeSelect" />

        <!-- Floating Add Button -->
        <div class="floating-add">
          <el-button size="default" type="primary" round @click="showCreateMenuAtCenter">
            <el-icon><Plus /></el-icon> 添加节点
          </el-button>
        </div>

        <div v-if="state.contextMenu.value.visible"
             class="node-context-menu"
             :style="{ left: state.contextMenu.value.x + 'px', top: state.contextMenu.value.y + 'px' }"
             @mousedown.stop>
          <button @click="saveSelectedAsAsset">保存为资产</button>
          <button @click="copySelectedNode(false)">复制节点</button>
          <button @click="copySelectedNode(true)">复用节点（保留连线）</button>
          <button class="danger" @click="deleteContextNode">删除节点</button>
        </div>
      </div>

      <!-- RIGHT Panel: Node Properties -->
      <NodePropertyPanel v-if="selectedNodeForPanel"
                         :node="selectedNodeForPanel"
                         :shots="getNodeShots(selectedNodeForPanel)"
                         @close="selectedNodeForPanel = null"
                         @update="(u) => handleNodeUpdate(selectedNodeForPanel, u)"
                         @openShotEditor="openShotEditor(selectedNodeForPanel)"
                         @generate="(n) => handleBatchGenerate(n, n.type === 'image' ? 'image' : 'video')"
                         @duplicate="handleDuplicateNode"
                         @delete="(n) => handleDeleteNode(n)" />
    </div>

    <!-- BOTTOM: Timeline -->
    <div class="canvas-bottom">
      <div v-if="selectedNodeForPanel" class="generator-panel">
          <div class="generator-head">
            <div>
              <strong><el-icon :size="14"><component :is="nodeIcon(selectedNodeForPanel.type)" /></el-icon> {{ selectedNodeForPanel.name || nodeLabel(selectedNodeForPanel.type) }}</strong>
            <span class="text-xs text-muted">{{ generatorDescription(selectedNodeForPanel.type) }}</span>
            </div>
          <div class="flex gap-sm">
            <el-button size="small" @click="createDownstreamNode(selectedNodeForPanel, recommendedNextNode(selectedNodeForPanel.type))">
              添加下游节点
            </el-button>
            <el-button type="primary" size="small" @click="runGeneratorForSelected">执行生成</el-button>
          </div>
        </div>
        <div class="generator-grid">
          <el-input v-model="generatorPrompt" type="textarea" :rows="3"
                    placeholder="输入剧情、分镜描述、角色设定、参考要求或生成提示词" />
          <div class="generator-controls">
            <label>模式</label>
            <el-select v-model="generatorConfig.mode" size="small">
              <el-option v-for="mode in generatorModes(selectedNodeForPanel.type)"
                         :key="mode.value"
                         :label="mode.label"
                         :value="mode.value" />
            </el-select>
            <label>模型</label>
            <el-select v-model="generatorConfig.modelId" size="small">
              <el-option label="DeepSeek V3" value="deepseek-v3" />
              <el-option label="Seedream 5.0" value="seedream-5.0" />
              <el-option label="Seedance 2.0" value="seedance-2.0" />
              <el-option label="Volcano TTS" value="volcano-tts" />
            </el-select>
            <label>比例</label>
            <el-select v-model="generatorConfig.aspectRatio" size="small">
              <el-option label="9:16" value="9:16" />
              <el-option label="16:9" value="16:9" />
              <el-option label="1:1" value="1:1" />
            </el-select>
            <label>时长</label>
            <el-input-number v-model="generatorConfig.duration" :min="1" :max="30" size="small" />
            <label>副本</label>
            <el-input-number v-model="generatorConfig.variants" :min="1" :max="8" size="small" />
          </div>
        </div>
      </div>
    </div>

    <!-- Full-screen Shot Editor -->
    <ShotTableEditor v-if="shotEditorVisible"
                     :visible="shotEditorVisible"
                     :shots="getNodeShots(shotEditorNode)"
                     :node-id="shotEditorNode ? nodeKey(shotEditorNode) : ''"
                     @close="shotEditorVisible = false"
                     @updateShot="(shot, data) => handleShotUpdate(shot, data)"
                     @batchGenerateImages="handleBatchFromEditor"
                     @batchGenerateVideos="handleBatchFromEditor" />

    <!-- Video Timeline -->
    <VideoComposeTimeline v-if="timelineVisible"
                          :visible="timelineVisible"
                          :timeline="canvas.timeline.value || {}"
                          @close="timelineVisible = false"
                          @export="handleExport" />

    <!-- Director Desk -->
    <div v-if="directorDeskVisible" class="director-overlay">
      <div class="director-shell" @mousedown.stop>
        <div class="director-topbar">
          <div>
            <strong>🎥 导演台</strong>
            <span>轻量3D构图 · 多视角机位 · 截图回传画布</span>
          </div>
          <div class="director-top-actions">
            <el-radio-group v-model="directorMode" size="small">
              <el-radio-button label="move">移动 V</el-radio-button>
              <el-radio-button label="rotate">旋转 R</el-radio-button>
              <el-radio-button label="scale">缩放 S</el-radio-button>
            </el-radio-group>
            <el-select v-model="directorAspect" size="small" style="width:92px" @change="persistDirectorDesk">
              <el-option label="9:16" value="9:16" />
              <el-option label="16:9" value="16:9" />
              <el-option label="1:1" value="1:1" />
              <el-option label="4:3" value="4:3" />
            </el-select>
            <el-button size="small" @click="directorGridSnap = !directorGridSnap">吸附 X: {{ directorGridSnap ? '开' : '关' }}</el-button>
            <el-button type="primary" size="small" @click="captureDirectorShot()">截图</el-button>
            <el-button size="small" @click="sendDirectorShotToCanvas()">发送到画布</el-button>
            <el-button size="small" @click="closeDirectorDesk">关闭</el-button>
          </div>
        </div>

        <div class="director-workspace">
          <aside class="director-panel director-left">
            <div class="director-panel-title">添加模型</div>
            <div class="director-add-grid">
              <button @click="addDirectorElement('human')">人体素模</button>
              <button @click="addDirectorElement('geometry')">基础几何</button>
              <button @click="addDirectorElement('crowd')">群众阵列</button>
              <button @click="addDirectorElement('upload')">本地上传</button>
            </div>
            <div class="director-panel-title">元素列表</div>
            <div class="director-element-list">
              <button v-for="item in directorElements" :key="item.id"
                      :class="{ active: directorSelectedElementId === item.id, hidden: item.hidden }"
                      @click="directorSelectedElementId = item.id">
                <span>{{ directorElementIcon(item.type) }}</span>
                <strong>{{ item.name }}</strong>
                <small>{{ item.group || '未分组' }}</small>
              </button>
            </div>
            <div class="director-list-actions">
              <el-button size="small" @click="renameDirectorElement">重命名</el-button>
              <el-button size="small" @click="toggleDirectorElement">{{ selectedDirectorElement?.hidden ? '显示' : '隐藏' }}</el-button>
              <el-button size="small" @click="groupDirectorElement">编组</el-button>
              <el-button size="small" @click="ungroupDirectorElement">解组</el-button>
              <el-button size="small" type="danger" @click="deleteDirectorElement">删除</el-button>
            </div>
          </aside>

          <main class="director-stage-wrap">
            <div class="director-viewbar">
              <button :class="{ active: directorView === 'top' }" @click="directorView = 'top'">顶视 T</button>
              <button :class="{ active: directorView === 'front' }" @click="directorView = 'front'">正视 Y</button>
              <button :class="{ active: directorView === 'director' }" @click="directorView = 'director'">导演视角</button>
              <button :class="{ active: directorView === 'camera' }" @click="directorView = 'camera'">相机视角</button>
              <button @click="resetDirectorView">重置 Q</button>
            </div>
            <div ref="directorStageRef" :class="['director-stage', 'view-' + directorView]">
              <div :class="['director-camera-frame', 'aspect-' + directorAspect.replace(':','-')]">
                <span>Camera {{ directorAspect }} · FOV {{ directorCamera.fov }}°</span>
              </div>
              <button v-for="item in visibleDirectorElements" :key="item.id"
                      :class="['director-object', 'director-object-' + item.type, { selected: directorSelectedElementId === item.id }]"
                      :style="directorObjectStyle(item)"
                      @click="directorSelectedElementId = item.id"
                      @pointerdown.stop.prevent="startDirectorObjectTransform($event, item)">
                <span>{{ directorElementIcon(item.type) }}</span>
                <strong>{{ item.name }}</strong>
              </button>
              <div class="director-ground"></div>
            </div>
            <div class="director-shortcuts">
              <span>V移动</span><span>R旋转</span><span>S缩放</span><span>Shift等比缩放</span>
              <span>X网格吸附</span><span>Delete删除</span><span>Ctrl+G编组</span>
            </div>
          </main>

          <aside class="director-panel director-right">
            <div class="director-panel-title">对象属性</div>
            <div v-if="selectedDirectorElement" class="director-form">
              <label>名称</label>
              <el-input v-model="selectedDirectorElement.name" size="small" @change="persistDirectorDesk" />
              <label>类型</label>
              <el-input :model-value="directorElementTypeLabel(selectedDirectorElement.type)" size="small" disabled />
              <label>位置 X/Y/Z</label>
              <div class="director-number-row">
                <el-input-number v-model="selectedDirectorElement.x" :step="10" size="small" @change="persistDirectorDesk" />
                <el-input-number v-model="selectedDirectorElement.y" :step="10" size="small" @change="persistDirectorDesk" />
                <el-input-number v-model="selectedDirectorElement.z" :step="10" size="small" @change="persistDirectorDesk" />
              </div>
              <label>旋转</label>
              <el-slider v-model="selectedDirectorElement.rotate" :min="-180" :max="180" @change="persistDirectorDesk" />
              <label>缩放</label>
              <el-slider v-model="selectedDirectorElement.scale" :min="40" :max="180" @change="persistDirectorDesk" />
            </div>
            <div class="director-panel-title">相机属性</div>
            <div class="director-form">
              <label>视场 FOV</label>
              <el-slider v-model="directorCamera.fov" :min="18" :max="90" @change="persistDirectorDesk" />
              <label>焦点绑定</label>
              <el-select v-model="directorCamera.focus" size="small" @change="persistDirectorDesk">
                <el-option label="不绑定" value="" />
                <el-option v-for="item in directorElements" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
              <label>全景设置</label>
              <div class="director-panorama-actions">
                <button @click="ElMessage.info('本地上传全景图入口待接入上传组件')">本地上传</button>
                <button @click="ElMessage.info('从生成历史选择全景图，待接入历史接口')">生成历史</button>
                <button @click="ElMessage.info('将场景图转全景，待接入图像生成服务')">场景转全景</button>
              </div>
            </div>
            <div class="director-panel-title">机位截图</div>
            <div class="director-shot-list">
              <div v-for="shot in directorShots" :key="shot.id" class="director-shot-item">
                <button @click="directorSelectedShotId = shot.id">
                  <img v-if="shot.preview_url" :src="shot.preview_url" alt="机位截图" />
                  <strong>{{ shot.name }}</strong>
                  <span>{{ shot.aspect }} · {{ shot.view }}</span>
                </button>
                <el-button size="small" @click="sendDirectorShotToCanvas(null, shot)">发送</el-button>
                <el-button size="small" type="danger" @click="deleteDirectorShot(shot.id)">删除</el-button>
              </div>
              <div v-if="!directorShots.length" class="director-empty">暂无截图，点击顶部“截图”创建当前机位。</div>
            </div>
          </aside>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, VideoPlay } from '@element-plus/icons-vue'
import { canvasApi } from '@/api/canvas'
import { generationApi } from '@/api/generation'
import { useCanvasState } from './canvas/composables/useCanvasState'
import { useCanvasNodes } from './canvas/composables/useCanvasNodes'
import NodeCreateMenu from './canvas/components/NodeCreateMenu.vue'
import NodePropertyPanel from './canvas/components/NodePropertyPanel.vue'
import ShotTableEditor from './canvas/components/ShotTableEditor.vue'
import VideoComposeTimeline from './canvas/components/VideoComposeTimeline.vue'
import CanvasNodeAgentBox from './canvas/components/CanvasNodeAgentBox.vue'

const route = useRoute()
const router = useRouter()
const state = useCanvasState()
const canvas = useCanvasNodes(state.projectId)

const canvasAreaRef = ref(null)
const directorStageRef = ref(null)
const selectedNodeForPanel = ref(null)
const shotEditorVisible = ref(false)
const shotEditorNode = ref(null)
const timelineVisible = ref(false)
const directorDeskVisible = ref(false)
const directorDeskNode = ref(null)
const directorMode = ref('move')
const directorView = ref('director')
const directorAspect = ref('16:9')
const directorGridSnap = ref(true)
const directorSelectedElementId = ref('')
const directorSelectedShotId = ref('')
const directorElements = ref(defaultDirectorElements())
const directorShots = ref([])
const directorCamera = reactive({ fov: 35, focus: '', x: 0, y: 160, z: 520 })
const directorTransformDrag = ref({
  active: false,
  elementId: '',
  mode: 'move',
  startX: 0,
  startY: 0,
  centerX: 0,
  centerY: 0,
  startAngle: 0,
  startItem: null,
  stageRect: null
})

// Node create menu state
const createMenuVisible = ref(false)
const createMenuPos = ref({ x: 0, y: 0 })

// Node drag state
const nodeDrag = ref({ active: false, nodeId: null, offsetX: 0, offsetY: 0 })
const groupDrag = ref({ active: false, groupId: null, startX: 0, startY: 0, nodes: [] })

// Connection drag state
const connectionDrag = ref({ active: false, sourceNode: null, mouseX: 0, mouseY: 0 })
const selectedConnectionId = ref(null)
const pendingConnectionSource = ref(null)
const generatorPrompt = ref('')
const generatorConfig = reactive({
  mode: 'image',
  modelId: 'seedream-5.0',
  aspectRatio: '9:16',
  duration: 5,
  variants: 1
})

// Canvas pan state
const panDrag = ref({ active: false, startX: 0, startY: 0, originX: 0, originY: 0 })

const leftTabs = [
  { key: 'add', icon: 'Plus', label: '添加' },
  { key: 'workflow', icon: 'Link', label: '工作流' },
  { key: 'assets', icon: 'Box', label: '资产' },
  { key: 'history', icon: 'List', label: '历史记录' },
  { key: 'tutorial', icon: 'QuestionFilled', label: '教程' }
]

const nodeTypeGroups = computed(() => {
  const groups = []
  canvas.NODE_TYPES.forEach((item) => {
    const name = item.group || '其他'
    let group = groups.find(g => g.name === name)
    if (!group) {
      group = { name, items: [] }
      groups.push(group)
    }
    group.items.push(item)
  })
  return groups
})

const projectAssets = computed(() =>
  canvas.nodes.value
    .filter(n => ['image', 'video', 'audio', 'script', 'director'].includes(n.type) || readNodeData(n).saved_as_asset)
    .map(n => ({
      asset_id: nodeKey(n),
      asset_type: n.type,
      name: n.name || n.label || nodeLabel(n.type),
      maturity_level: n.status === 'completed' ? 'L2' : 'L0'
    }))
)

const stageStyle = computed(() => ({
  width: state.canvasSize.value.width + 'px',
  height: state.canvasSize.value.height + 'px',
  transform: state.canvasTransform.value
}))

const textNodeAgentStyle = computed(() => {
  const node = selectedNodeForPanel.value
  if (!node || node.type !== 'text') return {}
  const width = 940
  const left = Math.max(24, Math.round((node.x || 0) + nodeW(node) / 2 - width / 2))
  const top = Math.max(24, Math.round((node.y || 0) + nodeH(node) + 28))
  return {
    left: left + 'px',
    top: top + 'px',
    width: width + 'px'
  }
})

// Selected node for property panel
watch(() => state.selectedNodeId.value, (id) => {
  if (id) {
    selectedNodeForPanel.value = findNodeByRef(id)
  } else {
    selectedNodeForPanel.value = null
  }
}, { immediate: true })

watch(selectedNodeForPanel, (node) => {
  if (!node) return
  generatorPrompt.value = getNodePrompt(node)
  generatorConfig.mode = taskTypeForNode(node.type)
  generatorConfig.modelId = defaultModelForTask(generatorConfig.mode)
}, { immediate: true })

// Connection paths for SVG
const connectionPaths = computed(() =>
  canvas.connections.value.map(conn => {
    const sourceId = conn.source_node_id || conn.sourceNodeId || conn.source
    const targetId = conn.target_node_id || conn.targetNodeId || conn.target
    const source = findNodeByRef(sourceId)
    const target = findNodeByRef(targetId)
    if (!source || !target) return null
    const start = nodePortPosition(source, 'out')
    const end = nodePortPosition(target, 'in')
    const sx = start.x
    const sy = start.y
    const ex = end.x
    const ey = end.y
    const mid = Math.max(60, Math.abs(ex - sx) / 2)
    return {
      id: String(conn.uuid || conn.id),
      path: `M ${sx} ${sy} C ${sx + mid} ${sy}, ${ex - mid} ${ey}, ${ex} ${ey}`,
      midX: Math.round((sx + ex) / 2),
      midY: Math.round((sy + ey) / 2)
    }
  }).filter(Boolean)
)

const selectedConnectionForPanel = computed(() =>
  connectionPaths.value.find(c => c.id === selectedConnectionId.value) || null
)

const selectedDirectorElement = computed(() =>
  directorElements.value.find(item => item.id === directorSelectedElementId.value) || null
)

const visibleDirectorElements = computed(() =>
  directorElements.value.filter(item => !item.hidden)
)

const presetGroupFrames = computed(() => {
  const groups = new Map()
  canvas.nodes.value.forEach((node) => {
    const data = readNodeData(node)
    const preset = data.preset_group
    const groupId = preset?.id || node.group_id || node.groupId
    if (!groupId) return
    if (!groups.has(groupId)) {
      groups.set(groupId, {
        id: groupId,
        title: preset?.title || node.group_name || node.groupName || '编组',
        nodes: []
      })
    }
    groups.get(groupId).nodes.push(node)
  })
  return Array.from(groups.values()).filter(group => group.nodes.length > 1).map((group) => {
    const bounds = group.nodes.reduce((acc, node) => {
      const x = Number(node.x || 0)
      const y = Number(node.y || 0)
      const w = nodeW(node)
      const h = nodeH(node)
      return {
        minX: Math.min(acc.minX, x),
        minY: Math.min(acc.minY, y),
        maxX: Math.max(acc.maxX, x + w),
        maxY: Math.max(acc.maxY, y + h)
      }
    }, { minX: Infinity, minY: Infinity, maxX: -Infinity, maxY: -Infinity })
    const padding = 32
    return {
      id: group.id,
      title: group.title,
      x: bounds.minX - padding,
      y: bounds.minY - padding - 18,
      width: bounds.maxX - bounds.minX + padding * 2,
      height: bounds.maxY - bounds.minY + padding * 2 + 18
    }
  })
})

// Temporary connection line shown while dragging a connection
const tempConnectionLine = computed(() => {
  if (!connectionDrag.value.active || !connectionDrag.value.sourceNode) return null
  const node = connectionDrag.value.sourceNode
  const start = nodePortPosition(node, 'out')
  const sx = start.x
  const sy = start.y
  const ex = connectionDrag.value.mouseX
  const ey = connectionDrag.value.mouseY
  const mid = Math.max(60, Math.abs(ex - sx) / 2)
  return `M ${sx} ${sy} C ${sx + mid} ${sy}, ${ex - mid} ${ey}, ${ex} ${ey}`
})

// ===== Lifecycle =====
onMounted(async () => {
  window.addEventListener('keydown', onCanvasKeydown)
  try {
    await ensureProject()
    await canvas.loadNodes()
    await canvas.loadWorkflows()
    state.markSaved()
  } catch (e) {
    ElMessage.warning('后端画布暂不可用，使用本地编辑模式')
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', onCanvasKeydown)
  cleanupDirectorTransformListeners()
  cleanupDocumentListeners()
})

async function ensureProject() {
  try {
    const res = await canvasApi.getProject(state.projectId.value)
    if (res.data) {
      state.projectName.value = res.data.name || state.projectName.value
      state.projectStatus.value = res.data.status || state.projectStatus.value
    }
  } catch (e) {
    try {
      const created = await canvasApi.createProject({
        name: '漫剧自由画布项目',
        status: 'editing',
        style_config: { aspect_ratio: '9:16', fps: 25, resolution: '1080x1920' }
      })
      if (created.data) {
        state.projectId.value = created.data.uuid || String(created.data.id)
        state.projectName.value = created.data.name || '漫剧自由画布项目'
        state.projectStatus.value = created.data.status || 'editing'
      }
    } catch {
      state.projectId.value = state.projectId.value || 'local_canvas'
      state.projectName.value = '本地漫剧自由画布'
      state.projectStatus.value = 'editing'
      canvas.localMode.value = true
    }
  }
}

async function renameCanvas() {
  try {
    const { value } = await ElMessageBox.prompt('请输入新的画布名称', '重命名画布', {
      inputValue: state.projectName.value,
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    })
    if (value) {
      state.projectName.value = value
      state.markSaved()
    }
  } catch { /* cancelled */ }
}

function resetLocalCanvas() {
  canvas.nodes.value = []
  canvas.connections.value = []
  localStorage.removeItem(`aicp:canvas:${state.projectId.value}`)
  state.deselectAll()
  selectedNodeForPanel.value = null
  state.markSaved()
  ElMessage.success('已新建空白画布')
}

// ===== Node CRUD =====
async function handleAddNode(type, x, y) {
  const count = canvas.nodes.value.length
  const nx = Number.isFinite(x) ? Math.max(0, Math.round(x)) : 120 + (count % 3) * 210
  const ny = Number.isFinite(y) ? Math.max(0, Math.round(y)) : 80 + Math.floor(count / 3) * 150
  try {
    const node = await canvas.addNode(type, nx, ny, defaultNodeData(type))
    if (node) {
      state.selectNode(nodeKey(node))
      state.markSaved()
      return node
    }
  } catch (e) {
    ElMessage.error('创建节点失败: ' + (e.message || '未知错误'))
  }
}

async function handleNodeUpdate(node, updates) {
  try {
    await canvas.updateNode(nodeKey(node), updates)
    state.markSaved()
  } catch (e) { ElMessage.error('更新节点失败') }
}

async function handleTextAgentApplied(updatedNode) {
  const id = nodeKey(updatedNode)
  const local = findNodeByRef(id)
  if (!local) {
    canvas.loadNodes()
    return
  }
  Object.assign(local, updatedNode)
  const raw = updatedNode.input_data ?? updatedNode.inputData ?? updatedNode.data
  if (raw) {
    local.input_data = typeof raw === 'string' ? raw : JSON.stringify(raw)
    local.inputData = local.input_data
    local.data = readNodeData(local)
    if (canvas.localMode.value) {
      await canvas.updateNode(id, { data: local.data, status: 'ready', width: 520, height: 300 }).catch(() => {})
    }
  }
  selectedNodeForPanel.value = local
  generatorPrompt.value = getNodePrompt(local)
  state.markSaved()
}

async function handleDeleteNode(node) {
  try {
    await ElMessageBox.confirm('确定删除此节点？', '确认删除')
    await canvas.deleteNode(nodeKey(node))
    selectedNodeForPanel.value = null
    state.deselectAll()
    state.markSaved()
  } catch (e) { /* cancelled */ }
}

async function handleDuplicateNode(node) {
  try {
    const duplicated = await canvas.duplicateNode(nodeKey(node))
    if (duplicated) {
      state.selectNode(nodeKey(duplicated))
      ElMessage.success('已创建副本，并保留原有关联连线')
      state.markSaved()
    }
  } catch (e) {
    ElMessage.error('创建副本失败')
  }
}

function contextNode() {
  return findNodeByRef(state.contextMenu.value.nodeId)
}

async function saveSelectedAsAsset() {
  const node = contextNode() || selectedNodeForPanel.value
  if (!node) return
  await canvas.updateNode(nodeKey(node), {
    data: { ...readNodeData(node), saved_as_asset: true, asset_name: node.name || nodeLabel(node.type) }
  }).catch(() => {})
  state.closeContextMenu()
  ElMessage.success('已保存为资产')
}

async function copySelectedNode(keepConnections) {
  const node = contextNode() || selectedNodeForPanel.value
  if (!node) return
  const copy = await canvas.duplicateNode(nodeKey(node))
  if (copy && keepConnections) {
    const related = canvas.connections.value.filter((conn) => {
      const s = conn.source_node_id || conn.sourceNodeId || conn.source
      const t = conn.target_node_id || conn.targetNodeId || conn.target
      return findNodeByRef(s) === node || findNodeByRef(t) === node
    })
    for (const conn of related) {
      const s = conn.source_node_id || conn.sourceNodeId || conn.source
      const t = conn.target_node_id || conn.targetNodeId || conn.target
      const source = findNodeByRef(s) === node ? copy : findNodeByRef(s)
      const target = findNodeByRef(t) === node ? copy : findNodeByRef(t)
      if (source && target && nodeKey(source) !== nodeKey(target)) {
        await canvas.connectNodes(nodeKey(source), nodeKey(target)).catch(() => {})
      }
    }
  }
  state.closeContextMenu()
  if (copy) state.selectNode(nodeKey(copy))
  ElMessage.success(keepConnections ? '已复用节点并保留连线' : '已复制节点')
}

async function deleteContextNode() {
  const node = contextNode() || selectedNodeForPanel.value
  if (!node) return
  await handleDeleteNode(node)
  state.closeContextMenu()
}

// ===== Node Actions =====
async function handleNodeAction(node, action) {
  if (action.creates) {
    const created = await createDownstreamNode(node, action.creates)
    if (created) ElMessage.success(`${action.label}节点已创建`)
    return
  }
  await runNodeTask(node, action)
}

async function handleBatchGenerate(node, mode) {
  try {
    const taskType = mode === 'video' ? 'video' : 'image'
    const confirmed = await confirmTaskCost(taskType, {
      node_id: nodeKey(node),
      prompt: getNodePrompt(node),
      batch: node.type === 'script'
    }, mode === 'image' ? '批量生成图片' : '批量生成视频')
    if (!confirmed) return

    if (canvas.localMode.value) {
      await createGeneratorGroupFromScript(node, mode)
      const data = readNodeData(node)
      const shots = Array.isArray(data.shots) ? data.shots.map((shot) => ({
        ...shot,
        [mode === 'image' ? 'image_status' : 'video_status']: 'completed'
      })) : []
      await canvas.updateNode(nodeKey(node), {
        status: 'completed',
        data: {
          ...data,
          shots,
          [`last_${mode}_task`]: {
            task_id: `local_${mode}_${Date.now()}`,
            status: 'completed',
            prompt: getNodePrompt(node)
          }
        }
      })
      ElMessage.success(`本地模式已完成${mode === 'image' ? '批量生图' : '批量生视频'}模拟`)
      state.markSaved()
      return
    }

    let res
    if (node.type === 'script') {
      res = mode === 'image'
        ? await canvasApi.batchGenerateImages(state.projectId.value, nodeKey(node), {
            node_id: nodeKey(node),
            prompt: getNodePrompt(node)
          })
        : await canvasApi.batchGenerateVideos(state.projectId.value, nodeKey(node), {
            node_id: nodeKey(node),
            prompt: getNodePrompt(node)
          })
    } else {
      res = await canvasApi.runSlashCommand(state.projectId.value, `generate-${mode}`, {
        node_id: nodeKey(node),
        prompt: getNodePrompt(node),
        model_id: mode === 'image' ? 'seedream-5.0' : 'seedance-2.0'
      })
    }
    await canvas.updateNode(nodeKey(node), { status: 'processing' }).catch(() => {})
    ElMessage.success(`已创建${mode === 'image' ? '生图' : '生视频'}任务: ${res.data?.uuid || res.data?.task_id || ''}`)
    state.markSaved()
  } catch (e) { ElMessage.error('批量生成失败') }
}

async function synthesizeScriptPrompts(node) {
  const data = readNodeData(node)
  const style = data.style_prompt || '统一漫剧视觉风格'
  data.shots = (data.shots || []).map((shot) => {
    const base = [
      shot.scene_no && `场次:${shot.scene_no}`,
      shot.shot_size && `景别:${shot.shot_size}`,
      shot.characters && `角色:${shot.characters}`,
      shot.props && `道具:${shot.props}`,
      shot.action && `动作:${shot.action}`,
      shot.visual_description,
      style
    ].filter(Boolean).join('，')
    return {
      ...shot,
      image_prompt: shot.image_prompt || base,
      video_prompt: shot.video_prompt || `${base}，运镜:${shot.camera_motion || '固定'}，时长:${shot.duration || 3000}ms`
    }
  })
  data.script_steps = {
    shot_confirmed: true,
    assets_ready: true,
    prompts_ready: true
  }
  await canvas.updateNode(nodeKey(node), { data })
  ElMessage.success('已合成最终提示词')
}

async function createGeneratorGroupFromScript(scriptNode, mode) {
  const data = readNodeData(scriptNode)
  const shots = data.shots || []
  const created = []
  const startX = (scriptNode.x || 0) + nodeW(scriptNode) + 120
  const startY = scriptNode.y || 80
  const targetType = mode === 'video' ? 'video' : 'image'
  for (let i = 0; i < shots.length; i += 1) {
    const shot = shots[i]
    const node = await canvas.addNode(targetType, startX, startY + i * 210, {
      prompt: mode === 'video' ? shot.video_prompt : shot.image_prompt,
      shot_no: shot.shot_no,
      source_script_node_id: nodeKey(scriptNode),
      generator_params: {
        model_id: mode === 'video' ? 'seedance-2.0' : 'seedream-5.0',
        aspect_ratio: '9:16',
        resolution: '1080x1920',
        duration: shot.duration || 3000
      }
    })
    if (node) {
      created.push(node)
      await canvas.connectNodes(nodeKey(scriptNode), nodeKey(node)).catch(() => {})
    }
  }
  if (created.length) {
    await canvas.groupNodes(created.map(nodeKey), `${mode === 'video' ? '分镜视频' : '分镜图'}生成器组`).catch(() => {})
  }
}

async function handleBatchFromEditor(shotIds, config) {
  if (!shotEditorNode.value) return
  try {
    if (canvas.localMode.value) {
      const data = readNodeData(shotEditorNode.value)
      const mode = config.mode === 'video' ? 'video' : 'image'
      data.shots = (data.shots || []).map((shot) => {
        const sid = shot.id || shot.uuid || shot.shot_no
        if (!shotIds.includes(sid)) return shot
        return {
          ...shot,
          [mode === 'image' ? 'image_status' : 'video_status']: 'completed',
          [`${mode}_task_id`]: `local_${mode}_${Date.now()}`
        }
      })
      await canvas.updateNode(nodeKey(shotEditorNode.value), { data, status: 'completed' })
      ElMessage.success(`本地模式已完成 ${shotIds.length} 个${mode === 'image' ? '生图' : '生视频'}任务`)
      shotEditorVisible.value = false
      return
    }
    const api = config.mode === 'video' ? canvasApi.batchGenerateVideos : canvasApi.batchGenerateImages
    await api(state.projectId.value, nodeKey(shotEditorNode.value), {
      shot_ids: shotIds,
      config
    })
    ElMessage.success(`已创建 ${shotIds.length} 个生成任务`)
    await canvas.updateNode(nodeKey(shotEditorNode.value), { status: 'processing' }).catch(() => {})
    shotEditorVisible.value = false
  } catch (e) {
    ElMessage.error('批量生成任务创建失败')
  }
}

// ===== Director Desk =====
function defaultDirectorElements() {
  return [
    { id: 'human_1', type: 'human', name: '角色素模', group: '角色', x: 42, y: 46, z: 0, rotate: 0, scale: 100, hidden: false },
    { id: 'geometry_1', type: 'geometry', name: '场景方块', group: '场景', x: 58, y: 58, z: 0, rotate: 0, scale: 100, hidden: false },
    { id: 'crowd_1', type: 'crowd', name: '群众阵列', group: '群演', x: 28, y: 68, z: 0, rotate: 0, scale: 86, hidden: false }
  ]
}

function cloneDirectorElements(items) {
  const source = Array.isArray(items) && items.length ? items : defaultDirectorElements()
  return source.map(item => ({ ...item }))
}

function getDirectorData(node) {
  const data = readNodeData(node)
  const director = data.director || {}
  return {
    elements: cloneDirectorElements(director.elements),
    shots: Array.isArray(director.shots) ? director.shots : [],
    camera: { fov: 35, focus: '', x: 0, y: 160, z: 520, ...(director.camera || {}) },
    aspect: director.aspect || data.aspect_ratio || '16:9'
  }
}

function loadDirectorDeskFromNode(node) {
  if (!node) return
  const data = getDirectorData(node)
  directorDeskNode.value = node
  directorElements.value = cloneDirectorElements(data.elements)
  directorShots.value = data.shots.map(shot => ({ ...shot }))
  directorAspect.value = data.aspect
  Object.assign(directorCamera, data.camera)
  directorSelectedElementId.value = directorElements.value[0]?.id || ''
  directorSelectedShotId.value = directorShots.value.at(-1)?.id || ''
}

function openDirectorDesk(node) {
  loadDirectorDeskFromNode(node)
  directorDeskVisible.value = true
}

async function closeDirectorDesk() {
  const saved = await persistDirectorDesk()
  if (saved !== false) directorDeskVisible.value = false
}

async function persistDirectorDesk() {
  if (!directorDeskNode.value) return true
  const data = readNodeData(directorDeskNode.value)
  try {
    await canvas.updateNode(nodeKey(directorDeskNode.value), {
      status: 'ready',
      data: {
        ...data,
        prompt: data.prompt || '导演台构图、机位截图和参考图管理',
        director: {
          elements: directorElements.value.map(item => ({ ...item })),
          shots: directorShots.value.map(shot => ({ ...shot })),
          camera: { ...directorCamera },
          aspect: directorAspect.value
        },
        aspect_ratio: directorAspect.value
      }
    })
    state.markSaved()
    return true
  } catch (e) {
    ElMessage.error('导演台保存失败，请检查网络或后端服务')
    return false
  }
}

function directorElementIcon(type) {
  return { human: '人', geometry: '几何', crowd: '群', upload: '本地' }[type] || '物'
}

function directorElementTypeLabel(type) {
  return { human: '人体素模', geometry: '基础几何体', crowd: '群众阵列', upload: '本地上传模型/图片' }[type] || '元素'
}

function addDirectorElement(type) {
  const labels = { human: '人体素模', geometry: '基础几何', crowd: '群众阵列', upload: '本地上传' }
  const index = directorElements.value.filter(item => item.type === type).length + 1
  const item = {
    id: `${type}_${Date.now()}`,
    type,
    name: `${labels[type] || '元素'} ${index}`,
    group: type === 'human' ? '角色' : type === 'crowd' ? '群演' : '场景',
    x: Math.min(82, 24 + index * 12),
    y: Math.min(82, 32 + index * 9),
    z: 0,
    rotate: 0,
    scale: type === 'crowd' ? 82 : 100,
    hidden: false
  }
  directorElements.value.push(item)
  directorSelectedElementId.value = item.id
  persistDirectorDesk()
}

async function renameDirectorElement() {
  const item = selectedDirectorElement.value
  if (!item) return
  try {
    const { value } = await ElMessageBox.prompt('请输入元素名称', '重命名元素', {
      inputValue: item.name,
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    })
    if (value) {
      item.name = value
      await persistDirectorDesk()
    }
  } catch { /* cancelled */ }
}

function toggleDirectorElement() {
  const item = selectedDirectorElement.value
  if (!item) return
  item.hidden = !item.hidden
  persistDirectorDesk()
}

function groupDirectorElement() {
  const item = selectedDirectorElement.value
  if (!item) return
  item.group = '编组A'
  persistDirectorDesk()
  ElMessage.success('已加入编组A')
}

function ungroupDirectorElement() {
  const item = selectedDirectorElement.value
  if (!item) return
  item.group = ''
  persistDirectorDesk()
}

function deleteDirectorElement() {
  const item = selectedDirectorElement.value
  if (!item) return
  directorElements.value = directorElements.value.filter(el => el.id !== item.id)
  directorSelectedElementId.value = directorElements.value[0]?.id || ''
  persistDirectorDesk()
}

function resetDirectorView() {
  directorView.value = 'director'
  directorMode.value = 'move'
}

function directorObjectStyle(item) {
  const scale = Number(item.scale || 100) / 100
  const x = Math.max(8, Math.min(88, Number(item.x || 50)))
  const y = Math.max(10, Math.min(84, Number(item.y || 50)))
  return {
    left: `${x}%`,
    top: `${y}%`,
    transform: `translate(-50%, -50%) rotate(${item.rotate || 0}deg) scale(${scale})`
  }
}

function clampDirectorValue(value, min, max) {
  return Math.max(min, Math.min(max, value))
}

function snapDirectorValue(value, step = 2) {
  if (!directorGridSnap.value) return value
  return Math.round(value / step) * step
}

function angleFromCenter(clientX, clientY, centerX, centerY) {
  return Math.atan2(clientY - centerY, clientX - centerX) * 180 / Math.PI
}

function startDirectorObjectTransform(e, item) {
  const stage = directorStageRef.value
  if (!stage) return
  const stageRect = stage.getBoundingClientRect()
  const objectRect = e.currentTarget.getBoundingClientRect()
  const centerX = objectRect.left + objectRect.width / 2
  const centerY = objectRect.top + objectRect.height / 2
  directorSelectedElementId.value = item.id
  directorTransformDrag.value = {
    active: true,
    elementId: item.id,
    mode: directorMode.value,
    startX: e.clientX,
    startY: e.clientY,
    centerX,
    centerY,
    startAngle: angleFromCenter(e.clientX, e.clientY, centerX, centerY),
    startItem: { ...item },
    stageRect: {
      left: stageRect.left,
      top: stageRect.top,
      width: stageRect.width,
      height: stageRect.height
    }
  }
  window.addEventListener('pointermove', onDirectorObjectPointerMove)
  window.addEventListener('pointerup', onDirectorObjectPointerUp)
}

function onDirectorObjectPointerMove(e) {
  const drag = directorTransformDrag.value
  if (!drag.active || !drag.startItem || !drag.stageRect) return
  const item = directorElements.value.find(el => el.id === drag.elementId)
  if (!item) return

  if (drag.mode === 'move') {
    const x = ((e.clientX - drag.stageRect.left) / drag.stageRect.width) * 100
    const y = ((e.clientY - drag.stageRect.top) / drag.stageRect.height) * 100
    item.x = clampDirectorValue(snapDirectorValue(Math.round(x)), 8, 88)
    item.y = clampDirectorValue(snapDirectorValue(Math.round(y)), 10, 84)
    return
  }

  if (drag.mode === 'rotate') {
    const nextAngle = angleFromCenter(e.clientX, e.clientY, drag.centerX, drag.centerY)
    item.rotate = Math.round((drag.startItem.rotate || 0) + nextAngle - drag.startAngle)
    return
  }

  if (drag.mode === 'scale') {
    const delta = (e.clientX - drag.startX) * 0.35 - (e.clientY - drag.startY) * 0.55
    item.scale = clampDirectorValue(Math.round((drag.startItem.scale || 100) + delta), 40, 180)
  }
}

async function onDirectorObjectPointerUp() {
  if (directorTransformDrag.value.active) {
    directorTransformDrag.value = {
      active: false,
      elementId: '',
      mode: 'move',
      startX: 0,
      startY: 0,
      centerX: 0,
      centerY: 0,
      startAngle: 0,
      startItem: null,
      stageRect: null
    }
    await persistDirectorDesk()
  }
  cleanupDirectorTransformListeners()
}

function cleanupDirectorTransformListeners() {
  window.removeEventListener('pointermove', onDirectorObjectPointerMove)
  window.removeEventListener('pointerup', onDirectorObjectPointerUp)
}

function directorShotDimensions(aspect) {
  if (aspect === '9:16') return { width: 360, height: 640 }
  if (aspect === '1:1') return { width: 520, height: 520 }
  if (aspect === '4:3') return { width: 560, height: 420 }
  return { width: 640, height: 360 }
}

function escapeSvgText(text) {
  return String(text || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function directorElementFill(type) {
  return { human: '#4f46e5', geometry: '#059669', crowd: '#334155', upload: '#c2410c' }[type] || '#475569'
}

function buildDirectorShotPreview(shot) {
  const { width, height } = directorShotDimensions(shot.aspect)
  const elements = Array.isArray(shot.elements) ? shot.elements : []
  const objectSvg = elements.map((item) => {
    const x = width * clampDirectorValue(Number(item.x || 50), 4, 96) / 100
    const y = height * clampDirectorValue(Number(item.y || 50), 4, 96) / 100
    const scale = clampDirectorValue(Number(item.scale || 100), 40, 180) / 100
    const baseW = item.type === 'crowd' ? 92 : item.type === 'human' ? 42 : 66
    const baseH = item.type === 'human' ? 98 : 54
    const fill = directorElementFill(item.type)
    return `
      <g transform="translate(${x} ${y}) rotate(${Number(item.rotate || 0)}) scale(${scale})">
        <rect x="${-baseW / 2}" y="${-baseH / 2}" width="${baseW}" height="${baseH}" rx="10" fill="${fill}" stroke="#cbd5e1" stroke-width="2"/>
        <text x="0" y="5" text-anchor="middle" font-size="14" fill="#f8fafc" font-family="Arial, sans-serif">${escapeSvgText(directorElementIcon(item.type))}</text>
      </g>`
  }).join('')
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
    <defs>
      <linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#0f172a"/>
        <stop offset="62%" stop-color="#111827"/>
        <stop offset="100%" stop-color="#172033"/>
      </linearGradient>
      <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
        <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#334155" stroke-width="1" opacity=".55"/>
      </pattern>
    </defs>
    <rect width="100%" height="100%" fill="url(#bg)"/>
    <circle cx="${width * 0.5}" cy="${height * 0.22}" r="${Math.min(width, height) * 0.28}" fill="#818cf8" opacity=".14"/>
    <rect x="0" y="${height * 0.48}" width="${width}" height="${height * 0.52}" fill="url(#grid)" opacity=".62"/>
    ${objectSvg}
    <rect x="12" y="12" width="${width - 24}" height="${height - 24}" fill="none" stroke="#f59e0b" stroke-width="3"/>
    <text x="22" y="36" font-size="16" fill="#fbbf24" font-family="Arial, sans-serif">${escapeSvgText(shot.name)} · ${escapeSvgText(shot.aspect)} · FOV ${escapeSvgText(shot.fov)}</text>
  </svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

async function captureDirectorShot(node = null, options = {}) {
  if (node) loadDirectorDeskFromNode(node)
  if (!directorDeskNode.value) return null
  const shot = {
    id: `shot_${Date.now()}`,
    name: `机位 ${String(directorShots.value.length + 1).padStart(2, '0')}`,
    aspect: directorAspect.value,
    view: directorView.value === 'camera' ? '相机视角' : directorView.value === 'top' ? '顶视图' : directorView.value === 'front' ? '正视图' : '导演视角',
    fov: directorCamera.fov,
    focus: directorCamera.focus,
    created_at: new Date().toLocaleString('zh-CN', { hour12: false }),
    elements: directorElements.value.filter(item => !item.hidden).map(item => ({
      id: item.id,
      name: item.name,
      type: item.type,
      x: item.x,
      y: item.y,
      z: item.z,
      rotate: item.rotate,
      scale: item.scale
    }))
  }
  shot.preview_url = buildDirectorShotPreview(shot)
  directorShots.value.push(shot)
  directorSelectedShotId.value = shot.id
  const saved = await persistDirectorDesk()
  if (saved === false) return null
  if (!options.silent) ElMessage.success('已创建当前机位截图')
  return shot
}

async function sendDirectorShotToCanvas(node = null, shot = null) {
  if (node) loadDirectorDeskFromNode(node)
  const source = directorDeskNode.value
  if (!source) return
  let targetShot = shot || directorShots.value.find(item => item.id === directorSelectedShotId.value) || directorShots.value.at(-1)
  if (!targetShot) targetShot = await captureDirectorShot(source, { silent: true })
  if (!targetShot) return
  const created = await canvas.addNode('image', (source.x || 0) + nodeW(source.type) + 120, source.y || 80, {
    prompt: `${targetShot.name}：${targetShot.view}，比例${targetShot.aspect}，FOV ${targetShot.fov}，作为漫剧画面构图/角色站位参考`,
    source: 'director',
    director_node_id: nodeKey(source),
    director_shot_id: targetShot.id,
    aspect_ratio: targetShot.aspect,
    reference_elements: targetShot.elements,
    preview_url: targetShot.preview_url || buildDirectorShotPreview(targetShot)
  })
  if (created) {
    await canvas.connectNodes(nodeKey(source), nodeKey(created)).catch(() => {})
    state.selectNode(nodeKey(created))
    state.markSaved()
    ElMessage.success('导演台截图已发送到画布图片节点')
  }
}

async function deleteDirectorShot(shotId) {
  directorShots.value = directorShots.value.filter(shot => shot.id !== shotId)
  directorSelectedShotId.value = directorShots.value.at(-1)?.id || ''
  await persistDirectorDesk()
}

// ===== Shot Operations =====
function openShotEditor(node) {
  shotEditorNode.value = node
  shotEditorVisible.value = true
}

async function handleShotUpdate(shot, data) {
  try {
    if (canvas.localMode.value && shotEditorNode.value) {
      const nodeData = readNodeData(shotEditorNode.value)
      nodeData.shots = (nodeData.shots || []).map((item) => {
        const same = (item.id || item.uuid || item.shot_no) === (shot.id || shot.uuid || shot.shot_no)
        return same ? { ...item, ...data } : item
      })
      await canvas.updateNode(nodeKey(shotEditorNode.value), { data: nodeData })
      ElMessage.success('分镜已保存到本地画布')
      return
    }
    await canvasApi.updateShot(state.projectId.value, shot.id || shot.shot_id || shot.uuid, data)
    ElMessage.success('分镜已更新')
  } catch (e) { ElMessage.warning('分镜更新失败') }
}

function getNodeShots(node) {
  if (!node) return []
  try {
    const raw = node.input_data ?? node.inputData ?? node.data ?? {}
    const data = typeof raw === 'string' ? JSON.parse(raw || '{}') : raw
    return data.shots || []
  } catch { return [] }
}

function getNodePrompt(node) {
  if (!node) return ''
  try {
    const data = readNodeData(node)
    return data.prompt || data.content || ''
  } catch { return '' }
}

function getTextNodeMode(node) {
  return readNodeData(node).text_mode || 'choice'
}

function setTextNodeDraft(node, value) {
  const data = readNodeData(node)
  data.prompt = value
  data.content = value
  node.input_data = JSON.stringify(data)
  node.inputData = JSON.stringify(data)
  node.data = data
}

async function persistTextNodeDraft(node, value) {
  const data = readNodeData(node)
  data.prompt = value
  data.content = value
  await canvas.updateNode(nodeKey(node), { data, status: 'ready' }).catch(() => {
    ElMessage.error('文本内容保存失败')
  })
  state.markSaved()
}

function getNodePreviewUrl(node) {
  const data = readNodeData(node)
  return data.preview_url || data.image_url || data.url || ''
}

function readNodeData(node) {
  if (!node) return {}
  try {
    const raw = node.input_data ?? node.inputData ?? node.data ?? {}
    return typeof raw === 'string' ? JSON.parse(raw || '{}') : raw
  } catch {
    return {}
  }
}

function getDialogueText(shot) {
  if (!shot) return '—'
  if (typeof shot.dialogue === 'string') {
    try { const d = JSON.parse(shot.dialogue); return d.text || d.character + ': ' + d.text || '—' }
    catch { return shot.dialogue }
  }
  if (shot.dialogue?.text) return shot.dialogue.text
  return shot.dialogue_text || '—'
}

// ===== Canvas Interactions =====
function onCanvasDoubleClick(e) {
  // Don't open create menu if we're finishing a drag
  if (nodeDrag.value.active || connectionDrag.value.active) return
  const rect = canvasAreaRef.value?.getBoundingClientRect()
  if (!rect) return
  createMenuPos.value = { x: e.clientX, y: e.clientY }
  createMenuVisible.value = true
}

function selectNode(node) {
  selectedConnectionId.value = null
  state.selectNode(nodeKey(node))
}

function selectConnection(connId) {
  selectedConnectionId.value = String(connId)
  state.deselectAll()
}

function deselectCanvas() {
  selectedConnectionId.value = null
  state.deselectAll()
  state.closeContextMenu()
}

async function deleteSelectedConnection() {
  if (!selectedConnectionId.value) return
  try {
    await canvas.deleteConnection(selectedConnectionId.value)
    selectedConnectionId.value = null
    state.markSaved()
    ElMessage.success('连线已删除')
  } catch (e) {
    ElMessage.error('删除连线失败')
  }
}

function onCanvasKeydown(e) {
  const tag = document.activeElement?.tagName?.toLowerCase()
  if (['input', 'textarea', 'select'].includes(tag)) return
  if (directorDeskVisible.value) {
    const key = e.key.toLowerCase()
    if (key === 'v') directorMode.value = 'move'
    if (key === 'r') directorMode.value = 'rotate'
    if (key === 's') directorMode.value = 'scale'
    if (key === 'x') directorGridSnap.value = !directorGridSnap.value
    if (key === 't') directorView.value = 'top'
    if (key === 'y') directorView.value = 'front'
    if (key === 'q') resetDirectorView()
    if (e.ctrlKey && key === 'g' && e.shiftKey) {
      e.preventDefault()
      ungroupDirectorElement()
    } else if (e.ctrlKey && key === 'g') {
      e.preventDefault()
      groupDirectorElement()
    }
    if (e.key === 'Delete' || e.key === 'Backspace') {
      e.preventDefault()
      deleteDirectorElement()
    }
    return
  }
  if ((e.key === 'Delete' || e.key === 'Backspace') && selectedConnectionId.value) {
    e.preventDefault()
    deleteSelectedConnection()
  }
}

function onNodeTypeSelect(type) {
  createMenuVisible.value = false
  const point = screenToCanvas(createMenuPos.value.x, createMenuPos.value.y)
  handleAddNode(type, point.x, point.y).then(async (node) => {
    if (node && pendingConnectionSource.value) {
      await canvas.connectNodes(nodeKey(pendingConnectionSource.value), nodeKey(node)).catch(() => {})
      pendingConnectionSource.value = null
      state.markSaved()
    }
  })
}

function showCreateMenuAtCenter() {
  const rect = canvasAreaRef.value?.getBoundingClientRect()
  createMenuPos.value = rect
    ? { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 }
    : { x: 300, y: 200 }
  createMenuVisible.value = true
}

function onCanvasDrop(e) {
  e.preventDefault()
  try {
    const data = JSON.parse(e.dataTransfer.getData('application/json'))
    if (data?.type === 'asset') {
      const point = screenToCanvas(e.clientX, e.clientY)
      const nodeType = data.assetType === 'video' ? 'video' : data.assetType === 'audio' ? 'audio' : 'image'
      handleAddNode(nodeType, point.x, point.y).then(n => {
        if (n) { canvas.updateNode(nodeKey(n), { data: { asset_id: data.assetId } }).catch(() => {}) }
      })
      ElMessage.success('素材已添加到画布')
    }
  } catch { /* not a drag-drop event */ }
}

// ===== Node Drag (move nodes) =====
function onCanvasMouseDown(e) {
  if (e.button !== 0) return
  if (e.target.closest('.canvas-node') ||
      e.target.closest('.el-button') ||
      e.target.closest('.node-create-menu') ||
      e.target.closest('.connection-hit') ||
      e.target.closest('.connection-delete')) return
  selectedConnectionId.value = null
  panDrag.value = {
    active: true,
    startX: e.clientX,
    startY: e.clientY,
    originX: state.panOffset.value.x,
    originY: state.panOffset.value.y
  }
}

function onNodeMouseDown(e, node) {
  // Don't start node drag if clicking on buttons, ports, or interactive elements
  if (e.target.closest('button, input, textarea, select, .el-button, .node-out-port, .node-in-port')) return
  // Only left-click initiates drag
  if (e.button !== 0) return

  const nodeId = nodeKey(node)
  const point = screenToCanvas(e.clientX, e.clientY)
  const group = groupFrameForNode(node)
  const groupId = group?.id || groupKeyForNode(node)
  const groupNodes = group?.nodes || (groupId ? groupedNodes(groupId) : [])
  if (groupNodes.length > 1 && !e.altKey) {
    startGroupDrag(groupId, point)
    selectedConnectionId.value = null
    state.selectNode(nodeId)
    document.addEventListener('mousemove', onDocumentMouseMove)
    document.addEventListener('mouseup', onDocumentMouseUp)
    e.preventDefault()
    return
  }

  nodeDrag.value = {
    active: true,
    nodeId,
    offsetX: point.x - (node.x || 0),
    offsetY: point.y - (node.y || 0),
    startX: node.x || 0,
    startY: node.y || 0
  }
  // Also select the node
  selectedConnectionId.value = null
  state.selectNode(nodeId)
  // Bind document-level listeners so drag continues outside canvas
  document.addEventListener('mousemove', onDocumentMouseMove)
  document.addEventListener('mouseup', onDocumentMouseUp)
  e.preventDefault()
}

function onPresetGroupMouseDown(e, group) {
  if (e.button !== 0) return
  if (e.target.closest('.canvas-node') || e.target.closest('.node-out-port') || e.target.closest('.node-in-port')) return
  const point = screenToCanvas(e.clientX, e.clientY)
  startGroupDrag(group.id, point)
  selectedConnectionId.value = null
  state.deselectAll()
  document.addEventListener('mousemove', onDocumentMouseMove)
  document.addEventListener('mouseup', onDocumentMouseUp)
  e.preventDefault()
}

function groupKeyForNode(node) {
  if (!node) return ''
  const preset = readNodeData(node).preset_group
  return preset?.id || node.group_id || node.groupId || ''
}

function groupFrameForNode(node) {
  const id = nodeKey(node)
  if (!id) return null
  return presetGroupFrames.value.find(group => group.nodes.some(item => nodeKey(item) === id)) || null
}

function groupedNodes(groupId) {
  if (!groupId) return []
  return canvas.nodes.value.filter(node => String(groupKeyForNode(node)) === String(groupId))
}

function startGroupDrag(groupId, point) {
  const nodes = groupFrameForGroup(groupId)?.nodes || groupedNodes(groupId)
  groupDrag.value = {
    active: nodes.length > 1,
    groupId,
    startX: point.x,
    startY: point.y,
    nodes: nodes.map(node => ({
      id: nodeKey(node),
      startX: node.x || 0,
      startY: node.y || 0
    }))
  }
}

function groupFrameForGroup(groupId) {
  if (!groupId) return null
  return presetGroupFrames.value.find(group => String(group.id) === String(groupId)) || null
}

// ===== Connection Drag (connect nodes) =====
function startConnectionDrag(node) {
  selectedConnectionId.value = null
  state.deselectAll()
  const start = nodePortPosition(node, 'out')
  connectionDrag.value = {
    active: true,
    sourceNode: node,
    mouseX: start.x,
    mouseY: start.y
  }
  // Bind document-level listeners so drag continues outside canvas
  document.addEventListener('mousemove', onDocumentMouseMove)
  document.addEventListener('mouseup', onDocumentMouseUp)
}

function completeConnectionDrag(targetNode) {
  if (!connectionDrag.value.active || !connectionDrag.value.sourceNode) return

  const sourceNode = connectionDrag.value.sourceNode
  const sourceId = nodeKey(sourceNode)
  const targetId = nodeKey(targetNode)

  // Don't connect a node to itself
  if (sourceId === targetId) {
    cleanupDragState()
    return
  }

  // Check if connection already exists (compare by ID, not reference)
  const alreadyConnected = canvas.connections.value.some(c => {
    const sId = c.source_node_id || c.sourceNodeId || c.source
    const tId = c.target_node_id || c.targetNodeId || c.target
    const sNode = findNodeByRef(sId)
    const tNode = findNodeByRef(tId)
    if (!sNode || !tNode) return false
    return nodeKey(sNode) === sourceId && nodeKey(tNode) === targetId
  })

  if (alreadyConnected) {
    ElMessage.warning('这两个节点已经连接')
    cleanupDragState()
    return
  }

  // Create the connection via API
  canvas.connectNodes(sourceId, targetId).then(() => {
    ElMessage.success('节点已连接')
    state.markSaved()
  }).catch(e => {
    ElMessage.error('连线失败: ' + (e?.message || e || '未知错误'))
  })

  // Reset drag state
  cleanupDragState()
}

function isConnectableTarget(node) {
  if (!connectionDrag.value.active || !connectionDrag.value.sourceNode) return false
  return nodeKey(connectionDrag.value.sourceNode) !== nodeKey(node)
}

// Canvas-level mousemove — handles panning only.
// Node/connection drag mousemove is handled by document-level onDocumentMouseMove.
function onCanvasMouseMove(e) {
  if (panDrag.value.active) {
    state.panOffset.value = {
      x: panDrag.value.originX + e.clientX - panDrag.value.startX,
      y: panDrag.value.originY + e.clientY - panDrag.value.startY
    }
  }
}

function onCanvasMouseUp(e) {
  // Canvas-level mouseup only handles panning — node/connection drags
  // are handled by document-level listeners (onDocumentMouseUp)
  if (panDrag.value.active) {
    panDrag.value = { active: false, startX: 0, startY: 0, originX: 0, originY: 0 }
  }
}

// Document-level mousemove — fires even when cursor is outside canvas-area
function onDocumentMouseMove(e) {
  // Group dragging
  if (groupDrag.value.active) {
    const point = screenToCanvas(e.clientX, e.clientY)
    const dx = Math.round(point.x - groupDrag.value.startX)
    const dy = Math.round(point.y - groupDrag.value.startY)
    groupDrag.value.nodes.forEach(item => {
      const node = findNodeByRef(item.id)
      if (!node) return
      node.x = Math.max(0, item.startX + dx)
      node.y = Math.max(0, item.startY + dy)
    })
  }

  // Node dragging
  if (nodeDrag.value.active) {
    const node = findNodeByRef(nodeDrag.value.nodeId)
    if (node) {
      const point = screenToCanvas(e.clientX, e.clientY)
      node.x = Math.max(0, Math.round(point.x - nodeDrag.value.offsetX))
      node.y = Math.max(0, Math.round(point.y - nodeDrag.value.offsetY))
    }
  }

  // Connection dragging — update temp line endpoint
  if (connectionDrag.value.active) {
    const point = screenToCanvas(e.clientX, e.clientY)
    connectionDrag.value.mouseX = point.x
    connectionDrag.value.mouseY = point.y
  }
}

// Document-level mouseup — handles drag termination anywhere in the document
function onDocumentMouseUp(e) {
  // Finish group drag
  if (groupDrag.value.active) {
    const changed = groupDrag.value.nodes
      .map(item => {
        const node = findNodeByRef(item.id)
        if (!node) return null
        return {
          node,
          startX: item.startX,
          startY: item.startY,
          x: node.x || 0,
          y: node.y || 0
        }
      })
      .filter(item => item && (item.x !== item.startX || item.y !== item.startY))
    if (changed.length) {
      canvas.updateNodePositions(changed.map(item => ({
        node_id: nodeKey(item.node),
        x: item.x,
        y: item.y
      }))).catch(() => {})
      state.markSaved()
    }
    groupDrag.value = { active: false, groupId: null, startX: 0, startY: 0, nodes: [] }
  }

  // Finish node drag
  if (nodeDrag.value.active) {
    const node = findNodeByRef(nodeDrag.value.nodeId)
    if (node) {
      // Only persist if position actually changed
      if (node.x !== nodeDrag.value.startX || node.y !== nodeDrag.value.startY) {
        canvas.updateNode(nodeKey(node), { x: node.x, y: node.y }).catch(() => {})
        state.markSaved()
      }
    }
    nodeDrag.value = { active: false, nodeId: null, offsetX: 0, offsetY: 0, startX: 0, startY: 0 }
  }

  // Connection drag released on empty canvas
  if (connectionDrag.value.active) {
    // Only show create-menu shortcut if released over the canvas area
    const canvasRect = canvasAreaRef.value?.getBoundingClientRect()
    const overCanvas = canvasRect &&
      e.clientX >= canvasRect.left && e.clientX <= canvasRect.right &&
      e.clientY >= canvasRect.top && e.clientY <= canvasRect.bottom
    if (overCanvas) {
      pendingConnectionSource.value = connectionDrag.value.sourceNode
      createMenuPos.value = { x: e.clientX, y: e.clientY }
      createMenuVisible.value = true
    }
    connectionDrag.value = { active: false, sourceNode: null, mouseX: 0, mouseY: 0 }
  }

  // Clean up document-level listeners
  cleanupDocumentListeners()
}

// Called when mouse leaves canvas area — cleanly cancel any drag without side effects
function onCanvasMouseLeave() {
  if (groupDrag.value.active) {
    const changed = groupDrag.value.nodes
      .map(item => {
        const node = findNodeByRef(item.id)
        if (!node) return null
        return {
          node,
          startX: item.startX,
          startY: item.startY,
          x: node.x || 0,
          y: node.y || 0
        }
      })
      .filter(item => item && (item.x !== item.startX || item.y !== item.startY))
    if (changed.length) {
      canvas.updateNodePositions(changed.map(item => ({
        node_id: nodeKey(item.node),
        x: item.x,
        y: item.y
      }))).catch(() => {})
      state.markSaved()
    }
    groupDrag.value = { active: false, groupId: null, startX: 0, startY: 0, nodes: [] }
  }
  if (nodeDrag.value.active) {
    const node = findNodeByRef(nodeDrag.value.nodeId)
    if (node && (node.x !== nodeDrag.value.startX || node.y !== nodeDrag.value.startY)) {
      canvas.updateNode(nodeKey(node), { x: node.x, y: node.y }).catch(() => {})
      state.markSaved()
    }
    nodeDrag.value = { active: false, nodeId: null, offsetX: 0, offsetY: 0, startX: 0, startY: 0 }
  }
  if (connectionDrag.value.active) {
    // Just cancel — no create menu
    connectionDrag.value = { active: false, sourceNode: null, mouseX: 0, mouseY: 0 }
  }
  cleanupDocumentListeners()
}

// Shared drag state reset (used by completeConnectionDrag)
function cleanupDragState() {
  connectionDrag.value = { active: false, sourceNode: null, mouseX: 0, mouseY: 0 }
  cleanupDocumentListeners()
}

// Remove document-level listeners when drag ends
function cleanupDocumentListeners() {
  document.removeEventListener('mousemove', onDocumentMouseMove)
  document.removeEventListener('mouseup', onDocumentMouseUp)
}

// ===== Workflow =====
async function handleApplyWorkflow(wf) {
  try {
    const confirmed = await confirmTaskCost('agent', {
      workflow_id: wf.uuid || wf.id
    }, '整组执行工作流')
    if (!confirmed) return
    const res = await canvas.executeWorkflow(wf.uuid || wf.id)
    ElMessage.success('工作流已执行')
  } catch (e) { ElMessage.error('工作流执行失败') }
}

async function quickCreateProductionChain() {
  const x = 120
  const y = 120
  const chain = [
    ['text', x, y],
    ['script', x + 300, y],
    ['image', x + 700, y - 120],
    ['image', x + 700, y + 90],
    ['video', x + 1040, y - 120],
    ['video', x + 1040, y + 90],
    ['audio', x + 1040, y + 300]
  ]
  const created = []
  for (const [type, nx, ny] of chain) {
    const node = await canvas.addNode(type, nx, ny, defaultNodeData(type))
    if (node) created.push(node)
  }
  const connect = async (fromType, toType) => {
    const from = created.find(n => n.type === fromType)
    const to = created.find(n => n.type === toType)
    if (from && to) await canvas.connectNodes(nodeKey(from), nodeKey(to)).catch(() => {})
  }
  await connect('text', 'script')
  await connect('script', 'image')
  await connect('image', 'video')
  state.selectNode(nodeKey(created[0]))
  state.markSaved()
  ElMessage.success('已创建漫剧生产链')
}

function textPresetPrompt(mode) {
  const prompts = {
    text_to_video: '电影级人物镜头 雨夜街头，霓虹灯反射在湿漉漉的地面，女主撑伞站在路灯下，表情克制而坚定，镜头环绕人物360度缓慢移动，浅景深，光影对比强烈，电影级调色，真实皮肤质感',
    image_to_prompt: '',
    text_to_music: '生成一首现代品牌电子音乐（约 110 BPM），干净有力的低频贝斯，清晰电子鼓点，整体风格高级、未来感强。开场节奏型贝斯与简洁合成器音色建立律动。主段加入稳定鼓点，节奏清晰，保持克制的张力。强化段加入更丰富的音层，合成器音色提升，律动增强但不过度拥挤。结尾鼓点减弱，仅保留低频与氛围音渐出，干净利落收尾。'
  }
  return prompts[mode] || ''
}

function textPresetGroupTitle(mode) {
  return {
    text_to_video: '预设 - 文生视频',
    image_to_prompt: '预设 - 图片反推提示词',
    text_to_music: '预设 - 文字生音乐'
  }[mode] || '预设'
}

function sampleReverseImageSvg() {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="360" height="540" viewBox="0 0 360 540">
    <defs>
      <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0%" stop-color="#172554"/>
        <stop offset="45%" stop-color="#334155"/>
        <stop offset="100%" stop-color="#111827"/>
      </linearGradient>
      <radialGradient id="light" cx="55%" cy="42%" r="44%">
        <stop offset="0%" stop-color="#dbeafe" stop-opacity=".95"/>
        <stop offset="55%" stop-color="#818cf8" stop-opacity=".4"/>
        <stop offset="100%" stop-color="#f97316" stop-opacity=".14"/>
      </radialGradient>
    </defs>
    <rect width="360" height="540" fill="url(#bg)"/>
    <circle cx="196" cy="212" r="168" fill="url(#light)"/>
    <path d="M56 378 C92 272 126 238 178 210 C238 178 292 124 330 54" fill="none" stroke="#bfdbfe" stroke-width="18" opacity=".78"/>
    <path d="M76 410 C118 316 148 282 196 252 C238 226 274 188 324 128" fill="none" stroke="#a78bfa" stroke-width="7" opacity=".9"/>
    <circle cx="180" cy="190" r="34" fill="#111827"/>
    <path d="M136 238 L224 238 L252 382 L108 382 Z" fill="#e5e7eb"/>
    <path d="M150 250 L206 250 L188 386 L124 386 Z" fill="#4338ca" opacity=".86"/>
    <path d="M78 440 C154 396 242 394 326 446 L326 540 L78 540 Z" fill="#0f172a" opacity=".72"/>
    <path d="M40 466 C102 430 172 430 232 466" fill="none" stroke="#fb923c" stroke-width="8" opacity=".75"/>
  </svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

async function handleTextPreset(node, mode) {
  if (mode === 'manual') {
    const data = {
      ...readNodeData(node),
      text_mode: 'manual',
      prompt: getNodePrompt(node),
      content: getNodePrompt(node)
    }
    await canvas.updateNode(nodeKey(node), { data, width: 520, height: 300, status: 'ready' }).catch(() => {
      ElMessage.error('切换文本编辑模式失败')
    })
    state.markSaved()
    return
  }

  if (mode === 'text_to_video') {
    await createTextPresetGroup(node, {
      mode,
      outputType: 'video',
      outputData: { prompt: '', generator_mode: 'text_to_video', aspect_ratio: '16:9' }
    })
    return
  }

  if (mode === 'image_to_prompt') {
    await createImageReversePromptGroup(node)
    return
  }

  if (mode === 'text_to_music') {
    await createTextPresetGroup(node, {
      mode,
      outputType: 'audio',
      outputData: { prompt: '', generator_mode: 'text_to_music', duration: 30 }
    })
  }
}

async function createTextPresetGroup(node, config) {
  const groupId = `preset_${config.mode}_${Date.now()}`
  const title = textPresetGroupTitle(config.mode)
  const prompt = getNodePrompt(node) || textPresetPrompt(config.mode)
  const sourceData = {
    ...readNodeData(node),
    text_mode: 'prompt',
    prompt,
    content: prompt,
    preset_group: { id: groupId, title, role: 'source' },
    preset_mode: config.mode
  }
  await canvas.updateNode(nodeKey(node), {
    name: node.name || '文本节点',
    data: sourceData,
    width: 520,
    height: 300,
    status: 'ready'
  }).catch(() => {
    ElMessage.error('预设文本节点保存失败')
  })

  const output = await canvas.addNode(config.outputType, (node.x || 0) + 650, node.y || 80, {
    ...config.outputData,
    upstream_node_id: nodeKey(node),
    preset_group: { id: groupId, title, role: 'output' },
    preset_mode: config.mode
  }, { width: 520, height: config.outputType === 'audio' ? 300 : 360 })
  if (output) {
    await canvas.connectNodes(nodeKey(node), nodeKey(output)).catch(() => {})
    state.selectNode(nodeKey(output))
  }
  state.markSaved()
  ElMessage.success(`已创建${title}`)
}

async function createImageReversePromptGroup(node) {
  const groupId = `preset_image_to_prompt_${Date.now()}`
  const title = textPresetGroupTitle('image_to_prompt')
  const image = await canvas.addNode('image', node.x || 80, node.y || 80, {
    prompt: '上传或拖入图片，用于反推提示词',
    generator_mode: 'image_to_prompt_source',
    preview_url: sampleReverseImageSvg(),
    preset_group: { id: groupId, title, role: 'source' },
    preset_mode: 'image_to_prompt'
  }, { width: 520, height: 360 })
  if (!image) return

  const data = {
    ...readNodeData(node),
    text_mode: 'prompt',
    prompt: '',
    content: '',
    generator_mode: 'image_to_prompt',
    upstream_node_id: nodeKey(image),
    preset_group: { id: groupId, title, role: 'output' },
    preset_mode: 'image_to_prompt'
  }
  await canvas.updateNode(nodeKey(node), {
    x: (node.x || 80) + 650,
    y: node.y || 80,
    name: node.name || '文本节点',
    data,
    width: 520,
    height: 300,
    status: 'ready'
  }).catch(() => {
    ElMessage.error('图片反推提示词节点保存失败')
  })
  await canvas.connectNodes(nodeKey(image), nodeKey(node)).catch(() => {})
  state.selectNode(nodeKey(node))
  state.markSaved()
  ElMessage.success(`已创建${title}`)
}

function recommendedNextNode(type) {
  return {
    text: 'script',
    director: 'image',
    script: 'image',
    image: 'video',
    video: 'audio',
    audio: 'video'
  }[type] || 'image'
}

async function runGeneratorForSelected() {
  const node = selectedNodeForPanel.value
  if (!node) return
  const data = {
    ...readNodeData(node),
    prompt: generatorPrompt.value,
    model_id: generatorConfig.modelId,
    aspect_ratio: generatorConfig.aspectRatio,
    duration: generatorConfig.duration,
    variants: generatorConfig.variants,
    mode: generatorConfig.mode
  }
  await canvas.updateNode(nodeKey(node), { data, status: 'ready' }).catch(() => {})
  await runNodeTask(node, {
    label: generatorActionLabel(generatorConfig.mode),
    taskType: generatorConfig.mode,
    modelId: generatorConfig.modelId,
    command: generatorActionLabel(generatorConfig.mode)
  })
}

function generatorActionLabel(mode) {
  return { text: '生成文本', image: '生成图片', video: '生成视频', audio: '生成音频', agent: '执行工作流', director_open: '打开导演台', director_capture: '导演台截图', director_send_canvas: '发送到画布', director_panorama: '全景设置' }[mode] || '执行生成'
}

function generatorDescription(type) {
  return {
    text: '文本节点 · 自己编写内容、文生视频、图片反推提示词、文字生音乐',
    director: '导演台 · 轻量3D构图、角色/元素/机位管理、截图发送到画布作为参考图',
    image: '图片生成器 · 上传图片、文生图、图生图、多图参考融合、图像编辑',
    video: '视频生成器 · 上传视频、文生视频、图生视频、首尾帧、多模态参考、视频编辑/延展',
    audio: '音频生成器 · 上传音频、生成音乐、音效、文字转语音',
    script: '脚本节点 · 确认镜头信息、整理资产、合成最终提示词、批量生成'
  }[type] || '节点生成器'
}

function generatorModes(type) {
  const modes = {
    text: [
      { label: '自己编写内容', value: 'manual_text' },
      { label: '文生视频', value: 'text_to_video' },
      { label: '图片反推提示词', value: 'image_to_prompt' },
      { label: '文字生音乐', value: 'text_to_music' }
    ],
    director: [
      { label: '打开导演台', value: 'director_open' },
      { label: '机位截图', value: 'director_capture' },
      { label: '截图发送到画布', value: 'director_send_canvas' },
      { label: '全景模式设置', value: 'director_panorama' }
    ],
    image: [
      { label: '文生图', value: 'image' },
      { label: '图生图', value: 'image_to_image' },
      { label: '多图参考融合', value: 'multi_ref_image' },
      { label: '图像编辑', value: 'image_edit' },
      { label: '全景模式', value: 'panorama' },
      { label: '智能打光', value: 'lighting' }
    ],
    video: [
      { label: '文生视频', value: 'video' },
      { label: '图生视频', value: 'image_to_video' },
      { label: '首尾帧视频', value: 'first_last_frame' },
      { label: '多模态参考视频', value: 'multi_ref_video' },
      { label: '视频编辑', value: 'video_edit' },
      { label: '视频延展', value: 'video_extend' }
    ],
    audio: [
      { label: '生成音乐', value: 'music' },
      { label: '生成音效', value: 'sfx' },
      { label: '文字转语音', value: 'tts' },
      { label: '音频截取', value: 'audio_trim' },
      { label: '音频变速', value: 'audio_speed' }
    ],
    script: [
      { label: '拆解剧本为分镜', value: 'script_breakdown' },
      { label: '合成最终提示词', value: 'script_prompt' },
      { label: '批量生分镜图', value: 'script_batch_image' },
      { label: '批量生视频', value: 'script_batch_video' }
    ]
  }
  return modes[type] || [{ label: '执行生成', value: taskTypeForNode(type) }]
}

// ===== Slash Commands =====
async function handleSlash(cmd) {
  const selectedNode = findNodeByRef(state.selectedNodeId.value)
  const command = encodeURIComponent(cmd.replace(/^[^\u4e00-\u9fa5A-Za-z0-9]+/, '').trim())
  if (canvas.localMode.value) {
    if (selectedNode) await canvas.updateNode(nodeKey(selectedNode), { status: 'processing' }).catch(() => {})
    ElMessage.success(`本地模式已记录 Slash 命令: ${cmd}`)
    return
  }
  try {
    const res = await canvasApi.runSlashCommand(state.projectId.value, command, {
      node_id: selectedNode ? nodeKey(selectedNode) : null,
      selected_node_ids: state.selectedNodes.value,
      command: cmd
    })
    if (selectedNode) await canvas.updateNode(nodeKey(selectedNode), { status: 'processing' }).catch(() => {})
    ElMessage.success(`已创建 Slash 任务: ${res.data?.uuid || res.data?.task_id || ''}`)
  } catch (e) {
    ElMessage.error(`Slash命令 "${cmd}" 执行失败`)
  }
}

// ===== Timeline / Export =====
function openTimeline() {
  canvas.loadTimeline().then(() => { timelineVisible.value = true })
}

async function handleExport() {
  try {
    const confirmed = await confirmTaskCost('export', {
      resolution: '1080p',
      fps: 25,
      format: 'mp4'
    }, '导出 MP4')
    if (!confirmed) return
    const res = await canvas.exportVideo({ format: 'mp4', resolution: '1080p', fps: 25 })
    ElMessage.success('导出任务已创建: ' + (res.data?.task_id || res.data?.uuid || ''))
  } catch (e) { ElMessage.error('导出失败') }
}

async function createDownstreamNode(sourceNode, type) {
  const x = (sourceNode.x || 0) + nodeW(sourceNode) + 120
  const y = sourceNode.y || 80
  const created = await canvas.addNode(type, x, y, {
    upstream_node_id: nodeKey(sourceNode),
    upstream_type: sourceNode.type,
    prompt: getNodePrompt(sourceNode)
  })
  if (created) {
    await canvas.connectNodes(nodeKey(sourceNode), nodeKey(created)).catch(() => {})
    state.selectNode(nodeKey(created))
    state.markSaved()
  }
  return created
}

async function runNodeTask(node, action) {
  const taskType = action.taskType || taskTypeForNode(node.type)
  if (node.type === 'director' || ['director_open', 'director_capture', 'director_send_canvas', 'director_panorama'].includes(taskType)) {
    if (taskType === 'director_open' || taskType === 'director_panorama' || action.label === '打开导演台') {
      openDirectorDesk(node)
      return
    }
    if (taskType === 'director_send_canvas') {
      await sendDirectorShotToCanvas(node)
      return
    }
    await captureDirectorShot(node)
    return
  }
  const params = {
    node_id: nodeKey(node),
    prompt: getNodePrompt(node),
    action: action.label,
    model_id: action.modelId || defaultModelForTask(taskType)
  }
  const confirmed = await confirmTaskCost(taskType, params, action.label)
  if (!confirmed) return
  if (canvas.localMode.value) {
    await canvas.updateNode(nodeKey(node), {
      status: 'completed',
      data: {
        ...readNodeData(node),
        prompt: params.prompt,
        last_action: action.label,
        model_id: params.model_id,
        local_result: `${action.label}已在本地模式记录，接入后端后会创建真实生成任务。`
      }
    }).catch(() => {})
    ElMessage.success(`${action.label}已记录到节点`)
    return
  }
  try {
    const command = encodeURIComponent(action.command || action.label)
    const res = await canvasApi.runSlashCommand(state.projectId.value, command, params)
    await canvas.updateNode(nodeKey(node), { status: 'processing' }).catch(() => {})
    ElMessage.success(`${action.label}任务已创建: ${res.data?.uuid || res.data?.task_id || ''}`)
  } catch (e) {
    ElMessage.error(`${action.label}任务创建失败`)
  }
}

async function confirmTaskCost(type, parameters, actionName) {
  const modelId = parameters?.model_id || defaultModelForTask(type)
  let credits = estimatedCostForNode(type)
  try {
    const res = await generationApi.estimateCredits({
      type,
      model_id: modelId,
      parameters
    })
    credits = res.data?.credits ?? credits
  } catch {
    credits = { image: 10, video: 50, audio: 5, export: 20, agent: 25, text: 10 }[type] || 10
  }
  try {
    await ElMessageBox.confirm(
      `本次将根据当前节点输入执行「${actionName}」任务，预计消耗 ${credits} 积分。确认后会创建生成任务，结果回写到当前节点或自动创建下游节点。`,
      '确认执行任务',
      { confirmButtonText: `确认 · ${credits}积分`, cancelButtonText: '取消', type: 'warning' }
    )
    return true
  } catch {
    return false
  }
}

function taskTypeForNode(type) {
  if (type === 'image') return 'image'
  if (type === 'video') return 'video'
  if (type === 'audio') return 'audio'
  if (type === 'director') return 'director_capture'
  return 'text'
}

function defaultModelForTask(type) {
  return { image: 'seedream-5.0', video: 'seedance-2.0', audio: 'volcano-tts', export: 'h264', agent: 'deepseek-v3' }[type] || 'deepseek-v3'
}

// ===== Helpers =====
function nodeKey(node) {
  if (!node) return ''
  return node.uuid || String(node.id)
}

function findNodeByRef(ref) {
  if (ref == null) return null
  return canvas.nodes.value.find(n => n.uuid === ref || String(n.id) === String(ref)) || null
}

function screenToCanvas(clientX, clientY) {
  const rect = canvasAreaRef.value?.getBoundingClientRect()
  const scale = state.zoomLevel.value / 100
  if (!rect || !scale) return { x: 0, y: 0 }
  return {
    x: (clientX - rect.left - state.panOffset.value.x) / scale,
    y: (clientY - rect.top - state.panOffset.value.y) / scale
  }
}

function nodeLabel(type) {
  return {
    text:'文本节点', script:'脚本 new 节点', director:'导演台节点', image:'图片节点', video:'视频节点', audio:'音频节点'
  }[type] || '节点'
}

function nodeH(nodeOrType) {
  const type = typeof nodeOrType === 'string' ? nodeOrType : nodeOrType?.type
  const savedHeight = typeof nodeOrType === 'object' ? Number(nodeOrType?.height) : 0
  if (savedHeight > 0) return savedHeight
  if (typeof nodeOrType === 'object' && type === 'text') {
    const mode = getTextNodeMode(nodeOrType)
    return mode === 'choice' ? 520 : 300
  }
  return { script: 280, director: 220, text: 300, video: 360, audio: 300, image: 360, compose: 170, workflow: 170 }[type] || 180
}

function nodePortPosition(node, port) {
  return {
    x: (node.x || 0) + (port === 'out' ? nodeW(node) : 0),
    y: (node.y || 0) + nodeH(node) / 2
  }
}

function defaultNodeData(type) {
  if (type === 'text') {
    return { prompt: '', content: '', text_mode: 'choice', source: 'canvas' }
  }
  if (type === 'script') {
    return {
      prompt: '',
      shots: [
        {
          shot_no: 'SH001',
          scene_no: 'S01',
          shot_size: 'MS',
          camera_motion: 'fixed',
          duration: 3000,
          visual_description: '',
          dialogue_text: '',
          voiceover: '',
          image_prompt: '',
          video_prompt: '',
          characters: '',
          props: '',
          action: '',
          image_status: 'pending',
          video_status: 'pending'
        }
      ]
    }
  }
  if (type === 'director') {
    return {
      prompt: '导演台构图、机位截图和参考图管理',
      aspect_ratio: '16:9',
      director: {
        elements: defaultDirectorElements(),
        shots: [],
        camera: { fov: 35, focus: '', x: 0, y: 160, z: 520 },
        aspect: '16:9'
      }
    }
  }
  if (type === 'character') {
    return { prompt: '角色名称、年龄、身份、服饰、发型、性格、三视图要求', consistency_level: 'L1' }
  }
  if (type === 'scene') {
    return { prompt: '场景名称、时代、空间结构、光线、色彩、镜头氛围', consistency_level: 'L1' }
  }
  if (type === 'prop') {
    return { prompt: '道具名称、材质、用途、出现场景、特写要求', consistency_level: 'L1' }
  }
  if (type === 'reference') {
    return { prompt: '参考图/参考视频说明，可用于构图、风格、运镜、角色姿态迁移', ref_type: 'image_video' }
  }
  if (type === 'compose') {
    return { prompt: '合成视频：按视频轨、音频轨、字幕轨、BGM轨输出成片', format: 'mp4' }
  }
  if (type === 'workflow') {
    return { prompt: '工作流：按节点连线顺序整组执行，结果回写到对应节点', mode: 'dag' }
  }
  return { prompt: '', source: 'canvas' }
}

function nodeW(nodeOrType) {
  const type = typeof nodeOrType === 'string' ? nodeOrType : nodeOrType?.type
  const savedWidth = typeof nodeOrType === 'object' ? Number(nodeOrType?.width) : 0
  if (savedWidth > 0) return savedWidth
  if (typeof nodeOrType === 'object' && type === 'text') {
    const mode = getTextNodeMode(nodeOrType)
    return mode === 'choice' ? 560 : 520
  }
  return { script: 340, director: 280, text: 520, video: 520, audio: 520, image: 520, compose: 230, workflow: 240, reference: 220 }[type] || 200
}

function nodeIcon(type) {
  return { text:'Document', image:'Picture', video:'VideoCamera', audio:'Headset', script:'Film', director:'VideoCameraFilled' }[type] || 'Box'
}

function assetIcon(type) {
  return { character:'User', scene:'Picture', prop:'Box', voice:'Microphone' }[type] || 'Folder'
}

function statusBadge(s) {
  return { editing:'badge-success', generating:'badge-warning', composing:'badge-warning',
    exporting:'badge-warning', completed:'badge-accent', archived:'badge-default' }[s] || 'badge-default'
}

function maturityBadge(level) {
  return { L0:'badge-neutral', L1:'badge-warning', L2:'badge-accent', L3:'badge-success', L4:'badge-danger' }[level] || 'badge-neutral'
}

function nodeStatusText(status) {
  return { ready:'就绪', processing:'生成中', running:'生成中', completed:'完成',
    failed:'失败', pending:'等待中' }[status] || '待配置'
}

function nodeStatusClass(status) {
  return { ready:'status-ready', processing:'status-running', running:'status-running',
    completed:'status-done', failed:'status-failed', pending:'status-pending' }[status] || 'status-idle'
}

function estimatedCostForNode(type) {
  return { text: 10, script: 10, director: 0, image: 10, video: 50, audio: 5, workflow: 25 }[type] || 10
}

function nodeActions(type) {
  const actions = {
    text: [],
    director: [{ label:'打开导演台', taskType:'director_open' }, { label:'机位截图', taskType:'director_capture' }, { label:'发送到画布', taskType:'director_send_canvas' }],
    image: [{ label:'发送到视频', creates:'video' }, { label:'Inpaint', taskType:'image' }, { label:'高清放大', taskType:'image' }],
    video: [{ label:'配音', creates:'audio' }, { label:'字幕', taskType:'audio' }, { label:'剪辑', taskType:'video' }],
    audio: [{ label:'转字幕', taskType:'audio' }, { label:'进时间轴' }],
    script: [{ label:'合成提示词', taskType:'text' }, { label:'批量生图', creates:'image' }, { label:'批量视频', creates:'video' }]
  }
  return actions[type] || [{ label:'执行' }]
}
</script>

<style scoped>
.canvas-page { position:relative; display:flex; flex-direction:column; height:100vh; min-height:0; overflow:hidden; background:#0f172a; color:#e0e0e0; }
.canvas-toolbar { display:flex; align-items:center; justify-content:space-between; padding:8px 16px;
  background:#1a1a2e; border-bottom:1px solid #2a2a3e; flex-shrink:0; color:#e0e0e0; }
.project-menu-btn { border:1px solid #2a2a3e; background:#111827; color:#e0e7ff; border-radius:6px; padding:5px 9px; font-size:13px; font-weight:800; cursor:pointer; }
.project-menu-btn:hover { border-color:#818cf8; }
.zoom-label { font-size:12px; color:#a1a1aa; padding:4px 8px; background:#0f172a; border-radius:4px; }
.canvas-body { display:flex; flex:1; min-height:0; overflow:hidden; }
.canvas-left-panel { width:248px; background:#1a1a2e; border-right:1px solid #2a2a3e;
  display:flex; flex-direction:column; flex-shrink:0; overflow-y:auto; padding:12px; color:#e0e0e0; --text-secondary:#a1a1aa; }
.canvas-tabs { display:flex; gap:4px; margin-bottom:12px; }
.canvas-tab { font-size:11px; padding:6px 8px; cursor:pointer; font-weight:600; color:#a1a1aa;
  border-bottom:2px solid transparent; transition:all .15s; min-height:32px; display:flex; align-items:center; gap:4px; }
.canvas-tab:hover { color:#e0e0e0; }
.canvas-tab.active { color:#818cf8; border-bottom-color:#818cf8; }
.tab-content { font-size:12px; }
.node-add-card { padding:10px 12px; margin-bottom:6px; display:flex; gap:10px; align-items:center;
  background:#1e293b; border:1px solid #2a2a3e; border-radius:8px; cursor:pointer; transition:all .15s; color:#e0e0e0; }
.node-add-card:hover { border-color:#818cf8; background:rgba(99,102,241,0.1); color:#fff; }
.node-add-card .node-icon { font-size:18px; }
.node-group { margin-bottom:12px; }
.node-group-title { color:#94a3b8; font-size:10px; font-weight:800; margin:10px 0 6px; letter-spacing:.06em; }
.reference-actions { display:grid; gap:6px; margin-bottom:12px; }
.reference-actions .el-button { margin-left:0; width:100%; }
.upload-drop { border:1px dashed #475569; color:#94a3b8; border-radius:8px; padding:12px; font-size:12px; text-align:center; cursor:pointer; background:#0f172a; }
.upload-drop:hover { border-color:#818cf8; color:#e0e7ff; }
.tutorial-list { display:grid; gap:8px; }
.tutorial-list button { text-align:left; border:1px solid #2a2a3e; background:#1e293b; color:#cbd5e1; border-radius:8px; padding:9px; cursor:pointer; }
.tutorial-list button:hover { border-color:#818cf8; }
.slash-btn { font-size:10px; padding:3px 8px; margin:0 4px 4px 0; }
.canvas-area { flex:1; min-width:0; min-height:0; background:#0f172a;
  background-image:radial-gradient(#1e293b 1px, transparent 1px); background-size:24px 24px;
  position:relative; overflow:hidden; cursor:grab; color:#e0e0e0; }
.canvas-area:active { cursor:grabbing; }
.canvas-stage { position:absolute; left:0; top:0; transform-origin:0 0; z-index:1; }
.preset-group-frame { position:absolute; z-index:4; pointer-events:auto; cursor:grab; border:1px solid #3f3f46; border-radius:18px;
  background:rgba(63,63,70,.5); box-shadow:inset 0 0 0 1px rgba(255,255,255,.03); }
.preset-group-frame.dragging { cursor:grabbing; border-color:#7dd3fc; box-shadow:inset 0 0 0 1px rgba(125,211,252,.22), 0 0 0 3px rgba(125,211,252,.12); }
.preset-group-title { position:absolute; left:0; top:-32px; color:#a1a1aa; font-size:18px; font-weight:600; pointer-events:auto; }
.connection-layer { position:absolute; inset:0; pointer-events:none; z-index:2; }
.connection-hit { fill:none; stroke:transparent; stroke-width:18; pointer-events:stroke; cursor:pointer; }
.connection-path { fill:none; stroke:#6366f1; stroke-width:2; stroke-dasharray:6 5; opacity:.72; pointer-events:none; }
.connection-path.selected { stroke:#f59e0b; stroke-width:3; opacity:1; filter:drop-shadow(0 0 6px rgba(245,158,11,.5)); }
.temp-connection { stroke:#f59e0b; stroke-width:2.5; stroke-dasharray:8 4; opacity:.9; animation:dash-march .6s linear infinite; }
.connection-delete {
  position:absolute; transform:translate(-50%, -50%); z-index:30;
  border:1px solid #f59e0b; background:#1f1606; color:#fbbf24;
  border-radius:6px; padding:4px 8px; font-size:11px; font-weight:700;
  cursor:pointer; box-shadow:0 6px 18px rgba(0,0,0,.35);
}
.connection-delete:hover { background:#3b2f0f; }
@keyframes dash-march { to { stroke-dashoffset: -24; } }
.minimap { position:absolute; bottom:12px; left:12px; width:140px; height:90px;
  background:rgba(30,30,50,.95); border:1px solid #333; border-radius:8px;
  z-index:10; font-size:10px; display:flex; flex-direction:column; align-items:center; justify-content:center; color:#888; }
.minimap strong { font-size:20px; color:#e0e7ff; line-height:1; }
.minimap small { color:#818cf8; margin-top:4px; }
.canvas-node { position:absolute; background:#1a1a2e; border-radius:12px;
  border:1px solid #2a2a3e; box-shadow:0 2px 8px rgba(0,0,0,.3); z-index:6; color:#e0e0e0; }
.canvas-node.selected { border-color:#818cf8; box-shadow:0 0 0 3px rgba(99,102,241,0.3), 0 4px 16px rgba(0,0,0,.4); }
.canvas-node.node-dragging { opacity:.85; box-shadow:0 8px 32px rgba(0,0,0,.5); z-index:20; cursor:grabbing; }
.canvas-node { cursor:grab; }
.canvas-node:active { cursor:grabbing; }
.node-header { padding:10px 14px; font-size:13px; font-weight:700; display:flex;
  align-items:center; justify-content:space-between; border-bottom:1px solid #2a2a3e; }
.node-status, .node-cost { display:inline-flex; align-items:center; height:18px; padding:0 6px; border-radius:4px;
  font-size:10px; font-weight:600; white-space:nowrap; }
.node-cost { background:#3b2f0f; color:#fbbf24; }
.status-ready { background:#0f2f3b; color:#7dd3fc; }
.status-running { background:#3b2f0f; color:#fbbf24; }
.status-done { background:#063b2b; color:#34d399; }
.status-failed { background:#3b0f0f; color:#f87171; }
.status-pending { background:#312e81; color:#a5b4fc; }
.status-idle { background:#1e293b; color:#94a3b8; }
.node-body { max-height:180px; overflow-y:auto; }
.node-text .node-body, .node-video .node-body, .node-image .node-body, .node-audio .node-body { max-height:none; overflow:visible; }
.script-progress { display:grid; grid-template-columns:repeat(4,1fr); gap:4px; padding:8px; border-bottom:1px solid #222; }
.script-progress span { background:#111827; color:#a5b4fc; border:1px solid #312e81; border-radius:4px; padding:4px; font-size:9px; text-align:center; }
.empty-node-tip { padding:10px; color:#94a3b8; font-size:11px; }
.node-script-table table { width:100%; font-size:10px; color:#ccc; }
.node-script-table th { font-size:9px; padding:6px 8px; background:#111; color:#a1a1aa; }
.node-script-table td { padding:4px 8px; font-size:10px; border-bottom:1px solid #222; color:#ccc; }
.canvas-mock { background:#111; border-radius:6px; display:flex; align-items:center;
  justify-content:center; font-size:12px; color:#94a3b8; --text-secondary:#a1a1aa; margin:8px; }
.node-image .canvas-mock, .node-video .canvas-mock { height:300px; border:2px solid #737373; border-radius:10px; background:#262626; margin:10px; overflow:hidden; }
.node-preview-image { width:100%; height:100%; max-height:none; object-fit:cover; border-radius:6px; display:block; }
.media-placeholder { width:100%; height:100%; display:flex; align-items:center; justify-content:center; color:#a3a3a3; }
.video-placeholder span { width:0; height:0; border-top:42px solid transparent; border-bottom:42px solid transparent; border-left:62px solid #6b7280; opacity:.72; }
.image-placeholder { font-size:18px; }
.audio-placeholder { height:250px; margin:10px; border:1px solid #333; border-radius:10px; background:#262626; display:flex; align-items:center; justify-content:center; gap:10px; }
.audio-placeholder span { width:10px; border-radius:99px; background:#737373; animation:audio-idle 1.2s ease-in-out infinite; }
.audio-placeholder span:nth-child(1) { height:42px; animation-delay:0s; }
.audio-placeholder span:nth-child(2) { height:86px; animation-delay:.12s; }
.audio-placeholder span:nth-child(3) { height:118px; animation-delay:.24s; }
.audio-placeholder span:nth-child(4) { height:76px; animation-delay:.36s; }
.audio-placeholder span:nth-child(5) { height:50px; animation-delay:.48s; }
@keyframes audio-idle { 50% { transform:scaleY(.62); opacity:.55; } }
.text-node-body { padding:0; }
.text-choice-panel { min-height:472px; display:flex; flex-direction:column; justify-content:center; padding:22px 46px 38px; color:#e5e7eb; }
.text-choice-empty { display:grid; gap:12px; justify-items:center; margin-bottom:70px; opacity:.35; }
.text-choice-empty span { display:block; width:86px; height:10px; background:#a3a3a3; }
.text-choice-empty small { display:block; width:52px; height:10px; background:#a3a3a3; }
.text-choice-label { color:#a3a3a3; font-size:22px; margin-bottom:18px; }
.text-choice-panel button { border:0; background:transparent; color:#f5f5f5; display:flex; align-items:center; gap:16px; padding:13px 18px; font-size:22px; cursor:pointer; border-radius:8px; text-align:left; }
.text-choice-panel button:hover { background:#333; }
.text-choice-panel button span { width:28px; color:#f5f5f5; font-weight:800; text-align:center; }
.text-manual-editor { position:relative; padding:0 10px 10px; }
.text-editor-toolbar { position:absolute; left:50%; bottom:calc(100% + 54px); transform:translateX(-50%); height:64px; min-width:760px; display:flex; align-items:center; justify-content:center; gap:20px; padding:0 24px; background:#262626; border:1px solid #333; border-radius:12px; box-shadow:0 12px 32px rgba(0,0,0,.32); }
.text-editor-toolbar button { border:0; background:transparent; color:#d4d4d8; font-size:20px; min-width:32px; height:34px; border-radius:6px; cursor:pointer; }
.text-editor-toolbar button:hover { background:#3f3f46; color:#fff; }
.text-editor-toolbar span { width:1px; height:30px; background:#3f3f46; }
.text-manual-editor textarea { width:100%; height:240px; resize:both; min-height:190px; color:#e5e7eb; background:#111; border:2px solid #8a8a8a; border-radius:10px; padding:16px; outline:none; font-size:16px; line-height:1.7; }
.text-prompt-card { min-height:240px; margin:10px; padding:18px 22px; border-radius:10px; background:#111; color:#f5f5f5; font-size:18px; line-height:1.7; white-space:pre-wrap; overflow:auto; }
.director-node-preview { padding:10px; }
.director-mini-stage { position:relative; height:92px; border:1px solid #334155; border-radius:8px;
  background:linear-gradient(180deg,#0f172a 0%,#111827 58%,#172033 100%);
  overflow:hidden; }
.director-mini-stage::before { content:''; position:absolute; inset:55% 0 0; opacity:.45;
  background-image:linear-gradient(#334155 1px,transparent 1px),linear-gradient(90deg,#334155 1px,transparent 1px);
  background-size:18px 18px; transform:skewX(-16deg); transform-origin:top; }
.director-mini-person, .director-mini-box, .director-mini-camera { position:absolute; display:block; }
.director-mini-person { left:28%; top:24%; width:20px; height:42px; border-radius:12px 12px 6px 6px;
  background:#818cf8; box-shadow:0 -12px 0 -4px #c7d2fe; }
.director-mini-box { right:24%; bottom:22%; width:34px; height:26px; background:#10b981; transform:rotate(-8deg); }
.director-mini-camera { left:8%; bottom:18%; width:32px; height:18px; border:2px solid #f59e0b; border-radius:4px; }
.director-node-meta { display:flex; justify-content:space-between; gap:6px; margin-top:8px; color:#94a3b8; font-size:11px; }
.node-actions { padding:8px 14px; border-top:1px solid #222; display:flex; gap:8px; }
.node-actions.compact { flex-wrap:wrap; gap:6px; }
.node-actions.compact .el-button { font-size:10px; padding:3px 8px; margin-left:0; }
.node-out-port, .node-in-port { position:absolute; top:50%; width:12px; height:12px; border-radius:50%;
  border:2px solid #fff; cursor:crosshair; z-index:10; }
.node-out-port { right:-6px; background:#6366f1; box-shadow:0 0 0 2px rgba(99,102,241,0.5); }
.node-in-port { left:-6px; background:#10b981; box-shadow:0 0 0 2px rgba(16,185,129,0.5); }
.node-in-port.connectable { transform:scale(1.25); box-shadow:0 0 0 4px rgba(16,185,129,0.28), 0 0 16px rgba(16,185,129,.75); }
.floating-add { position:absolute; bottom:16px; left:50%; transform:translateX(-50%); z-index:10; }
.canvas-bottom { background:#1a1a2e; border-top:1px solid #2a2a3e; flex-shrink:0; }
.generator-panel { border-bottom:1px solid #222; padding:10px 12px; background:#151526; }
.generator-head { display:flex; align-items:center; justify-content:space-between; gap:16px; margin-bottom:8px; }
.generator-head strong { display:block; font-size:13px; color:#e0e7ff; line-height:1.2; }
.generator-grid { display:grid; grid-template-columns:minmax(280px, 1fr) 520px; gap:12px; align-items:start; }
.generator-controls { display:grid; grid-template-columns:36px minmax(92px,1fr) 36px minmax(110px,1fr) 36px minmax(76px,1fr); gap:6px; align-items:center; }
.generator-controls label { color:#94a3b8; font-size:11px; font-weight:700; }
.node-context-menu { position:fixed; z-index:1000; display:grid; min-width:160px; background:#111827; border:1px solid #374151; border-radius:8px; overflow:hidden; box-shadow:0 16px 40px rgba(0,0,0,.45); }
.node-context-menu button { border:0; background:transparent; color:#e5e7eb; text-align:left; padding:9px 12px; cursor:pointer; font-size:12px; }
.node-context-menu button:hover { background:#1f2937; }
.node-context-menu button.danger { color:#fca5a5; }
.director-overlay { position:fixed; inset:0; z-index:1200; background:rgba(2,6,23,.78); display:flex; align-items:center; justify-content:center; padding:20px; }
.director-shell { width:min(1440px, 96vw); height:min(860px, 92vh); background:#0b1120; border:1px solid #334155; border-radius:10px;
  box-shadow:0 24px 80px rgba(0,0,0,.62); color:#e5e7eb; display:flex; flex-direction:column; overflow:hidden; }
.director-topbar { height:58px; flex-shrink:0; display:flex; align-items:center; justify-content:space-between; gap:16px;
  padding:10px 14px; border-bottom:1px solid #263244; background:#111827; }
.director-topbar strong { display:block; font-size:15px; line-height:1.2; }
.director-topbar span { color:#94a3b8; font-size:12px; }
.director-top-actions { display:flex; align-items:center; gap:8px; flex-wrap:wrap; justify-content:flex-end; }
.director-workspace { flex:1; min-height:0; display:grid; grid-template-columns:260px minmax(420px, 1fr) 320px; }
.director-panel { min-height:0; overflow:auto; background:#111827; padding:12px; border-color:#263244; }
.director-left { border-right:1px solid #263244; }
.director-right { border-left:1px solid #263244; }
.director-panel-title { margin:2px 0 8px; color:#cbd5e1; font-size:12px; font-weight:800; }
.director-add-grid { display:grid; grid-template-columns:1fr 1fr; gap:8px; margin-bottom:16px; }
.director-add-grid button, .director-panorama-actions button, .director-viewbar button {
  border:1px solid #334155; background:#0f172a; color:#cbd5e1; border-radius:6px; padding:7px 8px; font-size:12px; cursor:pointer;
}
.director-add-grid button:hover, .director-panorama-actions button:hover, .director-viewbar button:hover,
.director-viewbar button.active { border-color:#818cf8; color:#e0e7ff; background:#1e1b4b; }
.director-element-list { display:grid; gap:6px; margin-bottom:10px; }
.director-element-list button { display:grid; grid-template-columns:32px 1fr; grid-template-rows:auto auto; column-gap:8px;
  align-items:center; text-align:left; border:1px solid #273449; background:#0f172a; color:#e5e7eb; border-radius:8px; padding:8px; cursor:pointer; }
.director-element-list button span { grid-row:1 / span 2; display:flex; align-items:center; justify-content:center; height:28px;
  border-radius:6px; background:#1f2937; color:#a5b4fc; font-size:11px; font-weight:800; }
.director-element-list button small { color:#94a3b8; font-size:10px; }
.director-element-list button.active { border-color:#818cf8; background:#1e1b4b; }
.director-element-list button.hidden { opacity:.45; }
.director-list-actions { display:grid; grid-template-columns:1fr 1fr; gap:6px; }
.director-list-actions .el-button { margin-left:0; width:100%; }
.director-stage-wrap { position:relative; min-width:0; min-height:0; background:#0b1120; display:flex; flex-direction:column; }
.director-viewbar { height:42px; display:flex; gap:8px; align-items:center; padding:8px 10px; border-bottom:1px solid #1f2937; background:#0f172a; }
.director-stage { flex:1; position:relative; overflow:hidden;
  background:radial-gradient(circle at 50% 26%, rgba(129,140,248,.18), transparent 34%), #0b1120; }
.director-stage::before { content:''; position:absolute; left:6%; right:6%; bottom:9%; height:48%; opacity:.66;
  background-image:linear-gradient(#243244 1px,transparent 1px),linear-gradient(90deg,#243244 1px,transparent 1px);
  background-size:42px 42px; transform:perspective(420px) rotateX(62deg); transform-origin:bottom; }
.director-stage.view-top::before { transform:none; inset:50px 8% 56px; height:auto; }
.director-stage.view-front::before { transform:none; left:0; right:0; bottom:12%; height:1px; background:#334155; }
.director-ground { position:absolute; left:0; right:0; bottom:0; height:18%; background:linear-gradient(180deg, transparent, rgba(15,23,42,.9)); pointer-events:none; }
.director-camera-frame { position:absolute; left:50%; top:50%; transform:translate(-50%,-50%); border:2px solid rgba(245,158,11,.95);
  box-shadow:0 0 0 999px rgba(2,6,23,.32); z-index:2; pointer-events:none; }
.director-camera-frame span { position:absolute; left:8px; top:8px; color:#fbbf24; font-size:11px; background:rgba(15,23,42,.85); padding:3px 6px; border-radius:4px; }
.director-camera-frame.aspect-16-9 { width:62%; aspect-ratio:16 / 9; }
.director-camera-frame.aspect-9-16 { height:72%; aspect-ratio:9 / 16; }
.director-camera-frame.aspect-1-1 { width:46%; aspect-ratio:1 / 1; }
.director-camera-frame.aspect-4-3 { width:54%; aspect-ratio:4 / 3; }
.director-object { position:absolute; z-index:4; min-width:68px; height:54px; border:1px solid #475569; border-radius:8px;
  color:#e5e7eb; background:#1f2937; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:3px;
  cursor:pointer; box-shadow:0 12px 24px rgba(0,0,0,.35); transform-origin:center; touch-action:none; user-select:none; }
.director-object span { font-size:11px; color:#0f172a; background:#c7d2fe; border-radius:999px; padding:2px 6px; font-weight:800; }
.director-object strong { font-size:11px; max-width:86px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.director-object.selected { border-color:#f59e0b; box-shadow:0 0 0 3px rgba(245,158,11,.24), 0 16px 30px rgba(0,0,0,.42); }
.director-object-human { height:86px; border-radius:24px 24px 10px 10px; background:#4338ca; }
.director-object-geometry { background:#047857; }
.director-object-crowd { min-width:98px; background:repeating-linear-gradient(90deg,#1f2937 0 16px,#334155 16px 28px); }
.director-object-upload { background:#7c2d12; }
.director-shortcuts { height:34px; display:flex; align-items:center; gap:8px; padding:0 12px; color:#94a3b8; font-size:11px; border-top:1px solid #1f2937; overflow-x:auto; }
.director-shortcuts span { white-space:nowrap; }
.director-form { display:grid; gap:8px; margin-bottom:16px; }
.director-form label { color:#94a3b8; font-size:11px; font-weight:700; }
.director-number-row { display:grid; grid-template-columns:1fr 1fr 1fr; gap:6px; }
.director-panorama-actions { display:grid; grid-template-columns:1fr; gap:6px; }
.director-shot-list { display:grid; gap:8px; }
.director-shot-item { display:grid; grid-template-columns:1fr auto auto; gap:6px; align-items:center; border:1px solid #273449; background:#0f172a; border-radius:8px; padding:6px; }
.director-shot-item > button:first-child { border:0; background:transparent; color:#e5e7eb; text-align:left; cursor:pointer; padding:0; }
.director-shot-item img { display:block; width:100%; height:54px; object-fit:cover; border-radius:6px; margin-bottom:6px; border:1px solid #334155; }
.director-shot-item strong { display:block; font-size:12px; }
.director-shot-item span { color:#94a3b8; font-size:10px; }
.director-empty { color:#94a3b8; font-size:12px; padding:10px; border:1px dashed #334155; border-radius:8px; text-align:center; }
.flex { display:flex; } .gap-sm { gap:8px; } .gap-md { gap:12px; }
.items-center { align-items:center; } .font-bold { font-weight:600; }
.text-sm { font-size:12px; } .text-xs { font-size:11px; }
.text-muted { color:#888; } .mt-lg { margin-top:16px; } .mb-sm { margin-bottom:8px; }
.badge { display:inline-block; padding:2px 8px; border-radius:4px; font-size:11px; }
.badge-success { background:#064e3b; color:#34d399; }
.badge-warning { background:#78350f; color:#fbbf24; }
.badge-accent { background:#1e1b4b; color:#818cf8; }
.badge-neutral { background:#1e293b; color:#94a3b8; }
.badge-danger { background:#7f1d1d; color:#f87171; }
.badge-default { background:#1e293b; color:#94a3b8; }
.card { background:#1e293b; border:1px solid #2a2a3e; border-radius:8px; }
.card-hover { cursor:pointer; transition:all .15s; }
.card-hover:hover { border-color:#444; }
</style>
