<template>
  <div class="canvas-page">
    <!-- Toolbar -->
    <div class="canvas-toolbar">
      <div class="flex items-center gap-md">
        <el-button size="small" text class="canvas-back-btn" @click="router.push('/canvas-projects')">
          ← 项目中心
        </el-button>
        <el-dropdown trigger="click">
          <button class="project-menu-btn"><el-icon style="vertical-align:-2px"><Brush /></el-icon> {{ state.projectName.value }} ▾</button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="router.push('/canvas-projects')">全部项目</el-dropdown-item>
              <el-dropdown-item @click="resetLocalCanvas">新建空白画布</el-dropdown-item>
              <el-dropdown-item @click="renameCanvas">重命名画布</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <span :class="['badge', statusBadge(state.projectStatus.value)]">{{ state.projectStatus.value }}</span>
        <span v-if="canvas.localMode.value" class="badge badge-warning">本地可操作模式</span>
        <span class="text-sm text-muted">自动保存于 {{ state.lastSaved.value }}</span>
      </div>
      <div class="toolbar-actions">
        <span class="zoom-label" title="当前缩放">{{ state.zoomLevel.value }}%</span>
        <button type="button" class="toolbar-action-btn" @click="showAssetPicker = true">
          <el-icon :size="14"><FolderOpened /></el-icon>
          Workspace 资产
        </button>
      </div>
    </div>

    <div class="canvas-body">
      <!-- LEFT Panel -->
      <div v-show="!leftPanelCollapsed" class="canvas-left-panel">
        <nav class="canvas-tabs" aria-label="画布侧栏">
          <button
            v-for="tab in leftTabs"
            :key="tab.key"
            type="button"
            :class="['canvas-tab', { active: state.activeLeftTab.value === tab.key }]"
            @click="state.activeLeftTab.value = tab.key"
          >
            <el-icon :size="16"><component :is="tab.icon" /></el-icon>
            <span>{{ tab.label }}</span>
          </button>
        </nav>

        <!-- Add Node -->
        <div v-if="state.activeLeftTab.value === 'add'" class="tab-content">
          <section class="panel-section">
            <header class="panel-section-head">
              <h3>生产节点</h3>
              <p>双击画布空白处，或点击下方卡片添加</p>
            </header>
            <div v-for="group in nodeTypeGroups" :key="group.name" class="node-group">
              <div class="node-group-title">{{ group.name }}</div>
              <div class="node-add-grid">
                <button
                  v-for="nt in group.items"
                  :key="nt.type"
                  type="button"
                  class="node-add-card"
                  :style="{ '--node-accent': nt.accent }"
                  :title="nt.desc"
                  @click="handleAddNode(nt.type)"
                >
                  <span class="node-icon"><el-icon :size="16"><component :is="nt.icon" /></el-icon></span>
                  <span class="node-add-copy">
                    <strong>{{ nt.label }}</strong>
                    <small>{{ nt.short || nt.desc }}</small>
                  </span>
                </button>
              </div>
            </div>
          </section>

          <section class="panel-section">
            <header class="panel-section-head">
              <h3>添加资源</h3>
            </header>
            <button type="button" class="upload-drop" @click="ElMessage.info('可将图片/视频/音频直接拖入画布，或接入上传组件')">
              <span class="upload-drop-title">拖入媒体文件</span>
              <span class="upload-drop-hint">图片 / 视频 / 音频，或点击上传</span>
            </button>
          </section>

          <section class="panel-section">
            <header class="panel-section-head">
              <h3>Slash 快捷命令</h3>
            </header>
            <div class="slash-grid">
              <button
                v-for="cmd in canvas.SLASH_COMMANDS"
                :key="cmd"
                type="button"
                class="slash-chip"
                @click="handleSlash(cmd)"
              >{{ cmd }}</button>
            </div>
          </section>
        </div>

        <!-- Workflows -->
        <div v-if="state.activeLeftTab.value === 'workflow'" class="tab-content">
          <section class="panel-section">
            <header class="panel-section-head">
              <h3>已保存工作流</h3>
            </header>
            <button
              v-for="wf in canvas.workflows.value"
              :key="wf.id || wf.uuid"
              type="button"
              class="node-add-card"
              @click="handleApplyWorkflow(wf)"
            >
              <span class="node-add-copy">
                <strong>{{ wf.name }}</strong>
                <small>{{ wf.description }}</small>
              </span>
            </button>
            <div v-if="!canvas.workflows.value.length" class="panel-empty">暂无工作流</div>
          </section>
        </div>

        <!-- Assets -->
        <div v-if="state.activeLeftTab.value === 'assets'" class="tab-content">
          <section class="panel-section">
            <header class="panel-section-head">
              <h3>资产库</h3>
            </header>
            <div class="reference-actions">
              <el-button size="small" type="primary" @click="quickCreateProductionChain">创建脚本生产链</el-button>
              <el-button size="small" @click="ElMessage.info('将选中节点保存为资产，待接入资产接口')">保存为资产</el-button>
              <el-button size="small" @click="ElMessage.info('批量使用资产到当前画布')">批量使用</el-button>
            </div>
            <div v-for="asset in projectAssets" :key="asset.asset_id" class="asset-row">
              <span class="asset-row-name">{{ asset.name }}</span>
              <span :class="['badge', maturityBadge(asset.maturity_level)]">{{ asset.maturity_level || 'L0' }}</span>
            </div>
            <div v-if="!projectAssets.length" class="panel-empty">画布中尚无资产节点</div>
          </section>
        </div>

        <!-- History -->
        <div v-if="state.activeLeftTab.value === 'history'" class="tab-content">
          <section class="panel-section">
            <header class="panel-section-head">
              <h3>生成历史</h3>
            </header>
            <div class="reference-actions">
              <el-button size="small" type="primary" @click="$router.push('/asset-history')">打开历史记录</el-button>
              <el-button size="small" @click="ElMessage.info('批量下载生成历史，待接入后端')">批量下载</el-button>
              <el-button size="small" @click="ElMessage.info('批量删除生成历史，待接入后端')">批量删除</el-button>
              <el-button size="small" @click="ElMessage.info('批量使用历史资源到当前画布')">批量使用</el-button>
            </div>
          </section>
        </div>

        <div v-if="state.activeLeftTab.value === 'tutorial'" class="tab-content">
          <section class="panel-section">
            <header class="panel-section-head">
              <h3>教程</h3>
            </header>
            <div class="tutorial-list">
              <button type="button" @click="ElMessage.info('教程：双击空白画布新建节点')">新建节点</button>
              <button type="button" @click="ElMessage.info('教程：从节点右侧端口拖线到输入点建立连接')">节点连线</button>
              <button type="button" @click="ElMessage.info('教程：脚本节点按确认镜头、整理资产、合成提示词、批量生成执行')">脚本节点</button>
            </div>
          </section>
        </div>
      </div>

      <!-- CANVAS AREA -->
      <div class="canvas-area"
           ref="canvasAreaRef"
           @drop="onCanvasDrop"
           @dragover.prevent>
        <button
          class="left-panel-toggle"
          :title="leftPanelCollapsed ? '展开节点工具栏' : '收起节点工具栏'"
          @mousedown.stop
          @click.stop="leftPanelCollapsed = !leftPanelCollapsed"
        >{{ leftPanelCollapsed ? '›' : '‹' }}</button>

        <VueFlowCanvasStage
          ref="flowStageRef"
          :domain-nodes="canvas.nodes.value"
          :domain-connections="canvas.connections.value"
          :selected-node-id="state.selectedNodeId.value"
          :selected-edge-id="selectedConnectionId"
          @select-node="selectNode"
          @deselect="deselectCanvas"
          @connect="onFlowConnect"
          @select-edge="onFlowSelectEdge"
          @nodes-moved="onFlowNodesMoved"
          @viewport="onFlowViewport"
          @pane-dblclick="onCanvasDoubleClick"
          @drop="onCanvasDrop"
          @open-shot="openShotEditor"
          @open-director="openDirectorDesk"
          @node-context="onFlowNodeContext"
        />

        <button
          v-if="selectedConnectionId"
          class="connection-delete-btn"
          @mousedown.stop
          @click.stop="deleteSelectedConnection"
        >删除连线</button>

        <NodeFloatingEditor
          v-if="selectedNodeForPanel"
          :node="selectedNodeForPanel"
          :style="floatingEditorStyle"
          :placement="floatingPlacement"
          :shots="getNodeShots(selectedNodeForPanel)"
          :project-id="state.projectId.value"
          :local-mode="canvas.localMode.value"
          @close="deselectCanvas"
          @update="handleFloatingUpdate"
          @generate="handleFloatingGenerate"
          @tool="handleFloatingTool"
          @open-shot-editor="openShotEditor"
          @open-director="openDirectorDesk"
          @duplicate="handleDuplicateNode"
          @reuse="handleReuseNode"
          @save-asset="saveNodeAsAsset"
          @delete="handleDeleteNode"
          @agent-applied="handleTextAgentApplied" />

        <!-- Node Create Menu (on double click) -->
        <NodeCreateMenu :visible="createMenuVisible" :x="createMenuPos.x" :y="createMenuPos.y"
                        :node-types="canvas.NODE_TYPES" @select="onNodeTypeSelect" @close="closeCreateMenu" />

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

    <!-- Director Desk -->
    <div v-if="directorDeskVisible" class="director-overlay">
      <div :class="['director-shell', { fullscreen: directorFullscreen }]" @mousedown.stop>
        <div class="director-topbar">
          <strong>3D导演台</strong>
          <div class="director-view-switch">
            <button :class="{ active: directorView !== 'camera' }" @click="directorView = 'director'">导演视角</button>
            <button :class="{ active: directorView === 'camera' }" @click="directorView = 'camera'">机位视角</button>
          </div>
          <div class="director-window-actions">
            <button title="帮助" @click="ElMessage.info('可通过底部工具栏添加素体、全景、机位、比例和截图')">?</button>
            <button title="关闭" @click="closeDirectorDesk">×</button>
          </div>
        </div>

        <div class="director-workspace">
          <aside class="director-panel director-left">
            <div class="director-panel-title">场景</div>
            <div class="director-search">
              <input placeholder="请输入搜索内容" />
              <span>⌕</span>
            </div>
            <div class="director-element-list">
              <button :class="{ active: !directorSelectedElementId }" @click="directorSelectedElementId = ''">
                <span>▣</span>
                <strong>3D场景</strong>
                <small>{{ directorElements.length }} 个元素</small>
              </button>
              <button :class="{ active: directorSelectedElementId === directorCamera.id }" @click="directorSelectedElementId = directorCamera.id">
                <span>▰</span>
                <strong>{{ directorCamera.name }}</strong>
                <small>摄像机 · FOV {{ directorCamera.fov }}°</small>
              </button>
              <button v-for="item in directorElements" :key="item.id"
                      :class="{ active: directorSelectedElementId === item.id, hidden: item.hidden }"
                      @click="directorSelectedElementId = item.id">
                <span>{{ directorElementIcon(item.type, item.subType) }}</span>
                <strong>{{ item.name }}</strong>
                <small>{{ item.group || directorElementTypeLabel(item.type) }}</small>
              </button>
            </div>
            <div class="director-list-actions">
              <el-button size="small" @click="renameDirectorElement" :disabled="!selectedDirectorElement">重命名</el-button>
              <el-button size="small" @click="toggleDirectorElement" :disabled="!selectedDirectorElement">{{ selectedDirectorElement?.hidden ? '显示' : '隐藏' }}</el-button>
              <el-button size="small" @click="groupDirectorElement" :disabled="!selectedDirectorElement">打组</el-button>
              <el-button size="small" @click="ungroupDirectorElement" :disabled="!selectedDirectorElement">解组</el-button>
              <el-button size="small" type="danger" @click="deleteDirectorElement" :disabled="!selectedDirectorElement">删除</el-button>
            </div>
          </aside>

          <main class="director-stage-wrap" @mousedown.self="directorActiveMenu = ''">
            <div ref="directorStageRef" :class="['director-stage', 'view-' + directorView]" :style="directorStageStyle">
              <div class="director-orientation">
                <span></span><span></span><span></span><span></span>
                <button @click="resetDirectorView">重置视角</button>
              </div>
              <div v-if="directorAspect !== 'Auto'" :class="['director-camera-frame', 'aspect-' + directorAspect.replace(':','-')]">
                <span>{{ directorCamera.name }} · {{ directorAspect }} · FOV {{ directorCamera.fov }}°</span>
              </div>
              <button v-for="item in visibleDirectorElements" :key="item.id"
                      :class="['director-object', 'director-object-' + item.type, 'subtype-' + item.subType, { selected: directorSelectedElementId === item.id }]"
                      :style="directorObjectStyle(item)"
                      @click.stop="directorSelectedElementId = item.id"
                      @pointerdown.stop.prevent="startDirectorObjectTransform($event, item)">
                <span>{{ directorElementIcon(item.type, item.subType) }}</span>
                <strong v-if="directorScene.characterLabelVisible">{{ item.name }}</strong>
              </button>
              <div v-if="directorScene.groundVisible" class="director-ground" :style="{ opacity: directorScene.groundOpacity }"></div>
            </div>

            <div class="director-bottom-toolbar">
              <button :class="{ active: directorActiveMenu === 'mode' }" title="移动" @click="toggleDirectorMenu('mode')">⌁</button>
              <button :class="{ active: directorActiveMenu === 'model' }" title="添加模型" @click="toggleDirectorMenu('model')">♙</button>
              <button :class="{ active: directorActiveMenu === 'panorama' }" title="全景" @click="toggleDirectorMenu('panorama')">720</button>
              <button :class="{ active: directorActiveMenu === 'camera' }" title="机位预设" @click="toggleDirectorMenu('camera')">▰</button>
              <button :class="{ active: directorActiveMenu === 'aspect' }" title="比例" @click="toggleDirectorMenu('aspect')">▢</button>
              <button title="截图" @click="captureDirectorShot()">▣</button>
              <button title="AI识图导入" @click="openDirectorAiImport">▧</button>
              <button title="全屏" @click="directorFullscreen = !directorFullscreen">↗</button>

              <div v-if="directorActiveMenu === 'mode'" class="director-popover mode-popover">
                <button :class="{ active: directorMode === 'move' }" @click="setDirectorMode('move')"><span>⌁</span>移动 <kbd>V</kbd></button>
                <button :class="{ active: directorMode === 'rotate' }" @click="setDirectorMode('rotate')"><span>↻</span>旋转 <kbd>R</kbd></button>
                <button :class="{ active: directorMode === 'scale' }" @click="setDirectorMode('scale')"><span>↙</span>缩放 <kbd>S</kbd></button>
              </div>

              <div v-if="directorActiveMenu === 'model'" class="director-popover model-popover">
                <button @click="addDirectorElement('upload')"><span>⇧</span>本地上传</button>
                <button v-for="preset in directorHumanPresets" :key="preset.key" @click="addDirectorElement('human', preset.key)">
                  <span>♙</span>{{ preset.label }}
                </button>
                <button class="has-sub" @click.stop="directorActiveMenu = 'crowd'"><span>♙</span>群众（3x3） <b>›</b></button>
                <button class="has-sub" @click.stop="directorActiveMenu = 'geometry'"><span>◇</span>几何模型 <b>›</b></button>
              </div>

              <div v-if="directorActiveMenu === 'geometry'" class="director-popover geometry-popover">
                <button @click="addDirectorElement('upload')"><span>⇧</span>上传文件</button>
                <button v-for="preset in directorGeometryPresets" :key="preset.key" @click="addDirectorElement('geometry', preset.key)">
                  <span>{{ directorElementIcon('geometry', preset.key) }}</span>{{ preset.label }}
                </button>
              </div>

              <div v-if="directorActiveMenu === 'crowd'" class="director-popover crowd-popover">
                <div class="popover-title">添加群众阵列 <small>共{{ directorCrowdForm.rows * directorCrowdForm.cols }}人</small></div>
                <label>行数 <input v-model.number="directorCrowdForm.rows" type="number" min="1" max="20" /></label>
                <label>列数 <input v-model.number="directorCrowdForm.cols" type="number" min="1" max="20" /></label>
                <label>间距 <input v-model.number="directorCrowdForm.spacing" type="number" min="0.2" max="10" step="0.1" /></label>
                <div class="popover-actions">
                  <button @click="directorActiveMenu = 'model'">取消</button>
                  <button class="primary" @click="addDirectorCrowd">添加</button>
                </div>
              </div>

              <div v-if="directorActiveMenu === 'panorama'" class="director-popover panorama-popover">
                <button @click="setDirectorPanorama('本地上传')"><span>⇧</span>本地上传</button>
                <button @click="setDirectorPanorama('历史记录')"><span>◷</span>历史记录</button>
                <button @click="setDirectorPanorama('AI生成')"><span>720</span>AI生成</button>
              </div>

              <div v-if="directorActiveMenu === 'camera'" class="director-popover camera-popover">
                <div class="popover-title">选择机位视角</div>
                <button v-for="preset in directorCameraPresets" :key="preset.key" @click="applyDirectorCameraPreset(preset.key)">
                  <span>▰</span>{{ preset.label }}
                </button>
              </div>

              <div v-if="directorActiveMenu === 'aspect'" class="director-popover aspect-popover">
                <div class="popover-title">比例</div>
                <button v-for="aspect in directorAspectOptions" :key="aspect" :class="{ active: directorAspect === aspect }" @click="setDirectorAspect(aspect)">
                  <span>{{ aspect === 'Auto' ? '▢' : aspect }}</span>{{ aspect }}
                </button>
              </div>
            </div>
          </main>

          <aside class="director-panel director-right">
            <template v-if="directorSelectedElementId === directorCamera.id">
              <div class="director-panel-title">摄像机</div>
              <div class="director-panel-tabs"><button class="active">属性</button><button>摄像机截图</button></div>
              <div class="camera-preview">
                <strong>FOV {{ directorCamera.fov }}°</strong>
                <span>♙</span>
                <button>↗</button>
              </div>
              <div class="director-form">
                <label>名称</label>
                <el-input v-model="directorCamera.name" size="small" @change="persistDirectorDesk" />
                <label>切换机位</label>
                <el-select :model-value="directorCamera.preset || 'current'" size="small" @change="applyDirectorCameraPreset">
                  <el-option v-for="preset in directorCameraPresets" :key="preset.key" :label="preset.label" :value="preset.key" />
                </el-select>
                <label>位置</label>
                <div class="director-number-row">
                  <el-input-number v-model="directorCamera.x" :step="0.1" size="small" @change="persistDirectorDesk" />
                  <el-input-number v-model="directorCamera.y" :step="0.1" size="small" @change="persistDirectorDesk" />
                  <el-input-number v-model="directorCamera.z" :step="0.1" size="small" @change="persistDirectorDesk" />
                </div>
                <label>注视目标</label>
                <el-select v-model="directorCamera.focus" size="small" @change="persistDirectorDesk">
                  <el-option label="手动坐标" value="" />
                  <el-option v-for="item in directorElements" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
                <label>注视坐标</label>
                <div class="director-number-row">
                  <el-input-number v-model="directorCamera.lookAtX" :step="0.1" size="small" @change="persistDirectorDesk" />
                  <el-input-number v-model="directorCamera.lookAtY" :step="0.1" size="small" @change="persistDirectorDesk" />
                  <el-input-number v-model="directorCamera.lookAtZ" :step="0.1" size="small" @change="persistDirectorDesk" />
                </div>
                <label>视野角度（FOV）</label>
                <el-slider v-model="directorCamera.fov" :min="10" :max="120" @change="persistDirectorDesk" />
              </div>
              <div class="director-panel-title">相机截图</div>
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
                <div v-if="!directorShots.length" class="director-empty">暂无截图，点击底部相机按钮创建。</div>
              </div>
            </template>

            <template v-else-if="selectedDirectorElement">
              <div class="director-panel-title">{{ selectedDirectorElement.type === 'human' ? '角色' : '元素' }}</div>
              <div class="director-form">
                <label>名称</label>
                <el-input v-model="selectedDirectorElement.name" size="small" @change="persistDirectorDesk" />
                <label>类型</label>
                <el-input :model-value="directorElementTypeLabel(selectedDirectorElement.type, selectedDirectorElement.subType)" size="small" disabled />
                <label>位置 X/Y/Z</label>
                <div class="director-number-row">
                  <el-input-number v-model="selectedDirectorElement.x" :step="1" size="small" @change="persistDirectorDesk" />
                  <el-input-number v-model="selectedDirectorElement.y" :step="1" size="small" @change="persistDirectorDesk" />
                  <el-input-number v-model="selectedDirectorElement.z" :step="1" size="small" @change="persistDirectorDesk" />
                </div>
                <label>旋转</label>
                <el-slider v-model="selectedDirectorElement.rotate" :min="-180" :max="180" @change="persistDirectorDesk" />
                <label>缩放</label>
                <el-slider v-model="selectedDirectorElement.scale" :min="40" :max="180" @change="persistDirectorDesk" />
                <label>颜色</label>
                <el-color-picker v-model="selectedDirectorElement.color" size="small" @change="persistDirectorDesk" />
              </div>
            </template>

            <template v-else>
              <div class="director-panel-title">3D场景</div>
              <div class="director-form">
                <label>场景缩放</label>
                <el-slider v-model="directorScene.zoom" :min="50" :max="500" @change="persistDirectorDesk" />
                <label>场景平移</label>
                <div class="director-number-row">
                  <el-input-number v-model="directorScene.pan.x" :step="1" size="small" @change="persistDirectorDesk" />
                  <el-input-number v-model="directorScene.pan.y" :step="1" size="small" @change="persistDirectorDesk" />
                  <el-input-number v-model="directorScene.pan.z" :step="1" size="small" @change="persistDirectorDesk" />
                </div>
                <label>场景旋转</label>
                <div class="director-number-row">
                  <el-input-number v-model="directorScene.rotation.x" :step="1" size="small" @change="persistDirectorDesk" />
                  <el-input-number v-model="directorScene.rotation.y" :step="1" size="small" @change="persistDirectorDesk" />
                  <el-input-number v-model="directorScene.rotation.z" :step="1" size="small" @change="persistDirectorDesk" />
                </div>
                <label>全景背景</label>
                <div class="director-empty compact">{{ directorScene.panoramaStatus }}</div>
                <label>天空颜色</label>
                <el-color-picker v-model="directorScene.skyColor" size="small" @change="persistDirectorDesk" />
                <label>全景球</label>
                <el-slider v-model="directorScene.panoramaRotation" :min="0" :max="360" @change="persistDirectorDesk" />
                <el-slider v-model="directorScene.panoramaRadius" :min="10" :max="120" @change="persistDirectorDesk" />
                <label><input v-model="directorScene.characterLabelVisible" type="checkbox" @change="persistDirectorDesk" /> 角色标签</label>
                <label><input v-model="directorGridSnap" type="checkbox" @change="persistDirectorDesk" /> 网格吸附</label>
                <label><input v-model="directorScene.groundVisible" type="checkbox" @change="persistDirectorDesk" /> 地面</label>
                <el-slider v-model="directorScene.groundOpacity" :min="0" :max="1" :step="0.05" @change="persistDirectorDesk" />
                <el-slider v-model="directorScene.groundHeight" :min="-20" :max="20" @change="persistDirectorDesk" />
              </div>
            </template>
          </aside>
        </div>
      </div>

      <div v-if="directorAiImport.visible" class="director-ai-dialog" @mousedown.stop>
        <div class="director-ai-card">
          <header><strong>AI 识图导入</strong><button @click="directorAiImport.visible = false">×</button></header>
          <div class="director-ai-tabs">
            <button :class="{ active: directorAiImport.tab === 'local' }" @click="directorAiImport.tab = 'local'">本地上传</button>
            <button :class="{ active: directorAiImport.tab === 'history' }" @click="directorAiImport.tab = 'history'">历史记录</button>
          </div>
          <label class="ai-upload-box">
            <input type="file" accept="image/*" @change="onDirectorAiFileChange" />
            <strong>{{ directorAiImport.fileName || '点击上传图片 或 拖拽本地图片至此上传' }}</strong>
            <span>上传后画布将新建一个图片节点并自动替换当前图源</span>
          </label>
          <div class="director-ai-mode">
            <label :class="{ active: directorAiImport.mode === 'insert' }">
              <input v-model="directorAiImport.mode" type="radio" value="insert" />
              <strong>插入当前导演台</strong>
              <span>作为站位参考层插入，不覆盖当前全景、角色和机位</span>
            </label>
            <label :class="{ active: directorAiImport.mode === 'overwrite' }">
              <input v-model="directorAiImport.mode" type="radio" value="overwrite" />
              <strong>覆盖当前导演台</strong>
              <span>覆盖当前全景、角色和机位</span>
            </label>
          </div>
          <footer>
            <span>退出不会中断生成过程，全景图生成成功后，会自动加载到背景</span>
            <button :disabled="directorAiImport.running" @click="runDirectorAiImport">
              {{ directorAiImport.running ? '导入中...' : '识图导入场景' }}
            </button>
          </footer>
        </div>
      </div>
    </div>
  </div>

  <!-- Workspace 资产选择器 -->
  <WorkspaceAssetPicker
    v-model="showAssetPicker"
    :project-id="state.projectId.value"
    @applied="onAssetApplied"
  />
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, FolderOpened } from '@element-plus/icons-vue'
import { canvasApi } from '@/api/canvas'
import { generationApi } from '@/api/generation'
import { canvasFeatures } from '@/config/canvasFeatures'
import { useCanvasUIState } from './canvas/composables/useCanvasUIState'
import { useCanvasState } from './canvas/composables/useCanvasState'
import { useCanvasNodes } from './canvas/composables/useCanvasNodes'
import NodeCreateMenu from './canvas/components/NodeCreateMenu.vue'
import NodeFloatingEditor from './canvas/components/NodeFloatingEditor.vue'
import ShotTableEditor from './canvas/components/ShotTableEditor.vue'
import WorkspaceAssetPicker from './canvas/components/WorkspaceAssetPicker.vue'
import VueFlowCanvasStage from './canvas/flow/VueFlowCanvasStage.vue'
import { computeFloatingEditorPosition } from './canvas/utils/floatingEditorPosition'
import { shouldSelectNode } from './canvas/utils/nodeEditorData'
import { getNodeMeta, getNodeSize } from './canvas/nodeRegistry'

const route = useRoute()
const router = useRouter()
const state = useCanvasState()
const showAssetPicker = ref(false)
const canvas = useCanvasNodes(state.projectId)

const canvasAreaRef = ref(null)
const flowStageRef = ref(null)
const directorStageRef = ref(null)
const selectedNodeForPanel = ref(null)
const leftPanelCollapsed = ref(false)
const shotEditorVisible = ref(false)
const shotEditorNode = ref(null)
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
const directorActiveMenu = ref('')
const directorFullscreen = ref(false)
const directorAspectOptions = ['Auto', '21:9', '16:9', '4:3', '1:1', '3:4', '9:16']
const directorHumanPresets = [
  { key: 'male', label: '男性素体' },
  { key: 'female', label: '女性素体' },
  { key: 'broad', label: '宽厚素体' },
  { key: 'strong', label: '健壮素体' },
  { key: 'slim', label: '纤细素体' },
  { key: 'teen', label: '少年素体' },
  { key: 'child', label: '儿童素体' },
  { key: 'chibi', label: '二头身' }
]
const directorGeometryPresets = [
  { key: 'cube', label: '立方体' },
  { key: 'sphere', label: '球体' },
  { key: 'cylinder', label: '圆柱体' },
  { key: 'torus', label: '环状体' },
  { key: 'cone', label: '圆锥' },
  { key: 'pyramid', label: '棱锥' }
]
const directorCameraPresets = [
  { key: 'current', label: '当前视角' },
  { key: 'front_medium', label: '正面中景' },
  { key: 'front_close', label: '正面特写' },
  { key: 'front_wide', label: '正面全景' },
  { key: 'side_follow', label: '侧面跟拍' },
  { key: 'side_near', label: '侧面近景' }
]
const directorCrowdForm = reactive({ rows: 3, cols: 3, spacing: 1.2 })
const directorScene = reactive({
  zoom: 300,
  pan: { x: 0, y: 0, z: 0 },
  rotation: { x: 0, y: 0, z: 0 },
  skyColor: '#060608',
  panoramaAssetId: '',
  panoramaStatus: '未连接全景图',
  panoramaRotation: 0,
  panoramaRadius: 60,
  characterLabelVisible: true,
  groundVisible: true,
  groundOpacity: 0.4,
  groundHeight: 0
})
const directorCamera = reactive({
  id: 'camera_1',
  name: '机位1',
  fov: 50,
  focus: '',
  x: 0,
  y: 2.2,
  z: 10,
  lookAtMode: 'manual',
  lookAtX: 0,
  lookAtY: 1.2,
  lookAtZ: 0,
  preset: 'current'
})
const directorAiImport = reactive({
  visible: false,
  tab: 'local',
  mode: 'insert',
  fileName: '',
  running: false,
  historySelected: ''
})
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
const canvasViewport = ref({ width: 1280, height: 720 })
let canvasResizeObserver = null

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

const floatingEditorPosition = computed(() => {
  const node = selectedNodeForPanel.value
  if (!node) return { placement: 'right', x: 16, y: 16 }
  const scale = state.zoomLevel.value / 100
  return computeFloatingEditorPosition({
    nodeRect: {
      left: (node.x || 0) * scale + state.panOffset.value.x,
      top: (node.y || 0) * scale + state.panOffset.value.y,
      width: nodeW(node) * scale,
      height: nodeH(node) * scale
    },
    viewport: canvasViewport.value,
    panel: { width: floatingEditorWidth.value, height: Math.min(560, Math.max(360, canvasViewport.value.height - 32)) }
  })
})

const floatingEditorWidth = computed(() => {
  const node = selectedNodeForPanel.value
  if (!node) return 440
  const scale = state.zoomLevel.value / 100
  const nodeLeft = (node.x || 0) * scale + state.panOffset.value.x
  const nodeRight = nodeLeft + nodeW(node) * scale
  const rightSpace = canvasViewport.value.width - nodeRight - 34
  const leftSpace = nodeLeft - 34
  const available = Math.max(rightSpace, leftSpace)
  return Math.max(360, Math.min(440, available))
})

const floatingEditorStyle = computed(() => ({
  left: `${floatingEditorPosition.value.x}px`,
  top: `${floatingEditorPosition.value.y}px`,
  width: `${floatingEditorWidth.value}px`
}))

const floatingPlacement = computed(() => floatingEditorPosition.value.placement)

// Selected node for property panel
watch(() => state.selectedNodeId.value, (id) => {
  if (id) {
    selectedNodeForPanel.value = findNodeByRef(id)
    if (canvasViewport.value.width < 980) leftPanelCollapsed.value = true
  } else {
    selectedNodeForPanel.value = null
    leftPanelCollapsed.value = false
  }
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

const directorStageStyle = computed(() => ({
  backgroundColor: directorScene.skyColor,
  '--director-scene-zoom': directorScene.zoom / 300,
  '--director-ground-offset': `${directorScene.groundHeight}px`
}))

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
  // 路由切换后可能残留上一页 teleported 的 Element Plus 遮罩，叠在侧栏上
  document.querySelectorAll('body > .el-overlay').forEach((el) => el.remove())

  window.addEventListener('keydown', onCanvasKeydown)
  if (canvasAreaRef.value) {
    const updateViewport = () => {
      const rect = canvasAreaRef.value?.getBoundingClientRect()
      if (rect) canvasViewport.value = { width: rect.width, height: rect.height }
    }
    updateViewport()
    canvasResizeObserver = new ResizeObserver(updateViewport)
    canvasResizeObserver.observe(canvasAreaRef.value)
  }
  // Require explicit canvas ID — redirect if missing
  if (!state.projectId.value) {
    router.replace('/canvas-projects')
    return
  }
  try {
    const res = await canvasApi.getProject(state.projectId.value)
    if (res.data) {
      const p = res.data
      state.projectName.value = p.name || state.projectName.value
      state.projectStatus.value = p.status || state.projectStatus.value
      // Check upstream changes
      try {
        const diffRes = await canvasApi.getSourceDiff(state.projectId.value)
        if (diffRes?.data) state.upstreamChanges.value = diffRes.data
      } catch { /* diff is optional */ }
    }
    await canvas.loadNodes()
    await canvas.loadWorkflows()
    state.markSaved()
  } catch (e) {
    if (e?.response?.status === 404 || e?.response?.data?.code === 40002) {
      state.canvasNotFound.value = true
    } else {
      ElMessage.warning('画布加载失败，请刷新重试')
    }
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', onCanvasKeydown)
  canvasResizeObserver?.disconnect()
  cleanupDirectorTransformListeners()
  cleanupDocumentListeners()
})

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

function onAssetApplied({ asset, result }) {
  showAssetPicker.value = false
  ElMessage.success(result?.changeSummary || `「${asset.name}」已应用到画布`)
  // Refresh canvas state to pick up style/config changes
  if (state.projectId.value) {
    state.fetchProject(state.projectId.value)
  }
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

async function handleFloatingUpdate({ node, updates }) {
  await handleNodeUpdate(node, updates)
}

async function handleFloatingGenerate({ node, data, action }) {
  try {
    const id = nodeKey(node)
    await canvas.updateNode(id, { data, status: 'ready' })
    const current = findNodeByRef(id) || node
    await runNodeTask(current, action)
    state.markSaved()
  } catch (e) {
    ElMessage.error('生成任务提交失败')
  }
}

async function handleFloatingTool({ node, action }) {
  if (action.creates) {
    const created = await createDownstreamNode(node, action.creates)
    if (created) ElMessage.success(`${action.label}节点已创建`)
    return
  }
  await runNodeTask(node, action)
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
  await saveNodeAsAsset(node)
  state.closeContextMenu()
}

async function saveNodeAsAsset(node) {
  await canvas.updateNode(nodeKey(node), {
    data: { ...readNodeData(node), saved_as_asset: true, asset_name: node.name || nodeLabel(node.type) }
  }).catch(() => {})
  ElMessage.success('已保存为资产')
}

async function handleReuseNode(node) {
  const copy = await canvas.duplicateNode(nodeKey(node))
  if (!copy) return
  const related = canvas.connections.value.filter((conn) => {
    const source = conn.source_node_id || conn.sourceNodeId || conn.source
    const target = conn.target_node_id || conn.targetNodeId || conn.target
    return findNodeByRef(source) === node || findNodeByRef(target) === node
  })
  for (const conn of related) {
    const sourceRef = conn.source_node_id || conn.sourceNodeId || conn.source
    const targetRef = conn.target_node_id || conn.targetNodeId || conn.target
    const source = findNodeByRef(sourceRef) === node ? copy : findNodeByRef(sourceRef)
    const target = findNodeByRef(targetRef) === node ? copy : findNodeByRef(targetRef)
    if (source && target && nodeKey(source) !== nodeKey(target)) {
      await canvas.connectNodes(nodeKey(source), nodeKey(target)).catch(() => {})
    }
  }
  state.selectNode(nodeKey(copy))
  state.markSaved()
  ElMessage.success('已复用节点并保留连线')
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
    { id: 'human_1', type: 'human', subType: 'male', name: '角色A', group: '角色', x: 45, y: 48, z: 0, rotate: 0, scale: 100, color: '#8fb4cc', hidden: false },
    { id: 'geometry_1', type: 'geometry', subType: 'cylinder', name: '圆柱体1', group: '场景', x: 56, y: 60, z: 0, rotate: 0, scale: 96, color: '#6b7b75', hidden: false }
  ]
}

function defaultDirectorScene() {
  return {
    zoom: 300,
    pan: { x: 0, y: 0, z: 0 },
    rotation: { x: 0, y: 0, z: 0 },
    skyColor: '#060608',
    panoramaAssetId: '',
    panoramaStatus: '未连接全景图',
    panoramaRotation: 0,
    panoramaRadius: 60,
    characterLabelVisible: true,
    groundVisible: true,
    groundOpacity: 0.4,
    groundHeight: 0
  }
}

function defaultDirectorCamera() {
  return {
    id: 'camera_1',
    name: '机位1',
    fov: 50,
    focus: '',
    x: 0,
    y: 2.2,
    z: 10,
    lookAtMode: 'manual',
    lookAtX: 0,
    lookAtY: 1.2,
    lookAtZ: 0,
    preset: 'current'
  }
}

function cloneDirectorElements(items) {
  const source = Array.isArray(items) && items.length ? items : defaultDirectorElements()
  return source.map(item => ({ ...item }))
}

function cloneDirectorScene(scene) {
  const base = defaultDirectorScene()
  return {
    ...base,
    ...(scene || {}),
    pan: { ...base.pan, ...(scene?.pan || {}) },
    rotation: { ...base.rotation, ...(scene?.rotation || {}) }
  }
}

function getDirectorData(node) {
  const data = readNodeData(node)
  const director = data.director || {}
  return {
    elements: cloneDirectorElements(director.elements),
    shots: Array.isArray(director.shots) ? director.shots : [],
    scene: cloneDirectorScene(director.scene),
    camera: { ...defaultDirectorCamera(), ...(director.camera || {}) },
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
  Object.assign(directorScene, data.scene)
  directorGridSnap.value = data.scene.gridSnap ?? true
  Object.assign(directorCamera, data.camera)
  directorSelectedElementId.value = directorElements.value[0]?.id || ''
  directorSelectedShotId.value = directorShots.value.at(-1)?.id || ''
  directorActiveMenu.value = ''
}

const features = canvasFeatures()

function openDirectorDesk(node) {
  // R2: 当 DIRECTOR_V2 开启时，导航到独立 Three.js 导演台路由
  if (features.directorV2) {
    const ui = useCanvasUIState()
    ui.write({ activeShotUnitId: node.shotUnitId, activeNodeId: nodeKey(node), activeTab: 'director' })
    router.push(`/canvas/${state.projectId.value}/shot-units/${node.shotUnitId || 'draft'}/director`)
    return
  }
  // 旧 DOM 导演台（R2 前保持兼容）
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
          scene: {
            ...directorScene,
            pan: { ...directorScene.pan },
            rotation: { ...directorScene.rotation },
            gridSnap: directorGridSnap.value
          },
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

function directorElementIcon(type, subType = '') {
  if (type === 'human') return '♙'
  if (type === 'crowd') return '♙'
  if (type === 'upload') return '⇧'
  return {
    cube: '□',
    sphere: '◯',
    cylinder: '▯',
    torus: '◎',
    cone: '△',
    pyramid: '◇'
  }[subType] || '◇'
}

function directorElementTypeLabel(type, subType = '') {
  if (type === 'human') {
    return directorHumanPresets.find(item => item.key === subType)?.label || '人体素模'
  }
  if (type === 'geometry') {
    return directorGeometryPresets.find(item => item.key === subType)?.label || '基础几何体'
  }
  return { crowd: '群众阵列', upload: '本地上传模型/图片' }[type] || '元素'
}

function addDirectorElement(type, subType = '') {
  const presetLabel = type === 'human'
    ? directorHumanPresets.find(item => item.key === subType)?.label
    : type === 'geometry'
      ? directorGeometryPresets.find(item => item.key === subType)?.label
      : ''
  const labels = { human: presetLabel || '人体素模', geometry: presetLabel || '基础几何', crowd: '群众阵列', upload: '本地上传' }
  const index = directorElements.value.filter(item => item.type === type).length + 1
  const item = {
    id: `${type}_${Date.now()}`,
    type,
    subType,
    name: `${labels[type] || '元素'} ${index}`,
    group: type === 'human' ? '角色' : type === 'crowd' ? '群演' : '场景',
    x: Math.min(82, 24 + index * 12),
    y: Math.min(82, 32 + index * 9),
    z: 0,
    rotate: 0,
    scale: type === 'crowd' ? 82 : 100,
    color: type === 'human' ? '#8fb4cc' : type === 'geometry' ? '#6b7b75' : '#334155',
    hidden: false
  }
  directorElements.value.push(item)
  directorSelectedElementId.value = item.id
  directorActiveMenu.value = ''
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
  directorActiveMenu.value = ''
}

function toggleDirectorMenu(menu) {
  directorActiveMenu.value = directorActiveMenu.value === menu ? '' : menu
}

function setDirectorMode(mode) {
  directorMode.value = mode
  directorActiveMenu.value = ''
}

function setDirectorAspect(aspect) {
  directorAspect.value = aspect
  directorActiveMenu.value = ''
  persistDirectorDesk()
}

function addDirectorCrowd() {
  const rows = clampDirectorValue(Number(directorCrowdForm.rows || 3), 1, 20)
  const cols = clampDirectorValue(Number(directorCrowdForm.cols || 3), 1, 20)
  const spacing = clampDirectorValue(Number(directorCrowdForm.spacing || 1.2), 0.2, 10)
  const count = rows * cols
  const index = directorElements.value.filter(item => item.type === 'crowd').length + 1
  const item = {
    id: `crowd_${Date.now()}`,
    type: 'crowd',
    subType: 'array',
    name: `群众（${rows}x${cols}） ${index}`,
    group: '群演',
    rows,
    cols,
    spacing,
    count,
    x: 52,
    y: 62,
    z: 0,
    rotate: 0,
    scale: 88,
    color: '#334155',
    hidden: false
  }
  directorElements.value.push(item)
  directorSelectedElementId.value = item.id
  directorActiveMenu.value = ''
  persistDirectorDesk()
}

function applyDirectorCameraPreset(presetKey) {
  const preset = directorCameraPresets.find(item => item.key === presetKey)
  directorCamera.preset = presetKey
  directorCamera.name = preset?.label || '机位1'
  const subject = selectedDirectorElement.value || directorElements.value.find(item => item.type === 'human') || directorElements.value[0]
  directorCamera.focus = subject?.id || ''
  if (presetKey === 'front_close') {
    directorCamera.fov = 35
    directorCamera.z = 4.2
  } else if (presetKey === 'front_wide') {
    directorCamera.fov = 68
    directorCamera.z = 12
  } else if (presetKey === 'side_follow') {
    directorCamera.fov = 50
    directorCamera.x = 4.5
    directorCamera.z = 6.8
  } else if (presetKey === 'side_near') {
    directorCamera.fov = 42
    directorCamera.x = 3.2
    directorCamera.z = 4.6
  } else {
    directorCamera.fov = 50
    directorCamera.x = 0
    directorCamera.z = 8
  }
  directorView.value = 'camera'
  directorSelectedElementId.value = directorCamera.id
  directorActiveMenu.value = ''
  persistDirectorDesk()
}

function setDirectorPanorama(action) {
  directorScene.panoramaStatus = action === 'AI生成'
    ? 'AI 全景生成中，完成后自动加载到背景'
    : `${action}已选择，等待接入资源`
  directorActiveMenu.value = ''
  persistDirectorDesk()
  ElMessage.success(`已选择${action}`)
}

function openDirectorAiImport() {
  directorActiveMenu.value = ''
  directorAiImport.visible = true
}

function onDirectorAiFileChange(e) {
  const file = e.target.files?.[0]
  directorAiImport.fileName = file?.name || ''
}

async function runDirectorAiImport() {
  directorAiImport.running = true
  // TODO: 当前为 mock 实现 + 后端桩调用。待接入真实 AI 识图服务后替换。
  try {
    await canvasApi.aiImportDirectorDesk(state.projectId.value, nodeKey(directorDeskNode.value), {
      source_asset_id: directorAiImport.fileName || `local_${Date.now()}`,
      mode: directorAiImport.mode
    })
  } catch {
    // 后端未就绪时使用本地模拟（700ms 延迟模拟 AI 处理）
    await new Promise(resolve => window.setTimeout(resolve, 700))
  }
  directorScene.panoramaStatus = directorAiImport.mode === 'overwrite'
    ? 'AI 识图已覆盖当前全景背景'
    : 'AI 识图已插入为站位参考层'
  directorScene.panoramaAssetId = `ai_import_${Date.now()}`
  if (directorAiImport.mode === 'overwrite') {
    directorElements.value = cloneDirectorElements(defaultDirectorElements())
    directorSelectedElementId.value = directorElements.value[0]?.id || ''
  }
  directorAiImport.running = false
  directorAiImport.visible = false
  await persistDirectorDesk()
  ElMessage.success('AI 识图导入完成')
}

function directorObjectStyle(item) {
  const scale = Number(item.scale || 100) / 100
  const x = Math.max(8, Math.min(88, Number(item.x || 50)))
  const y = Math.max(10, Math.min(84, Number(item.y || 50)))
  return {
    left: `${x}%`,
    top: `${y}%`,
    transform: `translate(-50%, -50%) rotate(${item.rotate || 0}deg) scale(${scale})`,
    backgroundColor: item.color || undefined
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
  if (aspect === '21:9') return { width: 840, height: 360 }
  if (aspect === '9:16') return { width: 360, height: 640 }
  if (aspect === '1:1') return { width: 520, height: 520 }
  if (aspect === '4:3') return { width: 560, height: 420 }
  if (aspect === '3:4') return { width: 420, height: 560 }
  return { width: 640, height: 360 }
}

function escapeSvgText(text) {
  return String(text || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function directorElementFill(type, color) {
  return color || { human: '#8fb4cc', geometry: '#6b7b75', crowd: '#334155', upload: '#c2410c' }[type] || '#475569'
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
    const fill = directorElementFill(item.type, item.color)
    return `
      <g transform="translate(${x} ${y}) rotate(${Number(item.rotate || 0)}) scale(${scale})">
        <rect x="${-baseW / 2}" y="${-baseH / 2}" width="${baseW}" height="${baseH}" rx="10" fill="${fill}" stroke="#cbd5e1" stroke-width="2"/>
        <text x="0" y="5" text-anchor="middle" font-size="14" fill="#f8fafc" font-family="Arial, sans-serif">${escapeSvgText(directorElementIcon(item.type, item.subType))}</text>
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
    camera_name: directorCamera.name,
    camera_preset: directorCamera.preset,
    created_at: new Date().toLocaleString('zh-CN', { hour12: false }),
    elements: directorElements.value.filter(item => !item.hidden).map(item => ({
      id: item.id,
      name: item.name,
      type: item.type,
      subType: item.subType,
      color: item.color,
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
  // 同步到后端（异步，不阻塞用户操作）
  canvasApi.captureDirectorDesk(state.projectId.value, nodeKey(directorDeskNode.value), {
    aspect_ratio: shot.aspect
  }).catch(() => { /* 后端未就绪时静默降级 */ })
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
    // 同步到后端（异步，不阻塞用户操作）
    canvasApi.sendDirectorScreenshotToCanvas(state.projectId.value, nodeKey(source), targetShot.id, {
      target_position: { x: created.x, y: created.y }
    }).catch(() => { /* 后端未就绪时静默降级 */ })
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
  createMenuPos.value = { x: e.clientX, y: e.clientY }
  createMenuVisible.value = true
}

function selectNode(node) {
  selectedConnectionId.value = null
  const id = nodeKey(node)
  if (shouldSelectNode(state.selectedNodeId.value, id)) state.selectNode(id)
}

function selectConnection(connId) {
  selectedConnectionId.value = String(connId)
  state.deselectAll()
}

function deselectCanvas() {
  selectedConnectionId.value = null
  state.deselectAll()
  state.closeContextMenu()
  closeCreateMenu()
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
  // 弹窗/对话框打开时不处理画布快捷键
  if (document.querySelector('.el-overlay.is-message-box, .el-dialog:not([style*="display: none"])')) return
  if (e.key === 'Escape' && createMenuVisible.value) {
    closeCreateMenu()
    return
  }
  if (directorDeskVisible.value) {
    const key = e.key.toLowerCase()
    if (key === 'escape') {
      if (directorAiImport.visible) directorAiImport.visible = false
      else if (directorActiveMenu.value) directorActiveMenu.value = ''
      else closeDirectorDesk()
      return
    }
    if (key === 'v') directorMode.value = 'move'
    if (key === 'r') directorMode.value = 'rotate'
    if (key === 's') directorMode.value = 'scale'
    if (key === 'x') {
      directorGridSnap.value = !directorGridSnap.value
      persistDirectorDesk()
    }
    if (key === 't') directorView.value = 'top'
    if (key === 'y') directorView.value = 'front'
    if (key === 'q') resetDirectorView()
    if ((e.ctrlKey || e.metaKey) && key === 'g' && e.shiftKey) {
      e.preventDefault()
      ungroupDirectorElement()
    } else if ((e.ctrlKey || e.metaKey) && key === 'g') {
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
  closeCreateMenu()
  const point = screenToCanvas(createMenuPos.value.x, createMenuPos.value.y)
  handleAddNode(type, point.x, point.y).then(async (node) => {
    if (node && pendingConnectionSource.value) {
      await canvas.connectNodes(nodeKey(pendingConnectionSource.value), nodeKey(node)).catch(() => {})
      pendingConnectionSource.value = null
      state.markSaved()
    }
  })
}

function closeCreateMenu() {
  if (!createMenuVisible.value) return
  createMenuVisible.value = false
  pendingConnectionSource.value = null
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
    if (shouldSelectNode(state.selectedNodeId.value, nodeId)) state.selectNode(nodeId)
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
  if (shouldSelectNode(state.selectedNodeId.value, nodeId)) state.selectNode(nodeId)
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

// ===== Generate downstream =====
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
    ...(action.parameters || {}),
    node_id: nodeKey(node),
    prompt: action.parameters?.prompt ?? getNodePrompt(node),
    action: action.label,
    model_id: action.modelId || action.parameters?.model_id || defaultModelForTask(taskType)
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
  if (flowStageRef.value?.screenToCanvas) {
    try {
      const point = flowStageRef.value.screenToCanvas(clientX, clientY)
      if (point && Number.isFinite(point.x) && Number.isFinite(point.y)) return point
    } catch { /* fall through */ }
  }
  const rect = canvasAreaRef.value?.getBoundingClientRect()
  const scale = state.zoomLevel.value / 100
  if (!rect || !scale) return { x: 0, y: 0 }
  return {
    x: (clientX - rect.left - state.panOffset.value.x) / scale,
    y: (clientY - rect.top - state.panOffset.value.y) / scale
  }
}

async function onFlowConnect({ source, target, sourcePort = 'out', targetPort = 'in' }) {
  if (!source || !target || source === target) return
  const alreadyConnected = canvas.connections.value.some(c => {
    const sId = c.source_node_id || c.sourceNodeId || c.source
    const tId = c.target_node_id || c.targetNodeId || c.target
    const sNode = findNodeByRef(sId)
    const tNode = findNodeByRef(tId)
    if (!sNode || !tNode) return false
    return nodeKey(sNode) === source && nodeKey(tNode) === target
  })
  if (alreadyConnected) {
    ElMessage.warning('这两个节点已经连接')
    return
  }
  try {
    await canvas.connectNodes(source, target, sourcePort, targetPort)
    ElMessage.success('节点已连接')
    state.markSaved()
  } catch (e) {
    ElMessage.error('连线失败: ' + (e?.message || e || '未知错误'))
  }
}

function onFlowSelectEdge(edgeId) {
  selectConnection(edgeId)
}

async function onFlowNodesMoved(positions = []) {
  if (!positions.length) return
  positions.forEach((p) => {
    const node = findNodeByRef(p.node_id)
    if (!node) return
    node.x = p.x
    node.y = p.y
  })
  await canvas.updateNodePositions(positions).catch(() => {})
  state.markSaved()
}

function onFlowViewport(vp) {
  if (!vp) return
  state.zoomLevel.value = vp.zoom || 100
  state.panOffset.value = { x: vp.x || 0, y: vp.y || 0 }
}

function onFlowNodeContext(e, raw) {
  state.openContextMenu(e, nodeKey(raw))
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
        scene: defaultDirectorScene(),
        camera: defaultDirectorCamera(),
        aspect: '16:9'
      }
    }
  }
  if (type === 'prompt') {
    return { prompt: '', tags: '', source: 'canvas' }
  }
  if (type === 'character') {
    return {
      name: '',
      appearance: '',
      personality: '',
      prompt: '角色名称、年龄、身份、服饰、发型、性格、三视图要求',
      reference_url: '',
      consistency_level: 'L1',
    }
  }
  if (type === 'scene') {
    return {
      name: '',
      environment: '',
      atmosphere: '',
      prompt: '场景名称、时代、空间结构、光线、色彩、镜头氛围',
      reference_url: '',
      consistency_level: 'L1',
    }
  }
  if (type === 'model') {
    return { model_id: 'seedream-5.0', capability: 'image', notes: '', source: 'canvas' }
  }
  if (type === 'output') {
    return { title: '', format: 'package', notes: '', source: 'canvas' }
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
  return getNodeSize(type).width
}

function nodeIcon(type) {
  return getNodeMeta(type).icon
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

</script>

<style scoped>
.canvas-page { position:relative; display:flex; flex-direction:column; height:100vh; min-height:0; overflow:hidden; background:#0f172a; color:#e0e0e0; }
.canvas-back-btn {
  --el-button-text-color: #94a3b8;
  --el-button-hover-text-color: #e2e8f0;
  --el-button-hover-bg-color: rgba(148, 163, 184, .12);
  --el-button-active-text-color: #c7d2fe;
  --el-button-active-bg-color: rgba(129, 140, 248, .16);
  color: #94a3b8 !important;
  margin-right: 4px;
}
.canvas-back-btn:hover,
.canvas-back-btn:focus,
.canvas-back-btn:focus-visible {
  color: #e2e8f0 !important;
  background: rgba(148, 163, 184, .12) !important;
}
.canvas-back-btn:active,
.canvas-back-btn.is-active {
  color: #c7d2fe !important;
  background: rgba(129, 140, 248, .16) !important;
}
.canvas-toolbar { display:flex; align-items:center; justify-content:space-between; padding:8px 16px;
  background:#1a1a2e; border-bottom:1px solid #2a2a3e; flex-shrink:0; color:#e0e0e0; position:relative; z-index:40; }
.toolbar-actions { display:flex; align-items:center; gap:10px; }
.zoom-label {
  min-width: 46px;
  text-align: center;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  font-weight: 650;
  color: #c7d2fe;
  padding: 6px 10px;
  border-radius: 10px;
  border: 1px solid #2a3348;
  background: #121826;
}
.toolbar-action-btn {
  appearance: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 12px;
  border-radius: 10px;
  border: 1px solid #2a3348;
  background: #121826;
  color: #dbe4f5;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: border-color .15s, background .15s, color .15s;
}
.toolbar-action-btn:hover {
  border-color: rgba(129,140,248,.5);
  background: rgba(129,140,248,.12);
  color: #eef2ff;
}
.project-menu-btn { border:1px solid #2a2a3e; background:#111827; color:#e0e7ff; border-radius:6px; padding:5px 9px; font-size:13px; font-weight:800; cursor:pointer; }
.project-menu-btn:hover { border-color:#818cf8; }
.canvas-body { display:flex; flex:1; min-height:0; overflow:hidden; }
.canvas-left-panel {
  --panel-bg: #121826;
  --panel-surface: #1a2234;
  --panel-surface-hover: #222c42;
  --panel-border: #2a3348;
  --panel-text: #e8edf7;
  --panel-muted: #8b95a8;
  --panel-accent: #818cf8;
  --panel-accent-soft: rgba(129, 140, 248, .14);
  width: 268px;
  background: var(--panel-bg);
  border-right: 1px solid var(--panel-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  min-height: 0;
  overflow: hidden;
  color: var(--panel-text);
  position: relative;
  z-index: 30;
}
.canvas-tabs {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 2px;
  padding: 10px 8px 8px;
  border-bottom: 1px solid var(--panel-border);
  background: rgba(0, 0, 0, .18);
  flex-shrink: 0;
}
.canvas-tab {
  appearance: none;
  border: 0;
  background: transparent;
  color: var(--panel-muted);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 52px;
  padding: 6px 2px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.1;
  transition: background .15s, color .15s;
}
.canvas-tab:hover { color: var(--panel-text); background: rgba(255,255,255,.04); }
.canvas-tab.active {
  color: #c7d2fe;
  background: var(--panel-accent-soft);
  box-shadow: inset 0 0 0 1px rgba(129, 140, 248, .28);
}
.tab-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 12px 20px;
  font-size: 12px;
  scrollbar-width: thin;
  scrollbar-color: #334155 transparent;
}
.panel-section + .panel-section { margin-top: 18px; padding-top: 16px; border-top: 1px solid rgba(42, 51, 72, .9); }
.panel-section-head { margin-bottom: 10px; }
.panel-section-head h3 {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: .04em;
  color: #dbe4f5;
}
.panel-section-head p {
  margin: 4px 0 0;
  font-size: 11px;
  line-height: 1.45;
  color: var(--panel-muted);
}
.node-group { margin-bottom: 14px; }
.node-group-title {
  color: #6f7b91;
  font-size: 10px;
  font-weight: 700;
  margin: 0 0 8px;
  letter-spacing: .08em;
  text-transform: uppercase;
}
.node-add-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.node-add-card {
  width: 100%;
  appearance: none;
  text-align: left;
  padding: 9px 10px;
  display: flex;
  gap: 8px;
  align-items: flex-start;
  background: var(--panel-surface);
  border: 1px solid var(--panel-border);
  border-radius: 12px;
  cursor: pointer;
  transition: border-color .15s, background .15s, transform .12s, box-shadow .15s;
  color: var(--panel-text);
}
.node-add-card:hover {
  border-color: color-mix(in srgb, var(--node-accent) 55%, transparent);
  background: color-mix(in srgb, var(--node-accent) 10%, var(--panel-surface));
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--node-accent) 18%, transparent);
}
.node-add-card:active { transform: translateY(1px); }
.node-add-card .node-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  background: color-mix(in srgb, var(--node-accent) 16%, transparent);
  color: var(--node-accent);
}
.node-add-copy { display: grid; gap: 2px; min-width: 0; }
.node-add-copy strong {
  font-size: 12px;
  font-weight: 650;
  color: #f1f5f9;
  line-height: 1.25;
}
.node-add-copy small {
  font-size: 10px;
  line-height: 1.35;
  color: var(--panel-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.upload-drop {
  width: 100%;
  appearance: none;
  border: 1px dashed #3d4a63;
  color: var(--panel-muted);
  border-radius: 12px;
  padding: 16px 12px;
  font-size: 12px;
  text-align: center;
  cursor: pointer;
  background: rgba(15, 23, 42, .55);
  display: grid;
  gap: 4px;
  transition: border-color .15s, color .15s, background .15s;
}
.upload-drop:hover {
  border-color: rgba(129, 140, 248, .65);
  background: var(--panel-accent-soft);
  color: #e0e7ff;
}
.upload-drop-title { font-size: 12px; font-weight: 650; color: #cbd5e1; }
.upload-drop-hint { font-size: 11px; color: inherit; }
.slash-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.slash-chip {
  appearance: none;
  border: 1px solid var(--panel-border);
  background: var(--panel-surface);
  color: #c6d0e3;
  border-radius: 999px;
  padding: 5px 10px;
  font-size: 11px;
  font-weight: 550;
  line-height: 1.2;
  cursor: pointer;
  transition: border-color .15s, background .15s, color .15s;
}
.slash-chip:hover {
  border-color: rgba(129, 140, 248, .5);
  background: var(--panel-accent-soft);
  color: #e0e7ff;
}
.reference-actions { display: grid; gap: 6px; margin-bottom: 12px; }
.reference-actions .el-button { margin-left: 0; width: 100%; }
.asset-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  margin-bottom: 6px;
  border-radius: 10px;
  border: 1px solid var(--panel-border);
  background: var(--panel-surface);
}
.asset-row-name { flex: 1; min-width: 0; font-size: 12px; color: #e2e8f0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.panel-empty {
  padding: 18px 10px;
  text-align: center;
  color: var(--panel-muted);
  font-size: 12px;
  border: 1px dashed var(--panel-border);
  border-radius: 12px;
}
.tutorial-list { display: grid; gap: 8px; }
.tutorial-list button {
  text-align: left;
  border: 1px solid var(--panel-border);
  background: var(--panel-surface);
  color: #cbd5e1;
  border-radius: 10px;
  padding: 10px 12px;
  cursor: pointer;
  font-size: 12px;
}
.tutorial-list button:hover {
  border-color: rgba(129, 140, 248, .5);
  background: var(--panel-surface-hover);
}
.canvas-area { flex:1; min-width:0; min-height:0; background:#0f172a;
  background-image:radial-gradient(#1e293b 1px, transparent 1px); background-size:24px 24px;
  position:relative; overflow:hidden; cursor:grab; color:#e0e0e0; }
.canvas-area:active { cursor:grabbing; }
.left-panel-toggle {
  position:absolute; left:12px; top:12px; z-index:82;
  width:30px; height:30px; display:grid; place-items:center;
  border:1px solid #3b465c; border-radius:9px;
  color:#c7d2fe; background:rgba(18,24,38,.94);
  box-shadow:0 8px 22px rgba(0,0,0,.28); cursor:pointer;
}
.left-panel-toggle:hover { border-color:#818cf8; background:#252d48; }
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
.text-content-preview { min-height:210px; margin:10px; padding:18px 22px; border-radius:10px; background:#111827; color:#e5e7eb; font-size:16px; line-height:1.75; white-space:pre-wrap; overflow:auto; }
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
.floating-add { position:absolute; bottom:16px; left:50%; transform:translateX(-50%); z-index:80; }
.connection-delete-btn {
  position:absolute; top:16px; right:16px; z-index:80;
  border:1px solid #f59e0b; background:#1f1606; color:#fbbf24;
  border-radius:6px; padding:6px 10px; font-size:12px; font-weight:700; cursor:pointer;
}
.connection-delete-btn:hover { background:#2a1d08; }
.node-context-menu { position:fixed; z-index:1000; display:grid; min-width:160px; background:#111827; border:1px solid #374151; border-radius:8px; overflow:hidden; box-shadow:0 16px 40px rgba(0,0,0,.45); }
.node-context-menu button { border:0; background:transparent; color:#e5e7eb; text-align:left; padding:9px 12px; cursor:pointer; font-size:12px; }
.node-context-menu button:hover { background:#1f2937; }
.node-context-menu button.danger { color:#fca5a5; }
.director-overlay { position:fixed; inset:0; z-index:1200; background:#05070b; display:flex; align-items:center; justify-content:center; padding:0; }
.director-shell { width:100vw; height:100vh; background:#101010; border:1px solid #2c2c2c; color:#e5e5e5; display:flex; flex-direction:column; overflow:hidden; }
.director-shell.fullscreen { border:0; }
.director-topbar { height:46px; flex-shrink:0; display:grid; grid-template-columns:1fr auto 1fr; align-items:center; gap:16px;
  padding:0 14px; border-bottom:1px solid #2b2b2b; background:#1d1d1d; }
.director-topbar strong { display:block; font-size:14px; line-height:1.2; color:#d9d9d9; }
.director-view-switch { display:flex; padding:3px; background:#0f0f0f; border:1px solid #383838; border-radius:18px; }
.director-view-switch button { border:0; background:transparent; color:#9d9d9d; border-radius:14px; padding:4px 12px; cursor:pointer; font-size:12px; }
.director-view-switch button.active { background:#333; color:#fff; }
.director-window-actions { justify-self:end; display:flex; gap:10px; }
.director-window-actions button { width:22px; height:22px; border:0; background:transparent; color:#bfbfbf; font-size:18px; cursor:pointer; border-radius:50%; }
.director-window-actions button:hover { background:#333; color:#fff; }
.director-workspace { flex:1; min-height:0; display:grid; grid-template-columns:178px minmax(420px, 1fr) 220px; }
.director-panel { min-height:0; overflow:auto; background:#1f1f1f; padding:10px; border-color:#2d2d2d; }
.director-left { border-right:1px solid #263244; }
.director-right { border-left:1px solid #263244; }
.director-panel-title { margin:8px 0 12px; color:#d4d4d4; font-size:12px; font-weight:800; }
.director-search { height:28px; display:flex; align-items:center; gap:6px; background:#303030; border-radius:6px; padding:0 8px; margin-bottom:12px; }
.director-search input { flex:1; min-width:0; border:0; outline:0; background:transparent; color:#ddd; font-size:12px; }
.director-search span { color:#aaa; }
.director-element-list { display:grid; gap:6px; margin-bottom:10px; }
.director-element-list button { display:grid; grid-template-columns:26px 1fr; grid-template-rows:auto auto; column-gap:7px;
  align-items:center; text-align:left; border:1px solid transparent; background:transparent; color:#d7d7d7; border-radius:5px; padding:7px 8px; cursor:pointer; }
.director-element-list button span { grid-row:1 / span 2; display:flex; align-items:center; justify-content:center; height:28px;
  border-radius:6px; color:#bfbfbf; font-size:14px; font-weight:800; }
.director-element-list button strong { font-size:12px; font-weight:600; }
.director-element-list button small { color:#8a8a8a; font-size:10px; }
.director-element-list button.active { border-color:#3d3d3d; background:#333; }
.director-element-list button.hidden { opacity:.45; }
.director-list-actions { display:grid; grid-template-columns:1fr 1fr; gap:6px; }
.director-list-actions .el-button { margin-left:0; width:100%; }
.director-stage-wrap { position:relative; min-width:0; min-height:0; background:#07090f; display:flex; flex-direction:column; overflow:hidden; }
.director-stage { flex:1; position:relative; overflow:hidden; background:radial-gradient(circle at 50% 32%, rgba(13,55,84,.22), transparent 38%), #080b12; }
.director-stage::before { content:''; position:absolute; left:-12%; right:-12%; bottom:-8%; height:66%; opacity:.72;
  background-image:linear-gradient(#0e324b 1px,transparent 1px),linear-gradient(90deg,#0e324b 1px,transparent 1px);
  background-size:42px 42px; transform:perspective(520px) rotateX(62deg) scale(var(--director-scene-zoom, 1)); transform-origin:bottom; }
.director-stage.view-top::before { transform:none; inset:50px 8% 56px; height:auto; }
.director-stage.view-front::before { transform:none; left:0; right:0; bottom:12%; height:1px; background:#334155; }
.director-ground { position:absolute; left:0; right:0; bottom:var(--director-ground-offset, 0); height:22%; background:linear-gradient(180deg, transparent, rgba(21,27,35,.9)); pointer-events:none; }
.director-orientation { position:absolute; right:20px; top:16px; z-index:7; display:grid; justify-items:center; gap:6px; }
.director-orientation span { display:block; width:58px; height:58px; border-radius:50%; background:radial-gradient(circle,#6b7280 0 5px,transparent 6px), conic-gradient(from 0deg,#4b5563,#111827,#4b5563); opacity:.85; }
.director-orientation button { border:0; border-radius:5px; background:#333; color:#bfbfbf; padding:4px 10px; cursor:pointer; font-size:11px; }
.director-camera-frame { position:absolute; left:50%; top:50%; transform:translate(-50%,-50%); border:1px solid rgba(59,130,246,.48);
  box-shadow:0 0 0 999px rgba(2,6,23,.13); z-index:2; pointer-events:none; }
.director-camera-frame span { position:absolute; left:8px; top:8px; color:#e5e7eb; font-size:11px; background:rgba(20,20,20,.85); padding:3px 6px; border-radius:4px; }
.director-camera-frame.aspect-21-9 { width:72%; aspect-ratio:21 / 9; }
.director-camera-frame.aspect-16-9 { width:62%; aspect-ratio:16 / 9; }
.director-camera-frame.aspect-9-16 { height:72%; aspect-ratio:9 / 16; }
.director-camera-frame.aspect-1-1 { width:46%; aspect-ratio:1 / 1; }
.director-camera-frame.aspect-4-3 { width:54%; aspect-ratio:4 / 3; }
.director-camera-frame.aspect-3-4 { height:68%; aspect-ratio:3 / 4; }
.director-object { position:absolute; z-index:4; min-width:68px; height:54px; border:1px solid #475569; border-radius:8px;
  color:#e5e7eb; background:#1f2937; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:3px;
  cursor:pointer; box-shadow:0 12px 24px rgba(0,0,0,.35); transform-origin:center; touch-action:none; user-select:none; }
.director-object span { font-size:11px; color:#0f172a; background:#c7d2fe; border-radius:999px; padding:2px 6px; font-weight:800; }
.director-object strong { font-size:11px; max-width:86px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.director-object.selected { border-color:#60a5fa; box-shadow:0 0 0 3px rgba(96,165,250,.24), 0 16px 30px rgba(0,0,0,.42); }
.director-object-human { width:48px; min-width:48px; height:86px; border-radius:24px 24px 10px 10px; background:#8fb4cc; }
.director-object-geometry { background:#6b7b75; }
.director-object-crowd { min-width:98px; background:repeating-linear-gradient(90deg,#1f2937 0 16px,#334155 16px 28px); }
.director-object-upload { background:#7c2d12; }
.director-bottom-toolbar { position:absolute; left:50%; bottom:12px; transform:translateX(-50%); z-index:20; display:flex; align-items:center; gap:18px; height:44px; padding:0 18px; background:#1d1d1d; border:1px solid #3d3d3d; border-radius:15px; box-shadow:0 18px 48px rgba(0,0,0,.45); }
.director-bottom-toolbar > button { width:34px; height:34px; border:0; background:transparent; color:#f3f3f3; font-size:18px; border-radius:12px; cursor:pointer; display:flex; align-items:center; justify-content:center; }
.director-bottom-toolbar > button:hover, .director-bottom-toolbar > button.active { background:#343434; }
.director-bottom-toolbar > button:nth-child(3) { font-weight:800; font-size:16px; }
.director-popover { position:absolute; bottom:54px; min-width:210px; background:#1d1d1d; border:1px solid #454545; border-radius:14px; padding:10px; box-shadow:0 20px 60px rgba(0,0,0,.52); display:grid; gap:6px; color:#ddd; }
.director-popover button { border:0; background:transparent; color:#d9d9d9; border-radius:10px; padding:10px 12px; text-align:left; cursor:pointer; font-size:15px; display:flex; align-items:center; gap:14px; }
.director-popover button:hover, .director-popover button.active { background:#333; color:#fff; }
.director-popover button span { min-width:28px; color:#f1f1f1; font-weight:800; text-align:center; }
.director-popover button kbd { margin-left:auto; color:#8f8f8f; font-family:inherit; }
.director-popover .has-sub b { margin-left:auto; color:#aaa; }
.mode-popover { left:0; min-width:176px; }
.model-popover { left:46px; min-width:250px; }
.geometry-popover, .crowd-popover { left:280px; min-width:220px; }
.panorama-popover { left:118px; min-width:236px; }
.camera-popover { left:170px; min-width:420px; grid-template-columns:1fr 1fr; }
.aspect-popover { left:300px; min-width:420px; grid-template-columns:repeat(4, 1fr); }
.popover-title { grid-column:1 / -1; color:#9d9d9d; font-size:14px; padding:4px 8px 8px; display:flex; justify-content:space-between; }
.crowd-popover label { display:grid; grid-template-columns:52px 1fr; align-items:center; gap:10px; color:#a9a9a9; font-size:15px; padding:4px 12px; }
.crowd-popover input { width:78px; border:0; border-radius:10px; background:#343434; color:#fff; padding:10px; font-size:16px; }
.popover-actions { display:grid; grid-template-columns:1fr 1fr; gap:12px; padding:8px 12px 4px; }
.popover-actions button { justify-content:center; background:#333; }
.popover-actions button.primary { background:#fff; color:#111; }
.director-panel-tabs { display:flex; gap:8px; margin-bottom:12px; }
.director-panel-tabs button { border:0; background:#333; color:#aaa; border-radius:8px; padding:7px 10px; cursor:pointer; }
.director-panel-tabs button.active { color:#fff; background:#3d3d3d; }
.camera-preview { height:106px; border-radius:8px; background:linear-gradient(180deg,#030303 0 48%,#10151c 48%); border:1px solid #262626; padding:10px; display:grid; position:relative; margin-bottom:16px; overflow:hidden; }
.camera-preview strong { color:#aaa; font-size:12px; }
.camera-preview span { align-self:center; justify-self:center; color:#aab6c8; font-size:26px; }
.camera-preview button { position:absolute; right:8px; bottom:8px; border:0; border-radius:8px; background:#2f3440; color:#eee; width:24px; height:24px; cursor:pointer; }
.director-form { display:grid; gap:8px; margin-bottom:16px; }
.director-form label { color:#8e8e8e; font-size:11px; font-weight:700; }
.director-form input[type="checkbox"] { vertical-align:-2px; }
.director-number-row { display:grid; grid-template-columns:1fr 1fr 1fr; gap:6px; }
.director-panorama-actions { display:grid; grid-template-columns:1fr; gap:6px; }
.director-shot-list { display:grid; gap:8px; }
.director-shot-item { display:grid; grid-template-columns:1fr auto auto; gap:6px; align-items:center; border:1px solid #333; background:#161616; border-radius:8px; padding:6px; }
.director-shot-item > button:first-child { border:0; background:transparent; color:#e5e7eb; text-align:left; cursor:pointer; padding:0; }
.director-shot-item img { display:block; width:100%; height:54px; object-fit:cover; border-radius:6px; margin-bottom:6px; border:1px solid #333; }
.director-shot-item strong { display:block; font-size:12px; }
.director-shot-item span { color:#8f8f8f; font-size:10px; }
.director-empty { color:#8f8f8f; font-size:12px; padding:10px; border:1px dashed #3f3f3f; border-radius:8px; text-align:center; }
.director-empty.compact { text-align:left; }
.director-ai-dialog { position:fixed; inset:0; z-index:1300; background:rgba(0,0,0,.62); display:flex; align-items:center; justify-content:center; }
.director-ai-card { width:min(1120px, 78vw); background:#1f1f1f; border:1px solid #3b3b3b; border-radius:16px; box-shadow:0 24px 80px rgba(0,0,0,.62); color:#e5e5e5; overflow:hidden; }
.director-ai-card header { height:72px; display:flex; align-items:center; justify-content:space-between; padding:0 28px; border-bottom:1px solid #343434; }
.director-ai-card header strong { font-size:20px; }
.director-ai-card header button { border:0; background:transparent; color:#cfcfcf; font-size:34px; cursor:pointer; }
.director-ai-tabs { display:flex; gap:34px; padding:22px 28px 10px; }
.director-ai-tabs button { border:0; background:transparent; color:#8e8e8e; font-size:18px; font-weight:800; cursor:pointer; }
.director-ai-tabs button.active { color:#fff; }
.ai-upload-box { display:flex; flex-direction:column; justify-content:center; align-items:center; height:410px; margin:0 28px 22px; border:1px dashed #555; border-radius:14px; cursor:pointer; gap:14px; color:#8f8f8f; }
.ai-upload-box input { display:none; }
.ai-upload-box strong { color:#f5f5f5; font-size:20px; }
.ai-upload-box span { font-size:15px; }
.director-ai-mode { display:grid; grid-template-columns:1fr 1fr; gap:14px; padding:0 28px 24px; }
.director-ai-mode label { border:1px solid #444; border-radius:10px; padding:14px 18px; display:grid; grid-template-columns:26px 1fr; column-gap:8px; cursor:pointer; color:#8e8e8e; }
.director-ai-mode label.active { border-color:#8f8f8f; color:#fff; }
.director-ai-mode input { grid-row:1 / span 2; }
.director-ai-mode strong { font-size:18px; }
.director-ai-mode span { grid-column:2; font-size:14px; margin-top:6px; }
.director-ai-card footer { min-height:84px; border-top:1px solid #343434; display:flex; align-items:center; justify-content:space-between; gap:16px; padding:18px 28px; color:#aaa; font-size:15px; }
.director-ai-card footer button { border:0; border-radius:12px; background:#f5f5f5; color:#111; padding:14px 28px; font-size:18px; cursor:pointer; }
.director-ai-card footer button:disabled { opacity:.55; cursor:not-allowed; }
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
